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

    @Test
    void parsesAndMergesPackageRequirements(@TempDir Path project) throws IOException {
        Path source = Files.createDirectories(project.resolve("src"));
        Files.writeString(source.resolve("hardware.mplh"), """
            require screen: Display(access: write);
            require screen: Display(minWidth: 176, minHeight: 176);
            require trigger: Switch(access: readWrite, count: 1);
            """);

        PackageHardwareInterface result = new HardwareLoader().loadPackageInterface(project,
            new ProjectManifestLoader().load(project));

        assertEquals(2, result.requirements().size());
        assertEquals(PackageHardwareInterface.Access.WRITE, result.requirements().get("screen").access());
        assertEquals(176, result.requirements().get("screen").minimumWidth());
        assertEquals(PackageHardwareInterface.Access.READ_WRITE, result.requirements().get("trigger").access());
    }

    @Test
    void keepsRootAndPackageHardwareLanguagesSeparate(@TempDir Path project) throws IOException {
        Path source = Files.createDirectories(project.resolve("src"));
        Files.writeString(source.resolve("hardware.mplh"),
            "require output: Message(access: write);\n");
        IOException rootError = assertThrows(IOException.class, () -> new HardwareLoader().load(project));
        assertTrue(rootError.getMessage().contains("根项目"));

        Files.writeString(source.resolve("hardware.mplh"),
            "const Output: Message = link(\"message1\");\n");
        IOException packageError = assertThrows(IOException.class, () -> new HardwareLoader().loadPackageInterface(project,
            new ProjectManifestLoader().load(project)));
        assertTrue(packageError.getMessage().contains("包 .mplh"));

        Files.writeString(source.resolve("hardware.mplh"),
            "require storage: MemoryBank(access: readWrite);\n");
        IOException memoryError = assertThrows(IOException.class, () -> new HardwareLoader().loadPackageInterface(project,
            new ProjectManifestLoader().load(project)));
        assertTrue(memoryError.getMessage().contains("不能 require Memory"));
    }
}
