package com.arc.mpl.project;

import com.arc.mpl.ast.ExportDeclaration;
import com.arc.mpl.ast.ClassDeclaration;
import com.arc.mpl.ast.FunctionDeclaration;
import com.arc.mpl.ast.ImportDeclaration;
import com.arc.mpl.ast.Program;
import com.arc.mpl.ast.Statement;
import com.arc.mpl.ast.VariableDeclaration;
import com.arc.mpl.diagnostic.Diagnostic;
import com.arc.mpl.diagnostic.Diagnostic.SourceSpan;
import com.arc.mpl.diagnostic.Severity;
import com.arc.mpl.mil.semantic.MilLowerer;
import com.arc.mpl.mil.semantic.MilLoweringResult;
import com.arc.mpl.mil.syntax.MilParseResult;
import com.arc.mpl.mil.syntax.MilSourceKind;
import com.arc.mpl.mil.syntax.MilSyntaxParser;
import com.arc.mpl.profile.TargetProfile;
import com.arc.mpl.syntax.MplSyntaxParser;
import com.arc.mpl.syntax.ParseResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Parses, validates, and links all project-local MPL/MIL modules reachable from the entry. */
public final class ProjectProgramLoader {
    private final List<Diagnostic> diagnostics = new ArrayList<>();
    private final Map<Path, SourceModule> modules = new LinkedHashMap<>();
    private final Map<Path, VisitState> states = new HashMap<>();
    private final Deque<Path> stack = new ArrayDeque<>();
    private final List<Path> order = new ArrayList<>();
    private final Map<Path, ModuleScope> scopesByFile = new HashMap<>();
    private final Map<String, ModuleScope> packageScopes = new LinkedHashMap<>();
    private ModuleScope rootScope;
    private Path entryFile;
    private TargetProfile profile;
    private HardwareContract hardware;

    public ProjectProgramResult load(ProjectSourceCatalog catalog, TargetProfile targetProfile,
                                     HardwareContract hardwareContract) {
        return load(catalog, targetProfile, hardwareContract, ResolvedPackageGraph.empty());
    }

    public ProjectProgramResult load(ProjectSourceCatalog catalog, TargetProfile targetProfile,
                                     HardwareContract hardwareContract, ResolvedPackageGraph packages) {
        diagnostics.clear();
        modules.clear();
        states.clear();
        stack.clear();
        order.clear();
        scopesByFile.clear();
        packageScopes.clear();
        entryFile = catalog.entryFile().toAbsolutePath().normalize();
        profile = targetProfile;
        hardware = hardwareContract;
        rootScope = new ModuleScope("$root", catalog, packages.rootDependencies(), true,
            PackageHardwareInterface.empty());
        register(rootScope);
        packages.packages().forEach((name, value) -> {
            ModuleScope scope = new ModuleScope(name, value.sources(), value.dependencies(), false,
                value.hardwareInterface());
            packageScopes.put(name, scope);
            register(scope);
        });

        visit(entryFile, null, null);
        if (!diagnostics.isEmpty()) return result(Optional.empty());
        Program linked = link();
        return result(diagnostics.isEmpty() ? Optional.of(linked) : Optional.empty());
    }

    private ProjectProgramResult result(Optional<Program> program) {
        return new ProjectProgramResult(program, diagnostics, order);
    }

    private void visit(Path file, Path importer, ImportDeclaration edge) {
        VisitState state = states.get(file);
        if (state == VisitState.COMPLETE) return;
        if (state == VisitState.VISITING) {
            List<String> cycle = new ArrayList<>();
            boolean include = false;
            for (Path current : stack) {
                if (current.equals(file)) include = true;
                if (include) cycle.add(moduleName(current));
            }
            cycle.add(moduleName(file));
            error("MPL1404", "模块依赖形成循环：" + String.join(" -> ", cycle),
                importer == null ? file : importer, edge == null ? null : edge.span());
            return;
        }

        Optional<Program> parsed = parse(file);
        if (parsed.isEmpty()) {
            states.put(file, VisitState.COMPLETE);
            return;
        }
        SourceModule module = new SourceModule(file, parsed.orElseThrow(), new LinkedHashMap<>());
        modules.put(file, module);
        states.put(file, VisitState.VISITING);
        stack.addLast(file);
        for (ImportDeclaration declaration : module.program().imports()) {
            Optional<Path> target = resolve(file, declaration);
            if (target.isEmpty()) continue;
            module.targets().put(declaration, target.orElseThrow());
            visit(target.orElseThrow(), file, declaration);
        }
        stack.removeLast();
        states.put(file, VisitState.COMPLETE);
        order.add(file);
    }

