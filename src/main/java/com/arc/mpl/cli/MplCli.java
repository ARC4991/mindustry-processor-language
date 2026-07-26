package com.arc.mpl.cli;

import com.arc.mpl.compiler.CompilationRequest;
import com.arc.mpl.compiler.CompilationResult;
import com.arc.mpl.compiler.MplCompiler;
import com.arc.mpl.diagnostic.Diagnostic;

import java.nio.file.Path;

/** Minimal CLI shell; command parsing grows without leaking into compiler phases. */
public final class MplCli {
    private MplCli() {
    }

    public static void main(String[] args) {
        if (args.length == 1 && "--version".equals(args[0])) {
            System.out.println("mpl 0.1.0-SNAPSHOT");
            return;
        }
        if (args.length == 3 && "check".equals(args[0]) && args[1].startsWith("--target=")) {
            String target = args[1].substring("--target=".length());
            CompilationResult result = new MplCompiler().compile(new CompilationRequest(Path.of(args[2]), target));
            result.diagnostics().forEach(MplCli::printDiagnostic);
            System.exit(result.succeeded() ? 0 : 1);
            return;
        }

        System.err.println("用法：mpl check --target=<v146|v159.7> <项目目录>");
        System.exit(2);
    }

    private static void printDiagnostic(Diagnostic diagnostic) {
        System.err.printf("%s %s: %s%n", diagnostic.severity(), diagnostic.code(), diagnostic.message());
    }
}
