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
