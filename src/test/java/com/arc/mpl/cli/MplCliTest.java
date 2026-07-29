package com.arc.mpl.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MplCliTest {
    @Test
    void buildAcceptsAMilEntryAndWritesTheNormalBlueprintBundle(@TempDir Path project) throws IOException {
        Path sourceDirectory = Files.createDirectories(project.resolve("src"));
        Files.writeString(project.resolve("mpl.json"), """
            { "name": "mil-demo", "version": "0.1.0", "entry": "src/main.mil" }
            """);
        Files.writeString(sourceDirectory.resolve("hardware.mplh"),
            "const Status: Message = link(\"message1\");\n");
        Files.writeString(sourceDirectory.resolve("main.mil"),
            "@io.print(@message1, \"MIL ready\");\n");
        Path outputDirectory = Files.createDirectories(project.resolve("artifacts"));

        MplCli.main(new String[]{
            "build", "--lang=zh-CN", "--target=v146", project.toString(), outputDirectory.toString()
        });

        assertTrue(Files.readString(outputDirectory.resolve("Main.mlog")).contains("print \"MIL ready\""));
        assertTrue(Files.isRegularFile(outputDirectory.resolve("Main.mil")));
        assertTrue(Files.isRegularFile(outputDirectory.resolve("runtime.msch")));
        assertTrue(Files.isRegularFile(outputDirectory.resolve("report.json")));
        assertTrue(Files.isRegularFile(outputDirectory.resolve("deployment.json")));
    }

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

        String deployedMlog = Files.readString(mlog);
        assertTrue(deployedMlog.startsWith("# MPL shard: Main / build: "));
        assertTrue(deployedMlog.endsWith("""
            mpl_while_start_0:
            jump mpl_while_end_1 equal 1 0
            jump mpl_while_start_0 always 0 0
            mpl_while_end_1:
            stop
            """));
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
        assertTrue(Files.isRegularFile(outputDirectory.resolve("连接说明.txt")));
        assertEquals("msch", new String(Files.readAllBytes(outputDirectory.resolve("runtime.msch")), 0, 4));
        assertTrue(Files.readString(outputDirectory.resolve("report.json")).contains("\"processor\""));
        assertTrue(Files.readString(outputDirectory.resolve("deployment.json")).contains("\"runtimeTopology\""));
    }

    @Test
    void buildCarriesDynamicArrayMemoryFromCompilationIntoEveryArtifact(@TempDir Path project) throws IOException {
        Path sourceDirectory = Files.createDirectories(project.resolve("src"));
        Files.writeString(sourceDirectory.resolve("main.mpl"), """
            var values: Int[] = [1, 2, 3];
            for (var i: Int = 0; i < values.size; i += 1) {
                values.set(i, values[i] + 1);
            }
            """);
        Path outputDirectory = Files.createDirectories(project.resolve("artifacts"));

        MplCli.main(new String[]{
            "build", "--lang=zh-CN", "--target=v146", project.toString(), outputDirectory.toString()
        });

        String mlog = Files.readString(outputDirectory.resolve("Main.mlog"));
        JsonNode report = new ObjectMapper().readTree(Files.readString(outputDirectory.resolve("report.json")));
        JsonNode deployment = new ObjectMapper().readTree(Files.readString(outputDirectory.resolve("deployment.json")));
        assertTrue(mlog.contains("read "));
        assertTrue(mlog.contains("write "));
        assertEquals(3, report.path("totals").path("physicalSlots").asInt());
        assertEquals("__mpl_mem0",
            deployment.path("runtimeTopology").path("memorySegments").get(0).path("id").asText());
        assertEquals("bank",
            deployment.path("runtimeTopology").path("memorySegments").get(0).path("kind").asText());
    }
}
