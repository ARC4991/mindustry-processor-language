package com.arc.mpl.cli;

import com.arc.mpl.compiler.CompilationRequest;
import com.arc.mpl.compiler.CompilationResult;
import com.arc.mpl.compiler.MplCompiler;
import com.arc.mpl.compiler.MultiShardCompilation;
import com.arc.mpl.diagnostic.Diagnostic;
import com.arc.mpl.diagnostic.DiagnosticLanguage;
import com.arc.mpl.diagnostic.DiagnosticMessages;
import com.arc.mpl.project.ProjectInitializer;
import com.arc.mpl.project.HardwareContract;
import com.arc.mpl.project.HardwareLoader;
import com.arc.mpl.project.MindustrySchematicWriter;
import com.arc.mpl.project.BuildArtifactWriter;
import com.arc.mpl.project.RuntimePlan;
import com.arc.mpl.project.RuntimePlanner;
import com.arc.mpl.project.RuntimePreferencesLoader;
import com.arc.mpl.project.ProjectMetadata;
import com.arc.mpl.project.WorkspacePackageInstaller;
import com.arc.mpl.project.PackageDependencyEditor;
import com.arc.mpl.project.PackageRegistryClient;

import java.nio.file.Path;
import java.nio.file.Files;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Minimal CLI shell; command parsing grows without leaking into compiler phases. */
public final class MplCli {
    private MplCli() {
    }

    public static void main(String[] args) {
        execute(args, Path.of(".").toAbsolutePath().normalize());
    }

