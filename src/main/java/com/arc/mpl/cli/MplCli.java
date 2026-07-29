package com.arc.mpl.cli;

import com.arc.mpl.compiler.CompilationRequest;
import com.arc.mpl.compiler.CompilationResult;
import com.arc.mpl.compiler.MplCompiler;
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
        if ((args.length == 2 || args.length == 3) && "init".equals(args[0])) {
            String target = "v146";
            String directory;
            if (args.length == 3) {
                if (!args[1].startsWith("--target=")) {
                    printUsage(language);
                    System.exit(2);
                    return;
                }
                target = args[1].substring("--target=".length());
                directory = args[2];
            } else {
                directory = args[1];
            }
            try {
                new ProjectInitializer().initialize(Path.of(directory), target);
                System.out.println(message(language, "cli.init.success", directory, target));
            } catch (IOException | IllegalArgumentException exception) {
                System.err.println(message(language, "cli.init.failed", exceptionMessage(exception)));
                System.exit(1);
            }
            return;
        }
        if (args.length == 3 && "check".equals(args[0]) && args[1].startsWith("--target=")) {
            String target = args[1].substring("--target=".length());
            CompilationResult result = new MplCompiler().compile(new CompilationRequest(Path.of(args[2]), target));
            result.diagnostics().forEach(diagnostic -> printDiagnostic(diagnostic, language));
            System.exit(result.succeeded() ? 0 : 1);
            return;
        }
        BuildArguments build = parseBuildArguments(args);
        if (build != null) {
            CompilationResult result = new MplCompiler().compile(
                new CompilationRequest(Path.of(build.projectDirectory()), build.target(), build.debug()));
            result.diagnostics().forEach(diagnostic -> printDiagnostic(diagnostic, language));
            if (!result.succeeded()) {
                System.exit(1);
                return;
            }
            try {
                Path outputDirectory = Path.of(build.outputDirectory());
                HardwareContract hardware = new HardwareLoader().load(Path.of(build.projectDirectory()));
                RuntimePlan plan = new RuntimePlanner().plan(result.mlog().orElseThrow(), result.profile().orElseThrow(),
                    new RuntimePreferencesLoader().load(Path.of(build.projectDirectory())));
                new BuildArtifactWriter().write(outputDirectory, result.mlog().orElseThrow(), result.mil().orElseThrow(),
                    result.profile().orElseThrow(), hardware, plan, ProjectMetadata.load(Path.of(build.projectDirectory())));
                Path mlogOutput = outputDirectory.resolve("Main.mlog");
                Path milOutput = outputDirectory.resolve("Main.mil");
                System.out.println(message(language, "cli.mil.written", milOutput));
                System.out.println(message(language, "cli.mlog.written", mlogOutput));
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
        System.err.println(message(language, "cli.usage.check"));
        System.err.println(message(language, "cli.usage.build"));
    }

    /** Parses the build-only flags without making their order part of the CLI contract. */
    private static BuildArguments parseBuildArguments(String[] args) {
        if (args.length < 4 || args.length > 5 || !"build".equals(args[0])) return null;

        String target = null;
        boolean debug = false;
        List<String> positionals = new ArrayList<>();
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
            } else {
                positionals.add(argument);
            }
        }
        if (target == null || positionals.size() != 2) return null;
        return new BuildArguments(target, debug, positionals.get(0), positionals.get(1));
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

    private record BuildArguments(String target, boolean debug, String projectDirectory, String outputDirectory) {
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
