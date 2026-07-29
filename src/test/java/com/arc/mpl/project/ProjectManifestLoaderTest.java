package com.arc.mpl.project;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProjectManifestLoaderTest {
    @Test
    void readsStablePackageAndRuntimeFields(@TempDir Path project) throws Exception {
        Files.writeString(project.resolve("mpl.json"), """
            {
              "schemaVersion": 1,
              "name": "@demo/app",
              "version": "1.2.3",
              "target": { "mindustry": "v159.7" },
              "entry": "src/start.mil",
              "hardware": "src/board.mplh",
              "dependencies": { "@demo/b": "workspace:../b", "@demo/a": "workspace:../a" },
              "requires": {
                "mindustry": { "min": "v146" },
                "capabilities": ["format", "select"]
              },
              "runtime": {
                "goal": "balanced",
                "processors": { "logic": 2 },
                "memory": { "bank": 1 }
              }
            }
            """);

        ProjectManifest manifest = new ProjectManifestLoader().load(project);

        assertEquals("@demo/app", manifest.name());
        assertEquals("v159.7", manifest.targetMindustry().orElseThrow());
        assertEquals("src/start.mil", manifest.entry());
        assertEquals("src/board.mplh", manifest.hardware());
        assertEquals(java.util.List.of("@demo/a", "@demo/b"), manifest.dependencies().keySet().stream().toList());
        assertEquals(java.util.Set.of("format", "select"), manifest.requires().capabilities());
        assertEquals(RuntimePreferences.Goal.BALANCED, manifest.runtime().goal());
    }

    @Test
    void rejectsMalformedStableFields(@TempDir Path project) throws Exception {
        Files.writeString(project.resolve("mpl.json"), "{ \"schemaVersion\": 2 }");
        assertThrows(IllegalArgumentException.class, () -> new ProjectManifestLoader().load(project));

        Files.writeString(project.resolve("mpl.json"), "{ \"dependencies\": [] }");
        assertThrows(IllegalArgumentException.class, () -> new ProjectManifestLoader().load(project));

        Files.writeString(project.resolve("mpl.json"), "{ \"runtime\": { \"processors\": { \"logic\": -1 } } }");
        assertThrows(IllegalArgumentException.class, () -> new ProjectManifestLoader().load(project));
    }
}