    static void execute(String[] args, Path workingDirectory) {
        workingDirectory = workingDirectory.toAbsolutePath().normalize();
        CliInvocation invocation;
        try {
            invocation = extractLanguage(args);
        } catch (IllegalArgumentException exception) {
            System.err.println(DiagnosticMessages.formatChinese(
                "cli.language.unsupported", List.of(exception.getMessage()), "不支持的编译信息语言"));
            printUsage(DiagnosticLanguage.ZH_CN);
            System.exit(2);
            return;
        }
        args = invocation.arguments();
        DiagnosticLanguage language = invocation.language();

        if (args.length == 1 && "--version".equals(args[0])) {
            System.out.println("mpl 0.1.0-SNAPSHOT");
            return;
        }
        if ("init".equals(args[0]) && (args.length == 1 || args.length == 2)) {
            String target = "v146";
            if (args.length == 2) {
                if (!args[1].startsWith("--target=")) {
                    printUsage(language);
                    System.exit(2);
                    return;
                }
                target = args[1].substring("--target=".length());
            }
            try {
                new ProjectInitializer().initialize(workingDirectory, target);
                System.out.println(message(language, "cli.init.success", workingDirectory, target));
            } catch (IOException | IllegalArgumentException exception) {
                System.err.println(message(language, "cli.init.failed", exceptionMessage(exception)));
                System.exit(1);
            }
            return;
        }
        if ("search".equals(args[0]) && args.length <= 2) {
            try {
                String query = args.length == 2 ? args[1] : "";
                for (PackageRegistryClient.Entry entry : new PackageRegistryClient().search(query)) {
                    System.out.println(entry.name() + " " + entry.version()
                        + (entry.description().isBlank() ? "" : " - " + entry.description()));
                }
            } catch (IOException | IllegalArgumentException exception) {
                System.err.println("搜索包失败：" + exceptionMessage(exception));
                System.exit(1);
            }
            return;
        }
        if ("install".equals(args[0]) && (args.length == 1 || args.length == 2)) {
            try {
                String requested = null;
                if (args.length == 1) {
                } else if (args.length == 2) {
                    requested = args[1];
                }
                if (requested != null && Files.isDirectory(Path.of(requested))) {
                    throw new IllegalArgumentException("mpl install 不接受项目目录；请在目标项目目录内执行命令");
                }
                if (requested != null) addDependency(workingDirectory, requested);
                var lock = new WorkspacePackageInstaller().install(workingDirectory);
                System.out.println(message(language, "cli.install.success", lock.packages().size(),
                    workingDirectory.resolve("mpl.lock")));
            } catch (IOException | IllegalArgumentException exception) {
                System.err.println(message(language, "cli.install.failed", exceptionMessage(exception)));
                System.exit(1);
            }
            return;
        }
        if (args.length == 2 && "check".equals(args[0]) && args[1].startsWith("--target=")) {
            String target = args[1].substring("--target=".length());
            CompilationResult result = new MplCompiler().compile(new CompilationRequest(workingDirectory, target));
            result.diagnostics().forEach(diagnostic -> printDiagnostic(diagnostic, language));
            System.exit(result.succeeded() ? 0 : 1);
            return;
        }
        BuildArguments build = parseBuildArguments(args);
        if (build != null) {
            CompilationResult result = new MplCompiler().compile(
                new CompilationRequest(workingDirectory, build.target(), build.debug()));
            result.diagnostics().forEach(diagnostic -> printDiagnostic(diagnostic, language));
            if (!result.succeeded()) {
                System.exit(1);
                return;
            }
            try {
                Path outputDirectory = workingDirectory.resolve("build");
                HardwareContract hardware = new HardwareLoader().load(workingDirectory);
                BuildArtifactWriter writer = new BuildArtifactWriter();
                ProjectMetadata metadata = ProjectMetadata.load(workingDirectory);
                if (result.multiShard().isPresent()) {
                    MultiShardCompilation multi = result.multiShard().orElseThrow();
                    writer.write(outputDirectory, multi.shards().stream().map(shard ->
                        new BuildArtifactWriter.ShardArtifact(shard.id(), shard.mlog(), shard.mil())).toList(),
                        result.profile().orElseThrow(), hardware, multi.topology(), metadata,
                        result.optimizationReport());
                    for (MultiShardCompilation.Shard shard : multi.shards()) {
                        System.out.println(message(language, "cli.mil.written", outputDirectory.resolve(shard.id() + ".mil")));
                        System.out.println(message(language, "cli.mlog.written", outputDirectory.resolve(shard.id() + ".mlog")));
                    }
                } else {
                    RuntimePlan plan = new RuntimePlanner().plan(result.mlog().orElseThrow(), result.profile().orElseThrow(),
                        new RuntimePreferencesLoader().load(workingDirectory), result.physicalMemoryLayout());
                    writer.write(outputDirectory, result.mlog().orElseThrow(), result.mil().orElseThrow(),
                        result.profile().orElseThrow(), hardware, plan, metadata, result.optimizationReport());
                    System.out.println(message(language, "cli.mil.written", outputDirectory.resolve("Main.mil")));
                    System.out.println(message(language, "cli.mlog.written", outputDirectory.resolve("Main.mlog")));
                }
            } catch (IOException | IllegalArgumentException exception) {
                System.err.println(message(language, "cli.artifact.write.failed", exceptionMessage(exception)));
                System.exit(1);
                return;
            }
            return;
        }

        printUsage(language);
        System.exit(2);
    }

    private static void printUsage(DiagnosticLanguage language) {
        System.err.println(message(language, "cli.usage.init"));
        System.err.println(message(language, "cli.usage.install"));
        System.err.println(message(language, "cli.usage.search"));
        System.err.println(message(language, "cli.usage.check"));
        System.err.println(message(language, "cli.usage.build"));
    }

    private static void addDependency(Path project, String requested) throws IOException {
        String name;
        String specification;
        int separator = requested.indexOf('=');
        if (separator > 0) {
            name = requested.substring(0, separator);
            specification = normalizeSpecification(requested.substring(separator + 1));
        } else if (requested.startsWith("git:") || requested.startsWith("registry:")) {
            throw new IllegalArgumentException("直接来源必须使用 包名=来源 形式");
        } else if (requested.startsWith("https://") || requested.startsWith("http://")
            || requested.startsWith("git@")) {
            throw new IllegalArgumentException("直接来源必须使用 包名=来源 形式");
        } else if (requested.endsWith(".mplpkg") || requested.startsWith("file:")) {
            throw new IllegalArgumentException("mplpkg 来源必须使用 包名=文件路径 形式");
        } else {
            PackageRegistryClient.Entry entry = new PackageRegistryClient().require(requested);
            name = entry.name();
            specification = normalizeSpecification(entry.source());
        }
        if (name.isBlank()) throw new IllegalArgumentException("包名不能为空");
        new PackageDependencyEditor().add(project, name, specification);
    }