    private Optional<Program> parse(Path file) {
        String source;
        try {
            source = Files.readString(file);
        } catch (IOException exception) {
            error("MPL1402", "无法读取模块：" + exceptionMessage(exception), file, null);
            return Optional.empty();
        }
        if (file.getFileName().toString().endsWith(".mpl")) {
            ParseResult parsed = new MplSyntaxParser().parse(source, file);
            diagnostics.addAll(parsed.diagnostics());
            return parsed.program();
        }
        MilParseResult parsed = new MilSyntaxParser().parse(source, file, profile, MilSourceKind.USER);
        diagnostics.addAll(parsed.diagnostics());
        if (!parsed.succeeded()) return Optional.empty();
        HardwareContract visibleHardware = owner(file).visibleHardware(hardware);
        MilLoweringResult lowered = new MilLowerer().lower(parsed.document().orElseThrow(), file, profile, visibleHardware);
        diagnostics.addAll(lowered.diagnostics());
        return lowered.program();
    }

    private Optional<Path> resolve(Path importer, ImportDeclaration declaration) {
        String request = declaration.source();
        ModuleScope owner = owner(importer);
        if (!request.startsWith(".")) {
            if (!owner.dependencies().contains(request)) {
                error("MPL1401", "模块未声明外部包依赖：" + request, importer, declaration.span());
                return Optional.empty();
            }
            ModuleScope target = packageScopes.get(request);
            if (target == null) {
                error("MPL1401", "外部包尚未安装或锁定：" + request, importer, declaration.span());
                return Optional.empty();
            }
            bindPackageHardware(owner, target, declaration, importer);
            return Optional.of(target.catalog().entryFile().toAbsolutePath().normalize());
        }
        if (!declaration.hardwareArguments().isEmpty()) {
            error("MPL1410", "with 硬件注入只能用于外部包 import", importer, declaration.span());
        }
        Path requested = importer.getParent().resolve(request).normalize();
        Path sourceRoot = owner.catalog().sourceRoot().toAbsolutePath().normalize();
        if (!requested.startsWith(sourceRoot)) {
            error("MPL1403", "相对 import 不得越出所属包的 src：" + request, importer, declaration.span());
            return Optional.empty();
        }
        Set<Path> sourceFiles = Set.copyOf(owner.catalog().sourceFiles());
        List<Path> candidates = candidates(requested).stream().filter(sourceFiles::contains).toList();
        if (candidates.isEmpty()) {
            error("MPL1402", "找不到导入模块：" + request, importer, declaration.span());
            return Optional.empty();
        }
        if (candidates.size() > 1) {
            error("MPL1405", "导入路径存在歧义：" + request + " -> "
                + candidates.stream().map(this::moduleName).toList(), importer, declaration.span());
            return Optional.empty();
        }
        return Optional.of(candidates.get(0));
    }

