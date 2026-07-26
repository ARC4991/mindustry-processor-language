package com.arc.mpl.project;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectInitializerTest {
    @Test
    void createsABuildableV146Project(@TempDir Path temporaryDirectory) throws Exception {
        Path project = temporaryDirectory.resolve("frog-demo");

        new ProjectInitializer().initialize(project, "v146");

        assertTrue(Files.isRegularFile(project.resolve("mpl.json")));
        assertTrue(Files.isRegularFile(project.resolve("src/main.mpl")));
        assertTrue(Files.isRegularFile(project.resolve("src/hardware.mplh")));
        assertTrue(Files.readString(project.resolve("mpl.json")).contains("\"mindustry\": \"v146\""));
    }

    @Test
    void refusesToOverwriteANonEmptyDirectory(@TempDir Path temporaryDirectory) throws Exception {
        Path project = Files.createDirectory(temporaryDirectory.resolve("existing"));
        Files.writeString(project.resolve("important.mpl"), "var answer = 42;");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> new ProjectInitializer().initialize(project, "v146"));

        assertEquals("目标目录不是空目录：" + project, exception.getMessage());
    }
}
