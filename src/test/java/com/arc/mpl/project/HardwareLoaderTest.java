package com.arc.mpl.project;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HardwareLoaderTest {
    @Test
    void parsesLinksAndComposedDisplays(@TempDir Path project) throws IOException {
        Path source = Files.createDirectories(project.resolve("src"));
        Files.writeString(source.resolve("hardware.mplh"), """
            const Screen0: Display = link("display1");
            const Screen1: Display = link("display2");
            export const MainScreen: Display = Display.combine([[Screen0, Screen1]]);
            export const AlertBoard: Message = link("message1");
            """);

        HardwareContract contract = new HardwareLoader().load(project);

        assertEquals(3, contract.links().size());
        assertEquals("message1", contract.messages().get("AlertBoard"));
    }

    @Test
    void rejectsBusinessStatementsInHardwareFiles(@TempDir Path project) throws IOException {
        Path source = Files.createDirectories(project.resolve("src"));
        Files.writeString(source.resolve("hardware.mplh"), "var answer: Int = 42;");

        IOException error = assertThrows(IOException.class, () -> new HardwareLoader().load(project));

        assertTrue(error.getMessage().contains("语法错误"));
    }
}
