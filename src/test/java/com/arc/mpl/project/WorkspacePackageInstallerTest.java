package com.arc.mpl.project;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkspacePackageInstallerTest {
    @Test
    void writesDeterministicTransitiveWorkspaceLock(@TempDir Path workspace) throws Exception {
        Path app = project(workspace.resolve("app"), "@demo/app", "0.1.0", "v146", """
            "@demo/panel": "workspace:../panel"
            """, "");
        project(workspace.resolve("core"), "@demo/core", "1.0.0", null, "", "");
        project(workspace.resolve("panel"), "@demo/panel", "2.3.4", null, """
            "@demo/core": "workspace:../core"
            """, "");

        WorkspacePackageInstaller installer = new WorkspacePackageInstaller();
        PackageLock first = installer.install(app);
        String firstJson = Files.readString(app.resolve("mpl.lock"));
        PackageLock second = installer.install(app);

        assertEquals(List.of("@demo/core", "@demo/panel"),
            first.packages().stream().map(PackageLock.LockedPackage::name).toList());
        assertEquals("1.0.0", first.packages().get(1).dependencies().get("@demo/core"));
        assertEquals(first, second);
        assertEquals(firstJson, Files.readString(app.resolve("mpl.lock")));
        assertTrue(firstJson.contains("\n  \"packages\" : ["));
        assertTrue(firstJson.endsWith("\n"));
        assertEquals(first, new PackageLockFile().read(app));
    }

    @Test
    void contentChangesProduceANewDigest(@TempDir Path workspace) throws Exception {
        Path app = project(workspace.resolve("app"), "app", "0.1.0", "v146",
            "\"lib\": \"workspace:../lib\"", "");
        Path library = project(workspace.resolve("lib"), "lib", "1.0.0", null, "", "");
        WorkspacePackageInstaller installer = new WorkspacePackageInstaller();

        String before = installer.install(app).packages().get(0).contentSha256();
        Files.writeString(library.resolve("src/index.mpl"), "export val answer: Int = 42;\n");
        String after = installer.install(app).packages().get(0).contentSha256();

        assertNotEquals(before, after);
    }

    @Test
    void rejectsCyclesRegistrySpecsAndMissingCapabilities(@TempDir Path workspace) throws Exception {
        Path app = project(workspace.resolve("app"), "app", "0.1.0", "v146",
            "\"a\": \"workspace:../a\"", "");
        project(workspace.resolve("a"), "a", "1.0.0", null,
            "\"b\": \"workspace:../b\"", "");
        project(workspace.resolve("b"), "b", "1.0.0", null,
            "\"a\": \"workspace:../a\"", "");

        IllegalArgumentException cycle = assertThrows(IllegalArgumentException.class,
            () -> new WorkspacePackageInstaller().install(app));
        assertTrue(cycle.getMessage().contains("a -> b -> a"));

        Files.writeString(app.resolve("mpl.json"), manifest("app", "0.1.0", "v146",
            "\"registry\": \"^1.0.0\"", ""));
        IllegalArgumentException registry = assertThrows(IllegalArgumentException.class,
            () -> new WorkspacePackageInstaller().install(app));
        assertTrue(registry.getMessage().contains("依赖来源必须以 workspace:、registry: 或 git:"));

        Files.writeString(app.resolve("mpl.json"), manifest("app", "0.1.0", "v146",
            "\"absolute\": \"workspace:" + workspace.resolve("a") + "\"", ""));
        IllegalArgumentException absolute = assertThrows(IllegalArgumentException.class,
            () -> new WorkspacePackageInstaller().install(app));
        assertTrue(absolute.getMessage().contains("必须使用相对路径"));

        Files.writeString(app.resolve("mpl.json"), manifest("app", "0.1.0", "v146",
            "\"a\": \"workspace:../a\"", ""));
        Files.writeString(workspace.resolve("a/mpl.json"), manifest("a", "1.0.0", null, "",
            "\"requires\": { \"capabilities\": [\"format\"] },"));
        IllegalArgumentException capability = assertThrows(IllegalArgumentException.class,
            () -> new WorkspacePackageInstaller().install(app));
        assertTrue(capability.getMessage().contains("format"));
    }

    @Test
    void rejectsTwoWorkspaceLocationsForTheSamePackage(@TempDir Path workspace) throws Exception {
        Path app = project(workspace.resolve("app"), "app", "0.1.0", "v146", """
            "left": "workspace:../left",
            "right": "workspace:../right"
            """, "");
        project(workspace.resolve("left"), "left", "1.0.0", null,
            "\"shared\": \"workspace:../shared-v1\"", "");
        project(workspace.resolve("right"), "right", "1.0.0", null,
            "\"shared\": \"workspace:../shared-v2\"", "");
        project(workspace.resolve("shared-v1"), "shared", "1.0.0", null, "", "");
        project(workspace.resolve("shared-v2"), "shared", "2.0.0", null, "", "");

        IllegalArgumentException conflict = assertThrows(IllegalArgumentException.class,
            () -> new WorkspacePackageInstaller().install(app));

        assertTrue(conflict.getMessage().contains("只能锁定一个包版本"));
    }

    @Test
    void installsAndResolvesRegistryArchiveFromFileUri(@TempDir Path workspace) throws Exception {
        Path packageRoot = project(workspace.resolve("registry-lib"), "registry-lib", "1.2.3", null, "", "");
        Path archive = workspace.resolve("registry-lib.mplpkg");
        zipDirectory(packageRoot, archive);
        Path app = project(workspace.resolve("app"), "app", "0.1.0", "v146",
            "\"registry-lib\": \"registry:" + archive.toUri() + "\"", "");

        PackageLock lock = new WorkspacePackageInstaller().install(app);
        assertEquals("registry:" + archive.toUri(), lock.packages().get(0).source());
        assertTrue(Files.isDirectory(app.resolve(".mpl/registry").resolve(lock.packages().get(0).contentSha256())));

        ResolvedPackageGraph graph = new LockedPackageResolver().resolve(app,
            com.arc.mpl.profile.KnownProfiles.find("v146").orElseThrow());
        assertEquals(List.of("registry-lib"), graph.packages().keySet().stream().toList());
    }

    private void zipDirectory(Path source, Path archive) throws Exception {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            try (var paths = Files.walk(source)) {
                for (Path path : paths.filter(Files::isRegularFile).toList()) {
                    String entry = source.relativize(path).toString().replace('\\', '/');
                    zip.putNextEntry(new ZipEntry(entry));
                    zip.write(Files.readAllBytes(path));
                    zip.closeEntry();
                }
            }
        }
    }

    private Path project(Path root, String name, String version, String target, String dependencies,
                         String extraRootField) throws Exception {
        Files.createDirectories(root.resolve("src"));
        Files.writeString(root.resolve("mpl.json"), manifest(name, version, target, dependencies, extraRootField));
        Files.writeString(root.resolve("src/index.mpl"), "export val marker: Int = 1;\n");
        Files.writeString(root.resolve("src/hardware.mplh"), "// no external hardware\n");
        return root;
    }

    private String manifest(String name, String version, String target, String dependencies, String extraRootField) {
        String targetField = target == null ? "" : "\"target\": { \"mindustry\": \"" + target + "\" },";
        String normalizedExtra = extraRootField == null ? "" : extraRootField;
        return """
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
            """.formatted(name, version, targetField, normalizedExtra, dependencies);
    }
}
