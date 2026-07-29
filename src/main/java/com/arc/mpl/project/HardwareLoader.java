package com.arc.mpl.project;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Parses the restricted project hardware language into a deployment contract. */
public final class HardwareLoader {
    public HardwareContract load(Path projectDirectory) throws IOException {
        return load(projectDirectory, new ProjectManifestLoader().load(projectDirectory));
    }

    public HardwareContract load(Path projectDirectory, ProjectManifest manifest) throws IOException {
        Path project = projectDirectory.toAbsolutePath().normalize();
        Path file = WorkspacePackageInstaller.resolveInside(project, manifest.hardware(), "hardware");
        if (!Files.isRegularFile(file)) return emptyContract();

        MplhParser.HardwareFileContext hardwareFile = parse(file);

        List<HardwareContract.LinkDeclaration> links = new ArrayList<>();
        Map<String, String> messages = new HashMap<>();
        for (MplhParser.DeclarationContext declaration : hardwareFile.declaration()) {
            if (declaration.hardwareRequirement() != null) {
                throw new IOException("根项目 .mplh 不能声明 require：" + declaration.hardwareRequirement().name.getText());
            }
            if (declaration.hardwareConstant() != null) {
                MplhParser.HardwareConstantContext constant = declaration.hardwareConstant();
                String name = constant.name.getText();
                String type = constant.type.getText();
                if (constant.alias != null) {
                    String alias = unescape(constant.alias.getText());
                    if (!alias.matches("[_A-Za-z][_A-Za-z0-9]*")) {
                        throw new IOException("硬件链接名不是有效的游戏链接变量：" + alias);
                    }
                    if (links.stream().anyMatch(link -> link.mplName().equals(name))) {
                        throw new IOException("重复的硬件常量：" + name);
                    }
                    links.add(new HardwareContract.LinkDeclaration(name, type, alias));
                    if ("Message".equals(type)) messages.put(name, alias);
                }
                continue;
            }
        }
        return new HardwareContract(links, messages);
    }

    public PackageHardwareInterface loadPackageInterface(Path packageDirectory, ProjectManifest manifest) throws IOException {
        Path root = packageDirectory.toAbsolutePath().normalize();
        Path file = WorkspacePackageInstaller.resolveInside(root, manifest.hardware(), "hardware");
        if (!Files.isRegularFile(file)) throw new IOException("外部包缺少 .mplh 硬件接口：" + manifest.name());
        MplhParser.HardwareFileContext hardwareFile = parse(file);
        Map<String, MutableRequirement> merged = new LinkedHashMap<>();
        for (MplhParser.DeclarationContext declaration : hardwareFile.declaration()) {
            if (declaration.hardwareConstant() != null) {
                throw new IOException("包 .mplh 只能声明 require，不能绑定 const/link：" + manifest.name());
            }
            MplhParser.HardwareRequirementContext source = declaration.hardwareRequirement();
            String name = source.name.getText();
            String type = source.type.getText();
            if (type.equals("Memory") || type.equals("MemoryCell") || type.equals("MemoryBank")) {
                throw new IOException("包不能 require Memory：" + name);
            }
            MutableRequirement target = merged.computeIfAbsent(name, ignored -> new MutableRequirement(name, type));
            if (!target.type.equals(type)) throw new IOException("同名 require 的类型不一致：" + name);
            Map<String, String> arguments = new LinkedHashMap<>();
            for (MplhParser.RequirementArgumentContext argument : source.requirementArgument()) {
                String key = argument.name.getText();
                if (arguments.putIfAbsent(key, argument.value.getText()) != null) {
                    throw new IOException("同一 require 重复参数：" + name + "." + key);
                }
            }
            merge(target, arguments);
        }
        Map<String, PackageHardwareInterface.Requirement> requirements = new LinkedHashMap<>();
        for (MutableRequirement value : merged.values()) requirements.put(value.name, value.freeze());
        return new PackageHardwareInterface(requirements);
    }

