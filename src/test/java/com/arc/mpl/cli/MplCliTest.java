package com.arc.mpl.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MplCliTest {
    @Test
    void buildWritesADeployableBlueprintBundleAndAppliesTheDebugLabelStyle(@TempDir Path project) throws IOException {
        Path sourceDirectory = Files.createDirectories(project.resolve("src"));
        Files.writeString(sourceDirectory.resolve("main.mpl"), "while (true) { }");
        Path outputDirectory = Files.createDirectories(project.resolve("artifacts"));
        Path mlog = outputDirectory.resolve("Main.mlog");
        Path mil = outputDirectory.resolve("Main.mil");

        MplCli.main(new String[]{
            "build", "--debug", "--lang=zh-CN", "--target=v146", project.toString(), outputDirectory.toString()
        });

        assertEquals("""
            mpl_while_start_0:
            jump mpl_while_end_1 equal 1 0
            jump mpl_while_start_0 always 0 0
            mpl_while_end_1:
            stop
            """, Files.readString(mlog));
        assertEquals("""
            // 由 MPL 自动生成的 MIL；请通过 mpl build 重新生成，勿直接编辑。
            // 普通结构保留为 MIL；@unit.* 与 @io.* 是由 target profile 展开的受限宏。
            while (true) {
            }
            """, Files.readString(mil));
        assertTrue(Files.isRegularFile(mlog));
        assertTrue(Files.isRegularFile(mil));
        assertTrue(Files.isRegularFile(outputDirectory.resolve("runtime.msch")));
        assertTrue(Files.isRegularFile(outputDirectory.resolve("report.json")));
        assertTrue(Files.isRegularFile(outputDirectory.resolve("deployment.json")));
        assertEquals("msch", new String(Files.readAllBytes(outputDirectory.resolve("runtime.msch")), 0, 4));
        assertTrue(Files.readString(outputDirectory.resolve("report.json")).contains("\"processor\""));
        assertTrue(Files.readString(outputDirectory.resolve("deployment.json")).contains("\"runtimeTopology\""));
    }
}
