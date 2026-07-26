package com.arc.mpl.cli;

import com.arc.mpl.compiler.CompilationRequest;
import com.arc.mpl.compiler.CompilationResult;
import com.arc.mpl.compiler.MplCompiler;
import com.arc.mpl.diagnostic.Diagnostic;
import com.arc.mpl.project.ProjectInitializer;

import java.nio.file.Path;
import java.nio.file.Files;
import java.io.IOException;

/** Minimal CLI shell; command parsing grows without leaking into compiler phases. */
public final class MplCli {
    private MplCli() {
    }

    public static void main(String[] args) {
        if (args.length == 1 && "--version".equals(args[0])) {
            System.out.println("mpl 0.1.0-SNAPSHOT");
            return;
        }
        if ((args.length == 2 || args.length == 3) && "init".equals(args[0])) {
            String target = "v146";
            String directory;
            if (args.length == 3) {
                if (!args[1].startsWith("--target=")) {
                    printUsage();
                    return;
                }
                target = args[1].substring("--target=".length());
                directory = args[2];
            } else {
                directory = args[1];
            }
            try {
                new ProjectInitializer().initialize(Path.of(directory), target);
                System.out.println("已初始化 MPL 项目：" + directory + "（target=" + target + "）");
            } catch (IOException | IllegalArgumentException exception) {
                System.err.println("初始化失败：" + exception.getMessage());
                System.exit(1);
            }
            return;
        }
        if (args.length == 3 && "check".equals(args[0]) && args[1].startsWith("--target=")) {
            String target = args[1].substring("--target=".length());
            CompilationResult result = new MplCompiler().compile(new CompilationRequest(Path.of(args[2]), target));
            result.diagnostics().forEach(MplCli::printDiagnostic);
            System.exit(result.succeeded() ? 0 : 1);
            return;
        }
        if (args.length == 4 && "build".equals(args[0]) && args[1].startsWith("--target=")) {
            String target = args[1].substring("--target=".length());
            CompilationResult result = new MplCompiler().compile(new CompilationRequest(Path.of(args[2]), target));
            result.diagnostics().forEach(MplCli::printDiagnostic);
            if (!result.succeeded()) {
                System.exit(1);
                return;
            }
            try {
                Files.writeString(Path.of(args[3]), result.mlog().orElseThrow());
            } catch (IOException exception) {
                System.err.println("无法写入 mlog 文件：" + exception.getMessage());
                System.exit(1);
            }
            return;
        }

        printUsage();
        System.exit(2);
    }

    private static void printUsage() {
        System.err.println("用法：mpl init [--target=<v146|v159.7>] <项目目录>");
        System.err.println("      mpl check --target=<v146|v159.7> <项目目录>");
        System.err.println("      mpl build --target=<v146|v159.7> <项目目录> <输出.mlog>");
    }

    private static void printDiagnostic(Diagnostic diagnostic) {
        System.err.printf("%s %s: %s%n", diagnostic.severity(), diagnostic.code(), diagnostic.message());
    }
}