    private void bindPackageHardware(ModuleScope importerScope, ModuleScope packageScope,
                                     ImportDeclaration declaration, Path importer) {
        Map<String, PackageHardwareInterface.Requirement> required = packageScope.hardwareInterface().requirements();
        Map<String, String> supplied = new LinkedHashMap<>();
        for (ImportDeclaration.HardwareArgument argument : declaration.hardwareArguments()) {
            if (supplied.putIfAbsent(argument.name(), argument.value()) != null) {
                error("MPL1413", "with 重复硬件参数：" + argument.name(), importer, argument.span());
            }
        }
        Set<String> missing = new LinkedHashSet<>(required.keySet());
        missing.removeAll(supplied.keySet());
        Set<String> extra = new LinkedHashSet<>(supplied.keySet());
        extra.removeAll(required.keySet());
        if (!missing.isEmpty()) error("MPL1413", "with 缺少包硬件参数：" + missing, importer, declaration.span());
        if (!extra.isEmpty()) error("MPL1413", "with 包含未声明的硬件参数：" + extra, importer, declaration.span());

        Map<String, String> resolved = new LinkedHashMap<>();
        for (Map.Entry<String, PackageHardwareInterface.Requirement> entry : required.entrySet()) {
            String suppliedName = supplied.get(entry.getKey());
            if (suppliedName == null) continue;
            String rootName = importerScope.root() ? suppliedName : importerScope.hardwareBindings().get(suppliedName);
            HardwareContract.Resource resource = rootName == null ? null : hardware.resource(rootName).orElse(null);
            if (resource == null) {
                error("MPL1415", "with 只能传入当前模块可见的硬件常量：" + suppliedName,
                    importer, declaration.span());
                continue;
            }
            PackageHardwareInterface.Requirement requirement = entry.getValue();
            if (!requirement.type().equals(resource.mplType())) {
                error("MPL1415", "with 硬件类型不匹配：" + entry.getKey() + " 要求 " + requirement.type()
                    + "，实际为 " + resource.mplType(), importer, declaration.span());
                continue;
            }
            if (!PackageHardwareValidator.supportsAccess(requirement, profile)) {
                error("MPL1415", "target 中的硬件类型不满足访问要求：" + requirement.name()
                    + "（" + requirement.access() + "）", importer, declaration.span());
                continue;
            }
            if (requirement.minimumWidth() > 0 || requirement.minimumHeight() > 0) {
                HardwareContract.DisplayLayout layout = resource.display().orElse(null);
                if (layout == null) {
                    error("MPL1416", "with Display 缺少编译期尺寸：" + requirement.name(),
                        importer, declaration.span());
                    continue;
                }
                if (layout.width() < requirement.minimumWidth() || layout.height() < requirement.minimumHeight()) {
                    error("MPL1416", "with Display 尺寸不足：" + requirement.name() + " 要求至少 "
                        + requirement.minimumWidth() + "x" + requirement.minimumHeight() + "，实际为 "
                        + layout.width() + "x" + layout.height(), importer, declaration.span());
                    continue;
                }
            }
            resolved.put(entry.getKey(), rootName);
        }
        if (resolved.size() == required.size() && !packageScope.bindHardware(resolved)) {
            error("MPL1417", "同一个包的多次 import 必须使用完全一致的 with 硬件绑定：" + packageScope.id(),
                importer, declaration.span());
        }
    }

    private List<Path> candidates(Path requested) {
        String name = requested.getFileName().toString();
        if (name.endsWith(".mpl") || name.endsWith(".mil")) return List.of(requested);
        return List.of(Path.of(requested + ".mpl"), Path.of(requested + ".mil"),
            requested.resolve("index.mpl"), requested.resolve("index.mil"));
    }

    private Program link() {
        Map<Path, Map<String, String>> declarations = allocateDeclarations();
        Map<Path, Map<String, String>> exports = validateExports(declarations);
        Map<Path, Map<String, String>> bindings = bindImports(declarations, exports);
        if (!diagnostics.isEmpty()) return new Program(List.of());

        List<ClassDeclaration> classes = new ArrayList<>();
        List<FunctionDeclaration> functions = new ArrayList<>();
        List<Statement> statements = new ArrayList<>();
        for (Path path : order) {
            SourceModule module = modules.get(path);
            Program rewritten = new ModuleSymbolRewriter(bindings.get(path), declarations.get(path))
                .rewrite(module.program());
            classes.addAll(rewritten.classes());
            functions.addAll(rewritten.functions());
            statements.addAll(rewritten.statements());
        }
        return new Program(List.of(), List.of(), classes, functions, statements);
    }

    private Map<Path, Map<String, String>> allocateDeclarations() {
        Map<Path, Map<String, String>> result = new LinkedHashMap<>();
        Set<String> used = new HashSet<>();
        allocateModuleDeclarations(entryFile, result, used);
        List<Path> paths = modules.keySet().stream().sorted(Comparator.comparing(Path::toString)).toList();
        for (Path path : paths) {
            if (!path.equals(entryFile)) allocateModuleDeclarations(path, result, used);
        }
        return result;
    }