    private static String normalizeSpecification(String raw) {
        String specification = raw.trim();
        if (specification.startsWith("workspace:") || specification.startsWith("registry:")
            || specification.startsWith("git:")) return specification;
        if (specification.toLowerCase(java.util.Locale.ROOT).endsWith(".mplpkg")) {
            if (specification.startsWith("https://") || specification.startsWith("http://")
                || specification.startsWith("file:")) return "registry:" + specification;
            Path archive = Path.of(specification);
            if (Files.isRegularFile(archive)) {
                return "registry:" + archive.toAbsolutePath().normalize().toUri();
            }
            throw new IllegalArgumentException("找不到 mplpkg 文件：" + raw);
        }
        if (specification.startsWith("https://") || specification.startsWith("http://")
            || specification.startsWith("git@")) return "git:" + specification;
        if (specification.startsWith("file:")) return "registry:" + specification;
        throw new IllegalArgumentException("无法识别包来源：" + raw);
    }

    /** Parses the build-only flags without making their order part of the CLI contract. */
    private static BuildArguments parseBuildArguments(String[] args) {
        if (args.length < 1 || args.length > 3 || !"build".equals(args[0])) return null;

        String target = null;
        boolean debug = false;
        for (int index = 1; index < args.length; index++) {
            String argument = args[index];
            if (argument.startsWith("--target=")) {
                if (target != null) return null;
                target = argument.substring("--target=".length());
            } else if ("--debug".equals(argument)) {
                if (debug) return null;
                debug = true;
            } else if (argument.startsWith("--")) {
                return null;
            } else return null;
        }
        if (target == null) return null;
        return new BuildArguments(target, debug);
    }

    /** Places the inspectable intermediate artifact beside the final mlog. */
    private static Path milSiblingOf(Path mlogOutput) {
        Path filename = mlogOutput.getFileName();
        if (filename == null) {
            throw new IllegalArgumentException("mlog 输出路径必须包含文件名：" + mlogOutput);
        }
        String mlogName = filename.toString();
        String milName = mlogName.endsWith(".mlog")
            ? mlogName.substring(0, mlogName.length() - ".mlog".length()) + ".mil"
            : mlogName + ".mil";
        return mlogOutput.resolveSibling(milName);
    }

    private static void printDiagnostic(Diagnostic diagnostic, DiagnosticLanguage language) {
        System.err.printf("%s %s: %s%n", diagnostic.severity(), diagnostic.code(), diagnostic.render(language));
    }

    private record BuildArguments(String target, boolean debug) {
    }

    /** Removes the global language switch before command-specific parsing. */
    private static CliInvocation extractLanguage(String[] rawArguments) {
        DiagnosticLanguage language = DiagnosticLanguage.ZH_CN;
        boolean seenLanguage = false;
        List<String> remaining = new ArrayList<>();
        for (String argument : rawArguments) {
            if (!argument.startsWith("--lang=")) {
                remaining.add(argument);
                continue;
            }
            if (seenLanguage) throw new IllegalArgumentException(argument);
            String requested = argument.substring("--lang=".length());
            language = DiagnosticLanguage.parse(requested)
                .orElseThrow(() -> new IllegalArgumentException(requested));
            seenLanguage = true;
        }
        return new CliInvocation(language, remaining.toArray(String[]::new));
    }

    private static String message(DiagnosticLanguage language, String key, Object... arguments) {
        return DiagnosticMessages.format(language, key, List.of(arguments), "[" + key + "]");
    }

    private static String exceptionMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    private record CliInvocation(DiagnosticLanguage language, String[] arguments) {
    }
}