    private MplhParser.HardwareFileContext parse(Path file) throws IOException {
        List<String> errors = new ArrayList<>();
        MplhLexer lexer = new MplhLexer(CharStreams.fromString(Files.readString(file)));
        MplhParser parser = new MplhParser(new CommonTokenStream(lexer));
        SyntaxErrors listener = new SyntaxErrors(errors);
        lexer.removeErrorListeners();
        lexer.addErrorListener(listener);
        parser.removeErrorListeners();
        parser.addErrorListener(listener);
        MplhParser.HardwareFileContext hardwareFile = parser.hardwareFile();
        if (!errors.isEmpty()) throw new IOException("硬件声明语法错误：" + errors.get(0));
        return hardwareFile;
    }

    private void merge(MutableRequirement target, Map<String, String> arguments) throws IOException {
        for (Map.Entry<String, String> argument : arguments.entrySet()) {
            switch (argument.getKey()) {
                case "access" -> {
                    PackageHardwareInterface.Access access = switch (argument.getValue()) {
                        case "read" -> PackageHardwareInterface.Access.READ;
                        case "write" -> PackageHardwareInterface.Access.WRITE;
                        case "readWrite" -> PackageHardwareInterface.Access.READ_WRITE;
                        default -> throw new IOException("require access 必须是 read、write 或 readWrite：" + target.name);
                    };
                    if (target.access != null && target.access != access) {
                        throw new IOException("同名 require 的 access 冲突：" + target.name);
                    }
                    target.access = access;
                }
                case "minWidth" -> target.minimumWidth = Math.max(target.minimumWidth,
                    positiveOrZero(argument.getValue(), target.name + ".minWidth"));
                case "minHeight" -> target.minimumHeight = Math.max(target.minimumHeight,
                    positiveOrZero(argument.getValue(), target.name + ".minHeight"));
                case "count" -> target.count = Math.max(target.count,
                    positive(argument.getValue(), target.name + ".count"));
                default -> throw new IOException("未知 require 参数：" + target.name + "." + argument.getKey());
            }
        }
    }

    private int positiveOrZero(String value, String field) throws IOException {
        int parsed = integer(value, field);
        if (parsed < 0) throw new IOException("require 参数不得为负数：" + field);
        return parsed;
    }

    private int positive(String value, String field) throws IOException {
        int parsed = integer(value, field);
        if (parsed < 1) throw new IOException("require 参数必须大于 0：" + field);
        return parsed;
    }

    private int integer(String value, String field) throws IOException {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IOException("require 参数必须是整数：" + field, exception);
        }
    }

    private HardwareContract emptyContract() {
        return new HardwareContract(List.of(), Map.of());
    }

    private String unescape(String token) {
        String text = token.substring(1, token.length() - 1);
        return text.replace("\\n", "\n").replace("\\r", "\r").replace("\\t", "\t")
            .replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private static final class SyntaxErrors extends BaseErrorListener {
        private final List<String> errors;

        private SyntaxErrors(List<String> errors) {
            this.errors = errors;
        }

        @Override
        public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int column,
                                String message, RecognitionException exception) {
            errors.add(line + ":" + (column + 1) + " " + message);
        }
    }

    private static final class MutableRequirement {
        private final String name;
        private final String type;
        private PackageHardwareInterface.Access access;
        private int minimumWidth;
        private int minimumHeight;
        private int count = 1;

        private MutableRequirement(String name, String type) {
            this.name = name;
            this.type = type;
        }

        private PackageHardwareInterface.Requirement freeze() throws IOException {
            if (access == null) throw new IOException("require 必须声明 access：" + name);
            try {
                return new PackageHardwareInterface.Requirement(name, type, access, minimumWidth, minimumHeight, count);
            } catch (IllegalArgumentException exception) {
                throw new IOException(exception.getMessage(), exception);
            }
        }
    }
}