    private void allocateModuleDeclarations(Path path, Map<Path, Map<String, String>> result, Set<String> used) {
        Map<String, String> names = new LinkedHashMap<>();
        SourceModule module = modules.get(path);
        for (ClassDeclaration declaration : module.program().classes()) {
            declare(path, names, used, declaration.name(), declaration.span());
        }
        for (FunctionDeclaration function : module.program().functions()) {
            declare(path, names, used, function.name(), function.span());
        }
        for (Statement statement : module.program().statements()) {
            if (statement instanceof VariableDeclaration variable) {
                declare(path, names, used, variable.name(), variable.span());
            }
        }
        result.put(path, Map.copyOf(names));
    }

    private void declare(Path path, Map<String, String> names, Set<String> used, String name, SourceSpan span) {
        if (names.containsKey(name)) {
            error("MPL1406", "模块顶层名称重复：" + name, path, span);
            return;
        }
        if (owner(path).root() && hardwareName(name)) {
            error("MPL1412", "模块顶层名称与全局硬件常量冲突：" + name, path, span);
        }
        if (!owner(path).root() && owner(path).hardwareInterface().requirements().containsKey(name)) {
            error("MPL1412", "包顶层名称与 require 硬件常量冲突：" + name, path, span);
        }
        String candidate = path.equals(entryFile) ? name : canonical(path, name);
        int suffix = 1;
        while (!used.add(candidate)) candidate = canonical(path, name) + "_" + suffix++;
        names.put(name, candidate);
    }

    private Map<Path, Map<String, String>> validateExports(Map<Path, Map<String, String>> declarations) {
        Map<Path, Map<String, String>> result = new LinkedHashMap<>();
        for (Path path : modules.keySet()) {
            Map<String, String> visible = new LinkedHashMap<>();
            for (ExportDeclaration declaration : modules.get(path).program().exports()) {
                String target = declarations.get(path).get(declaration.name());
                if (target == null) {
                    error("MPL1407", "export 指向未声明的顶层名称：" + declaration.name(), path, declaration.span());
                } else if (mutableTopLevel(path, declaration.name())) {
                    error("MPL1411", "第一版只能 export 函数或 val 常量：" + declaration.name(),
                        path, declaration.span());
                } else if (visible.putIfAbsent(declaration.name(), target) != null) {
                    error("MPL1407", "重复 export：" + declaration.name(), path, declaration.span());
                }
            }
            result.put(path, Map.copyOf(visible));
        }
        return result;
    }

    private boolean mutableTopLevel(Path path, String name) {
        return modules.get(path).program().statements().stream()
            .filter(VariableDeclaration.class::isInstance)
            .map(VariableDeclaration.class::cast)
            .anyMatch(variable -> variable.name().equals(name) && variable.mutable());
    }

    private Map<Path, Map<String, String>> bindImports(Map<Path, Map<String, String>> declarations,
                                                       Map<Path, Map<String, String>> exports) {
        Map<Path, Map<String, String>> result = new LinkedHashMap<>();
        for (Path path : modules.keySet()) {
            Map<String, String> names = new LinkedHashMap<>(declarations.get(path));
            SourceModule module = modules.get(path);
            for (ImportDeclaration declaration : module.program().imports()) {
                Path target = module.targets().get(declaration);
                if (target == null) continue;
                Set<String> seen = new LinkedHashSet<>();
                for (String name : declaration.names()) {
                    if (!seen.add(name)) {
                        error("MPL1408", "同一 import 重复名称：" + name, path, declaration.span());
                        continue;
                    }
                    if (owner(path).root() && hardwareName(name)) {
                        error("MPL1412", "import 名称与全局硬件常量冲突：" + name, path, declaration.span());
                        continue;
                    }
                    if (!owner(path).root() && owner(path).hardwareInterface().requirements().containsKey(name)) {
                        error("MPL1412", "import 名称与包 require 硬件常量冲突：" + name,
                            path, declaration.span());
                        continue;
                    }
                    String linked = exports.getOrDefault(target, Map.of()).get(name);
                    if (linked == null) {
                        error("MPL1409", "模块 " + moduleName(target) + " 未 export：" + name,
                            path, declaration.span());
                    } else if (names.putIfAbsent(name, linked) != null) {
                        error("MPL1408", "import 名称与当前模块绑定冲突：" + name, path, declaration.span());
                    }
                }
            }
            if (!owner(path).root()) {
                owner(path).hardwareBindings().forEach(names::putIfAbsent);
                hardware.names().forEach(name -> names.putIfAbsent(name,
                    "__package_hardware_unavailable_" + canonical(path, name)));
            }
            result.put(path, Map.copyOf(names));
        }
        return result;
    }

