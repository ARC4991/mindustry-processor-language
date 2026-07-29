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

        List<HardwareContract.LinkDeclaration> links = new ArrayList<>();
        Map<String, String> messages = new HashMap<>();
        for (MplhParser.DeclarationContext declaration : hardwareFile.declaration()) {
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
}
