package com.arc.mpl.project;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectSourceLoaderTest {
    private final ProjectSourceLoader loader = new ProjectSourceLoader();

    @Test
    void loadsTheConfiguredMilEntryAndDiscoversSourcesDeterministically(@TempDir Path project) throws Exception {
        Path source = Files.createDirectories(project.resolve("src/nested"));
        Files.writeString(project.resolve("mpl.json"), "{ \"entry\": \"src/nested/main.mil\" }");
        Files.writeString(project.resolve("src/z.mpl"), "var z = 1;");
        Files.writeString(source.resolve("main.mil"), "var a = 1;");
        Files.writeString(source.resolve("ignored.txt"), "not source");

        ProjectSourceCatalog catalog = loader.load(project);

        assertEquals(ProjectSourceLanguage.MIL, catalog.entryLanguage());
        assertEquals(source.resolve("main.mil").toAbsolutePath().normalize(), catalog.entryFile());
        assertEquals(java.util.List.of(source.resolve("main.mil").toAbsolutePath().normalize(),
            project.resolve("src/z.mpl").toAbsolutePath().normalize()), catalog.sourceFiles());
    }

    @Test
    void defaultsToMainMplWithoutMetadata(@TempDir Path project) throws Exception {
        Files.createDirectories(project.resolve("src"));

        ProjectSourceCatalog catalog = loader.load(project);

        assertEquals(ProjectSourceLanguage.MPL, catalog.entryLanguage());
        assertTrue(catalog.entryFile().endsWith("src/main.mpl"));
    }

    @Test
    void rejectsEntriesOutsideSrcAndUnsupportedExtensions(@TempDir Path project) throws Exception {
        Files.createDirectories(project.resolve("src"));
        Files.writeString(project.resolve("mpl.json"), "{ \"entry\": \"../main.mil\" }");

        assertThrows(IllegalArgumentException.class, () -> loader.load(project));

        Files.writeString(project.resolve("mpl.json"), "{ \"entry\": \"src/main.mlog\" }");
        assertThrows(IllegalArgumentException.class, () -> loader.load(project));
    }
}