    private boolean hardwareName(String name) {
        return hardware.names().contains(name);
    }

    private String canonical(Path path, String symbol) {
        String module = moduleName(path).replaceAll("[^A-Za-z0-9]", "_");
        return "__module_" + module + "_" + symbol;
    }

    private String moduleName(Path path) {
        ModuleScope owner = owner(path);
        String relative = owner.catalog().sourceRoot().toAbsolutePath().normalize().relativize(path)
            .toString().replace('\\', '/');
        return owner.root() ? relative : owner.id() + "/" + relative;
    }

    private void register(ModuleScope scope) {
        for (Path source : scope.catalog().sourceFiles()) {
            Path normalized = source.toAbsolutePath().normalize();
            ModuleScope previous = scopesByFile.putIfAbsent(normalized, scope);
            if (previous != null && previous != scope) {
                throw new IllegalArgumentException("源码文件同时属于多个包：" + normalized);
            }
        }
    }

    private ModuleScope owner(Path file) {
        ModuleScope result = scopesByFile.get(file.toAbsolutePath().normalize());
        if (result == null) throw new IllegalStateException("模块不属于已验证的源码根：" + file);
        return result;
    }

    private void error(String code, String message, Path file, SourceSpan span) {
        diagnostics.add(new Diagnostic(Severity.ERROR, code, message, Optional.ofNullable(file),
            Optional.ofNullable(span)));
    }

    private String exceptionMessage(Exception exception) {
        return exception.getMessage() == null || exception.getMessage().isBlank()
            ? exception.getClass().getSimpleName() : exception.getMessage();
    }

    private record SourceModule(Path path, Program program, Map<ImportDeclaration, Path> targets) {
    }

    private static final class ModuleScope {
        private final String id;
        private final ProjectSourceCatalog catalog;
        private final Set<String> dependencies;
        private final boolean root;
        private final PackageHardwareInterface hardwareInterface;
        private Map<String, String> hardwareBindings = Map.of();
        private boolean hardwareBound;

        private ModuleScope(String id, ProjectSourceCatalog catalog, Set<String> dependencies, boolean root,
                            PackageHardwareInterface hardwareInterface) {
            this.id = id;
            this.catalog = catalog;
            this.dependencies = Set.copyOf(dependencies);
            this.root = root;
            this.hardwareInterface = hardwareInterface;
        }

        private String id() {
            return id;
        }

        private ProjectSourceCatalog catalog() {
            return catalog;
        }

        private Set<String> dependencies() {
            return dependencies;
        }

        private boolean root() {
            return root;
        }

        private PackageHardwareInterface hardwareInterface() {
            return hardwareInterface;
        }

        private Map<String, String> hardwareBindings() {
            return hardwareBindings;
        }

        private boolean bindHardware(Map<String, String> bindings) {
            Map<String, String> normalized = Map.copyOf(bindings);
            if (!hardwareBound) {
                hardwareBindings = normalized;
                hardwareBound = true;
                return true;
            }
            return hardwareBindings.equals(normalized);
        }

        private HardwareContract visibleHardware(HardwareContract rootHardware) {
            if (root) return rootHardware;
            List<HardwareContract.LinkDeclaration> links = new ArrayList<>();
            Map<String, String> messages = new LinkedHashMap<>();
            Map<String, HardwareContract.Resource> resources = new LinkedHashMap<>();
            for (Map.Entry<String, String> binding : hardwareBindings.entrySet()) {
                HardwareContract.Resource rootResource = rootHardware.resource(binding.getValue()).orElseThrow();
                links.addAll(rootResource.physicalLinks());
                resources.put(binding.getKey(), new HardwareContract.Resource(binding.getKey(), rootResource.mplType(),
                    rootResource.physicalLinks(), rootResource.display()));
                if (rootResource.mplType().equals("Message")) {
                    messages.put(binding.getKey(), rootResource.physicalLinks().get(0).gameAlias());
                }
            }
            return new HardwareContract(links.stream().distinct().toList(), messages, resources);
        }
    }

    private enum VisitState {
        VISITING,
        COMPLETE
    }
}
