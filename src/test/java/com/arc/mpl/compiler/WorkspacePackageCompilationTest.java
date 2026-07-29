package com.arc.mpl.compiler;

import com.arc.mpl.project.WorkspacePackageInstaller;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkspacePackageCompilationTest {
    @Test
    void injectsARequiredPackageHardwareConstant(@TempDir Path workspace) throws Exception {
        Path app = project(workspace.resolve("app"), "app", "v146",
            "\"notifier\": \"workspace:../notifier\"", "main.mpl", """
                import { notify } from "notifier" with { output: StatusBoard };
                notify();
                """);
        Files.writeString(app.resolve("src/hardware.mplh"),
            "const StatusBoard: Message = link(\"message1\");\n");
        Path notifier = project(workspace.resolve("notifier"), "notifier", null, "", "index.mpl", """
            export fun notify(): Void { output.print("package ready"); }
            """);
        Files.writeString(notifier.resolve("src/hardware.mplh"),
            "require output: Message(access: write);\n");
        new WorkspacePackageInstaller().install(app);

        CompilationResult result = new MplCompiler().compile(new CompilationRequest(app, "v146"));

        assertTrue(result.succeeded(), () -> result.diagnostics().toString());
        assertTrue(result.mlog().orElseThrow().contains("printflush message1"));
        assertTrue(result.mil().orElseThrow().contains("@io.print(@message1, \"package ready\")"));
    }

    @Test
    void forwardsInjectedHardwareThroughATransitivePackage(@TempDir Path workspace) throws Exception {
        Path app = project(workspace.resolve("app"), "app", "v146",
            "\"wrapper\": \"workspace:../wrapper\"", "main.mpl", """
                import { notify } from "wrapper" with { sink: StatusBoard };
                notify();
                """);
        Files.writeString(app.resolve("src/hardware.mplh"),
            "const StatusBoard: Message = link(\"message1\");\n");
        Path leaf = project(workspace.resolve("leaf"), "leaf", null, "", "index.mpl", """
            export fun leafNotify(): Void { output.print("forwarded"); }
            """);
        Files.writeString(leaf.resolve("src/hardware.mplh"),
            "require output: Message(access: write);\n");
        Path wrapper = project(workspace.resolve("wrapper"), "wrapper", null,
            "\"leaf\": \"workspace:../leaf\"", "index.mpl", """
                import { leafNotify } from "leaf" with { output: sink };
                export fun notify(): Void { leafNotify(); }
                """);
        Files.writeString(wrapper.resolve("src/hardware.mplh"),
            "require sink: Message(access: write);\n");
        new WorkspacePackageInstaller().install(app);

        CompilationResult result = new MplCompiler().compile(new CompilationRequest(app, "v146"));

        assertTrue(result.succeeded(), () -> result.diagnostics().toString());
        assertTrue(result.mlog().orElseThrow().contains("printflush message1"));
    }

    @Test
    void validatesWithKeysTypesAndSingletonBindingsStrictly(@TempDir Path workspace) throws Exception {
        Path app = project(workspace.resolve("app"), "app", "v146",
            "\"notifier\": \"workspace:../notifier\"", "main.mpl", """
                import { notify } from "notifier";
                notify();
                """);
        Files.writeString(app.resolve("src/hardware.mplh"), """
            const First: Message = link("message1");
            const Second: Message = link("message2");
            const Canvas: Display = link("display1");
            """);
        Path notifier = project(workspace.resolve("notifier"), "notifier", null, "", "index.mpl", """
            export fun notify(): Void { output.print("strict"); }
            """);
        Files.writeString(notifier.resolve("src/hardware.mplh"),
            "require output: Message(access: write);\n");
        new WorkspacePackageInstaller().install(app);

        CompilationResult missing = new MplCompiler().compile(new CompilationRequest(app, "v146"));
        assertTrue(missing.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("MPL1413")));

        Files.writeString(app.resolve("src/main.mpl"), """
            import { notify } from "notifier" with { output: Canvas };
            notify();
            """);
        CompilationResult wrongType = new MplCompiler().compile(new CompilationRequest(app, "v146"));
        assertTrue(wrongType.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("MPL1415")
            && diagnostic.message().contains("类型不匹配")));

        Files.writeString(app.resolve("src/main.mpl"), """
            import { first } from "./first";
            import { second } from "./second";
            first();
            second();
            """);
        Files.writeString(app.resolve("src/first.mpl"), """
            import { notify } from "notifier" with { output: First };
            export fun first(): Void { notify(); }
            """);
        Files.writeString(app.resolve("src/second.mpl"), """
            import { notify } from "notifier" with { output: Second };
            export fun second(): Void { notify(); }
            """);
        CompilationResult inconsistent = new MplCompiler().compile(new CompilationRequest(app, "v146"));
        assertTrue(inconsistent.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("MPL1417")));
    }

    @Test
    void linksTransitiveMplAndMilWorkspacePackages(@TempDir Path workspace) throws Exception {
        Path app = project(workspace.resolve("app"), "app", "v146",
            "\"panel\": \"workspace:../panel\"", "main.mpl", """
                import { panelValue } from "panel";
                val answer: Int = panelValue();
                """);
        project(workspace.resolve("core"), "core", null, "", "index.mil", """
            export fun coreValue(): Int { return 41; }
            """);
        project(workspace.resolve("panel"), "panel", null,
            "\"core\": \"workspace:../core\"", "index.mpl", """
                import { coreValue } from "core";
                export fun panelValue(): Int { return coreValue() + 1; }
                """);
        new WorkspacePackageInstaller().install(app);

        CompilationResult result = new MplCompiler().compile(new CompilationRequest(app, "v146"));

        assertTrue(result.succeeded(), () -> result.diagnostics().toString());
        assertTrue(result.mil().orElseThrow().contains("panelValue"));
        assertTrue(result.mlog().orElseThrow().contains("\nstop\n"));
    }

    @Test
    void packageCannotImportAnUndeclaredLockedSibling(@TempDir Path workspace) throws Exception {
        Path app = project(workspace.resolve("app"), "app", "v146", """
            "core": "workspace:../core",
            "panel": "workspace:../panel"
            """, "main.mpl", """
                import { panelValue } from "panel";
                val answer = panelValue();
                """);
        project(workspace.resolve("core"), "core", null, "", "index.mpl",
            "export fun coreValue(): Int { return 1; }\n");
        project(workspace.resolve("panel"), "panel", null, "", "index.mpl", """
            import { coreValue } from "core";
            export fun panelValue(): Int { return coreValue(); }
            """);
        new WorkspacePackageInstaller().install(app);

        CompilationResult result = new MplCompiler().compile(new CompilationRequest(app, "v146"));

        assertFalse(result.succeeded());
        assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("MPL1401")
            && diagnostic.message().contains("未声明")));
    }

    @Test
    void packageCannotSeeRootHardwareWithoutWithInjection(@TempDir Path workspace) throws Exception {
        Path app = project(workspace.resolve("app"), "app", "v146",
            "\"panel\": \"workspace:../panel\"", "main.mpl", """
                import { render } from "panel";
                render();
                """);
        Files.writeString(app.resolve("src/hardware.mplh"),
            "const Status: Message = link(\"message1\");\n");
        project(workspace.resolve("panel"), "panel", null, "", "index.mpl", """
            export fun render(): Void { Status.print("not allowed"); }
            """);
        new WorkspacePackageInstaller().install(app);

        CompilationResult result = new MplCompiler().compile(new CompilationRequest(app, "v146"));

        assertFalse(result.succeeded());
        assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("MPL1414")
            && diagnostic.message().contains("未通过 with 注入")), () -> result.diagnostics().toString());
    }

    @Test
    void compilerRejectsMissingStaleLockAndUnimplementedHardwareArguments(@TempDir Path workspace) throws Exception {
        Path app = project(workspace.resolve("app"), "app", "v146",
            "\"panel\": \"workspace:../panel\"", "main.mpl", """
                import { render } from "panel" with { screen: MainScreen };
                render();
                """);
        project(workspace.resolve("panel"), "panel", null, "", "index.mpl",
            "export fun render(): Void { }\n");

        CompilationResult missing = new MplCompiler().compile(new CompilationRequest(app, "v146"));
        assertTrue(missing.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("MPL1401")));

        new WorkspacePackageInstaller().install(app);
        CompilationResult with = new MplCompiler().compile(new CompilationRequest(app, "v146"));
        assertTrue(with.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("MPL1413")));

        Files.writeString(workspace.resolve("panel/src/index.mpl"), "export fun render(): Void { val changed = 1; }\n");
        CompilationResult stale = new MplCompiler().compile(new CompilationRequest(app, "v146"));
        assertTrue(stale.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("MPL1401")
            && diagnostic.message().contains("摘要不匹配")));
    }

    private Path project(Path root, String name, String target, String dependencies, String entryName,
                         String source) throws Exception {
        Files.createDirectories(root.resolve("src"));
        String targetField = target == null ? "" : "\"target\": { \"mindustry\": \"" + target + "\" },";
        Files.writeString(root.resolve("mpl.json"), """
            {
              "schemaVersion": 1,
              "name": "%s",
              "version": "1.0.0",
              %s
              "entry": "src/%s",
              "hardware": "src/hardware.mplh",
              "dependencies": { %s }
            }
            """.formatted(name, targetField, entryName, dependencies));
        Files.writeString(root.resolve("src/" + entryName), source);
        Files.writeString(root.resolve("src/hardware.mplh"), "// no external hardware\n");
        return root;
    }
}
