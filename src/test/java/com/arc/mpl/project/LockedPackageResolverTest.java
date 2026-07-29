package com.arc.mpl.project;

import com.arc.mpl.profile.KnownProfiles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LockedPackageResolverTest {
    @Test
    void resolvesOnlyVerifiedReachablePackages(@TempDir Path workspace) throws Exception {
        Path app = project(workspace.resolve("app"), "app", "0.1.0", "v146",
            "\"panel\": \"workspace:../panel\"", "");
        project(workspace.resolve("core"), "core", "1.0.0", null, "", "");
        project(workspace.resolve("panel"), "panel", "2.0.0", null,
            "\"core\": \"workspace:../core\"", "");
        new WorkspacePackageInstaller().install(app);

        ResolvedPackageGraph graph = new LockedPackageResolver().resolve(app,
            KnownProfiles.find("v146").orElseThrow());

        assertEquals(java.util.Set.of("panel"), graph.rootDependencies());
        assertEquals(java.util.Set.of("core", "panel"), graph.packages().keySet());
        assertEquals(java.util.Set.of("core"), graph.packages().get("panel").dependencies());
        assertTrue(graph.packages().get("core").sources().sourceFiles().stream()
            .anyMatch(file -> file.endsWith("src/index.mpl")));
    }

    @Test
    void rejectsMissingAndStaleLocksWithoutUpdatingThem(@TempDir Path workspace) throws Exception {
        Path app = project(workspace.resolve("app"), "app", "0.1.0", "v146",
            "\"lib\": \"workspace:../lib\"", "");
        Path library = project(workspace.resolve("lib"), "lib", "1.0.0", null, "", "");
        LockedPackageResolver resolver = new LockedPackageResolver();

        IllegalArgumentException missing = assertThrows(IllegalArgumentException.class,
            () -> resolver.resolve(app, KnownProfiles.find("v146").orElseThrow()));
        assertTrue(missing.getMessage().contains("缺少 mpl.lock"));

        new WorkspacePackageInstaller().install(app);
        String lockBefore = Files.readString(app.resolve("mpl.lock"));
        Files.writeString(library.resolve("src/index.mpl"), "export val changed: Int = 2;\n");
        IllegalArgumentException stalePackage = assertThrows(IllegalArgumentException.class,
            () -> resolver.resolve(app, KnownProfiles.find("v146").orElseThrow()));
        assertTrue(stalePackage.getMessage().contains("内容摘要不匹配"));
        assertEquals(lockBefore, Files.readString(app.resolve("mpl.lock")));

        new WorkspacePackageInstaller().install(app);
        Files.writeString(app.resolve("mpl.json"), Files.readString(app.resolve("mpl.json")) + "\n");
        IllegalArgumentException staleManifest = assertThrows(IllegalArgumentException.class,
            () -> resolver.resolve(app, KnownProfiles.find("v146").orElseThrow()));
        assertTrue(staleManifest.getMessage().contains("mpl.json 摘要不匹配"));
    }

    @Test
    void rechecksTargetCapabilitiesAtBuildTime(@TempDir Path workspace) throws Exception {
        Path app = project(workspace.resolve("app"), "app", "0.1.0", "v159.7",
            "\"format-lib\": \"workspace:../format-lib\"", "");
        project(workspace.resolve("format-lib"), "format-lib", "1.0.0", null, "",
            "\"requires\": { \"capabilities\": [\"format\"] },");
        new WorkspacePackageInstaller().install(app);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> new LockedPackageResolver().resolve(app, KnownProfiles.find("v146").orElseThrow()));

        assertTrue(error.getMessage().contains("format"));
    }

    private Path project(Path root, String name, String version, String target, String dependencies,
                         String... extraRootField) throws Exception {
        Files.createDirectories(root.resolve("src"));
        String targetField = target == null ? "" : "\"target\": { \"mindustry\": \"" + target + "\" },";
        String extra = extraRootField.length == 0 ? "" : extraRootField[0];
        Files.writeString(root.resolve("mpl.json"), """
            {
              "schemaVersion": 1,
              "name": "%s",
              "version": "%s",
              %s
              %s
              "entry": "src/index.mpl",
              "hardware": "src/hardware.mplh",
              "dependencies": { %s }
            }
            """.formatted(name, version, targetField, extra, dependencies));
        Files.writeString(root.resolve("src/index.mpl"), "export val marker: Int = 1;\n");
        Files.writeString(root.resolve("src/hardware.mplh"), "// no external hardware\n");
        return root;
    }
}
