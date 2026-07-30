package com.arc.mpl.project;

import com.arc.mpl.memory.PhysicalMemoryLayout;
import com.arc.mpl.hir.ValueType;
import com.arc.mpl.profile.KnownProfiles;
import com.arc.mpl.profile.TargetProfile;
import com.arc.mpl.optimization.OptimizationReport;
import com.arc.mpl.optimization.ProfileOptimization;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.InflaterInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildArtifactWriterTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void namesTheSchematicFromProjectMetadataAndStoresTheCompleteBuildHash() throws Exception {
        TargetProfile profile = KnownProfiles.find("v146").orElseThrow();
        RuntimePlan plan = new RuntimePlanner().plan("set value 1\n", profile);

        new BuildArtifactWriter().write(temporaryDirectory, "set value 1\n", "set value 1\n", profile,
            new HardwareContract(List.of(), Map.of()), plan, new ProjectMetadata("circle-demo", "1.2.3"));

        Map<String, String> tags = readTags(temporaryDirectory.resolve("runtime.msch"));
        String hash = tags.get("mpl.buildHash");
        assertTrue(hash.matches("[0-9a-f]{64}"));
        assertEquals("MPL-circle-demo-1.2.3-" + hash.substring(0, 12), tags.get("name"));
        assertTrue(readProcessorConfig(temporaryDirectory.resolve("runtime.msch")).code()
            .startsWith("# MPL shard: Main / build: " + hash.substring(0, 12)));
    }

    @Test
    void writesReadableValidJsonArtifacts() throws Exception {
        TargetProfile profile = KnownProfiles.find("v146").orElseThrow();
        RuntimePlan plan = new RuntimePlanner().plan("set value 1\n", profile);

        new BuildArtifactWriter().write(temporaryDirectory, "set value 1\n", "set value 1\n", profile,
            new HardwareContract(List.of(), Map.of()), plan, new ProjectMetadata("json-demo", "1.0.0"));

        String report = java.nio.file.Files.readString(temporaryDirectory.resolve("report.json"));
        String deployment = java.nio.file.Files.readString(temporaryDirectory.resolve("deployment.json"));
        JsonNode parsed = new ObjectMapper().readTree(report);
        assertEquals("v146", parsed.path("targetProfile").asText());
        assertTrue(report.contains("\n  \"compiler\" : {"));
        assertTrue(deployment.contains("\n  \"runtimeTopology\" : {"));
        JsonNode deploymentJson = new ObjectMapper().readTree(deployment);
        JsonNode main = deploymentJson.path("runtimeTopology").path("shards").get(0);
        assertEquals("main", main.path("roles").get(0).asText());
        assertEquals(0, main.path("blueprintPosition").path("x").asInt());
        assertEquals(0, main.path("blueprintPosition").path("y").asInt());
        assertTrue(java.nio.file.Files.readString(temporaryDirectory.resolve("Main.mlog"))
            .startsWith("# MPL shard: Main / build: "));
        assertTrue(java.nio.file.Files.readString(temporaryDirectory.resolve("连接说明.txt"))
            .contains("Main 是蓝图最左侧处理器"));
    }

    @Test
    void recordsAppliedOptimizerStatisticsInTheBuildReport() throws Exception {
        TargetProfile profile = KnownProfiles.find("v146").orElseThrow();
        RuntimePlan plan = new RuntimePlanner().plan("set value 1\n", profile);

        new BuildArtifactWriter().write(temporaryDirectory, "set value 1\n", "set value 1\n", profile,
            new HardwareContract(List.of(), Map.of()), plan, new ProjectMetadata("optimization-demo", "1.0.0"),
            new OptimizationReport(3, 2, 1, 4));

        JsonNode report = new ObjectMapper().readTree(java.nio.file.Files.readString(temporaryDirectory.resolve("report.json")));
        assertEquals("Main", report.path("optimizations").get(0).path("shard").asText());
        assertEquals(3, report.path("optimizations").get(0).path("applied").asInt());
        assertEquals(2, report.path("optimizations").get(1).path("applied").asInt());
        assertEquals(1, report.path("optimizations").get(2).path("applied").asInt());
        assertEquals(4, report.path("optimizations").get(3).path("applied").asInt());
        assertTrue(report.path("optimizations").get(0).path("estimatedInstructionsSaved").isMissingNode());
    }

    @Test
    void recordsProfileSpecificLoweringSavingsInTheBuildReport() throws Exception {
        TargetProfile profile = KnownProfiles.find("v159.7").orElseThrow();
        RuntimePlan plan = new RuntimePlanner().plan("printchar 65\nstop\n", profile);
        OptimizationReport optimizations = OptimizationReport.NONE.withProfileOptimization(
            new ProfileOptimization("printcharStringOutput", 1, 9, 4));

        new BuildArtifactWriter().write(temporaryDirectory, "printchar 65\nstop\n", "", profile,
            new HardwareContract(List.of(), Map.of()), plan, new ProjectMetadata("profile-demo", "1.0.0"),
            optimizations);

        JsonNode report = new ObjectMapper().readTree(
            java.nio.file.Files.readString(temporaryDirectory.resolve("report.json")));
        JsonNode optimization = report.path("optimizations").get(4);
        assertEquals("printcharStringOutput", optimization.path("name").asText());
        assertEquals(1, optimization.path("applied").asInt());
        assertEquals(9, optimization.path("estimatedInstructionsSaved").asInt());
        assertEquals(4, optimization.path("estimatedLabelsSaved").asInt());
    }

    @Test
    void safelySerializesHardwareNamesAndAliasesAsStructuredJson() throws Exception {
        TargetProfile profile = KnownProfiles.find("v146").orElseThrow();
        RuntimePlan plan = new RuntimePlanner().plan("print \"ok\"\n", profile);
        HardwareContract hardware = new HardwareContract(
            List.of(new HardwareContract.LinkDeclaration("output\\\"name", "Message", "message\\\"1")), Map.of());

        new BuildArtifactWriter().write(temporaryDirectory, "print \"ok\"\n", "print(\"ok\");\n", profile,
            hardware, plan, new ProjectMetadata("json-demo", "1.0.0"));

        JsonNode deployment = new ObjectMapper().readTree(
            java.nio.file.Files.readString(temporaryDirectory.resolve("deployment.json")));
        JsonNode external = deployment.path("externalHardware").get(0);
        assertEquals("output\\\"name", external.path("mplName").asText());
        assertEquals("message\\\"1", external.path("alias").asText());
        assertTrue(java.nio.file.Files.readString(temporaryDirectory.resolve("连接说明.txt"))
            .contains("连接完成后无需重新写入代码"));
    }

    @Test
    void writesTheExactCompilerMemoryLayoutToTheBlueprintAndDeploymentManifest() throws Exception {
        TargetProfile profile = KnownProfiles.find("v146").orElseThrow();
        PhysicalMemoryLayout.StorageKey key = new PhysicalMemoryLayout.StorageKey(null, "values");
        PhysicalMemoryLayout layout = new PhysicalMemoryLayout(
            List.of(new PhysicalMemoryLayout.Segment("bank__mpl_mem0", RuntimePreferences.MemoryKind.BANK, 512, 3)),
            Map.of(key, new PhysicalMemoryLayout.Allocation(key, 3,
                List.of(new PhysicalMemoryLayout.Slice(0, 0, 0, 3)))), 3);
        String mlog = "write 1 bank__mpl_mem0 0\nstop\n";
        RuntimePlan plan = new RuntimePlanner().plan(mlog, profile, RuntimePreferences.defaults(), layout);

        new BuildArtifactWriter().write(temporaryDirectory, mlog, "val values: Int[] = [1, 2, 3];\n", profile,
            new HardwareContract(List.of(), Map.of()), plan, new ProjectMetadata("memory-demo", "1.0.0"));

        JsonNode report = new ObjectMapper().readTree(java.nio.file.Files.readString(temporaryDirectory.resolve("report.json")));
        JsonNode deployment = new ObjectMapper().readTree(
            java.nio.file.Files.readString(temporaryDirectory.resolve("deployment.json")));
        JsonNode segment = deployment.path("runtimeTopology").path("memorySegments").get(0);
        assertEquals(3, report.path("totals").path("physicalSlots").asInt());
        assertEquals("bank__mpl_mem0", segment.path("id").asText());
        assertEquals("bank", segment.path("kind").asText());
        assertEquals(512, segment.path("capacity").asInt());
        assertEquals(3, segment.path("usedSlots").asInt());
        assertEquals(1, segment.path("blueprintPosition").path("x").asInt());
        assertEquals(0, segment.path("blueprintPosition").path("y").asInt());
        assertEquals("bank__mpl_mem0", segment.path("bindings").get(0).path("alias").asText());
        assertTrue(segment.path("bindings").get(0).path("autoConnected").asBoolean());
        assertEquals(List.of(new LogicLink("bank__mpl_mem0", 1, 0)),
            readProcessorConfig(temporaryDirectory.resolve("runtime.msch")).links());
        assertEquals(List.of(new TilePlacement("micro-processor", 0, 0),
                new TilePlacement("memory-bank", 1, 0)),
            readTilePlacements(temporaryDirectory.resolve("runtime.msch")));
        assertTrue(java.nio.file.Files.readString(temporaryDirectory.resolve("连接说明.txt"))
            .contains("Runtime Memory 已自动连接到 Main"));
    }

    @Test
    void writesMultipleProcessorsAndConnectsSharedMemoryToEveryShard() throws Exception {
        TargetProfile profile = KnownProfiles.find("v146").orElseThrow();
        PhysicalMemoryLayout.StorageKey key = new PhysicalMemoryLayout.StorageKey(null, "shared");
        PhysicalMemoryLayout layout = new PhysicalMemoryLayout(
            List.of(new PhysicalMemoryLayout.Segment("bank__mpl_mem0", RuntimePreferences.MemoryKind.BANK, 512, 1)),
            Map.of(key, new PhysicalMemoryLayout.Allocation(key, 1,
                List.of(new PhysicalMemoryLayout.Slice(0, 0, 0, 1)))), 1);
        RuntimeTopologyPlan plan = new RuntimeTopologyPlan(List.of(
            new ShardPlan("Main", List.of("main", "io"), TargetProfile.ProcessorKind.MICRO, 2, 0, 4, 0),
            new ShardPlan("Worker-0", List.of("worker"), TargetProfile.ProcessorKind.MICRO, 2, 0, 4, 1)
        ), layout);

        new BuildArtifactWriter().write(temporaryDirectory, List.of(
            new BuildArtifactWriter.ShardArtifact("Main", "write 1 bank__mpl_mem0 0\nstop\n", ""),
            new BuildArtifactWriter.ShardArtifact("Worker-0", "read worker bank__mpl_mem0 0\nstop\n", "")
        ), profile, new HardwareContract(List.of(), Map.of()), plan,
            new ProjectMetadata("multi-demo", "1.0.0"), OptimizationReport.NONE);

        assertTrue(java.nio.file.Files.readString(temporaryDirectory.resolve("Main.mlog"))
            .startsWith("# MPL shard: Main / build: "));
        assertTrue(java.nio.file.Files.readString(temporaryDirectory.resolve("Worker-0.mlog"))
            .startsWith("# MPL shard: Worker-0 / build: "));
        JsonNode report = new ObjectMapper().readTree(
            java.nio.file.Files.readString(temporaryDirectory.resolve("report.json")));
        assertEquals(2, report.path("shards").size());
        assertEquals(4, report.path("totals").path("instructions").asInt());
        assertEquals(1, report.path("totals").path("physicalSlots").asInt());
        assertEquals(0, report.path("shards").get(1).path("resources").path("physicalSlots").asInt());
        JsonNode deployment = new ObjectMapper().readTree(
            java.nio.file.Files.readString(temporaryDirectory.resolve("deployment.json")));
        JsonNode bindings = deployment.path("runtimeTopology").path("memorySegments").get(0).path("bindings");
        assertEquals(List.of("Main", "Worker-0"), java.util.stream.StreamSupport.stream(bindings.spliterator(), false)
            .map(value -> value.path("shard").asText()).toList());
        assertTrue(java.nio.file.Files.readString(temporaryDirectory.resolve("连接说明.txt"))
            .contains("Runtime Memory 已自动连接到所有 shard"));
        assertEquals(List.of(
            new ProcessorPlacement("micro-processor", 0, 0,
                List.of(new LogicLink("bank__mpl_mem0", 0, 1))),
            new ProcessorPlacement("micro-processor", 1, 0,
                List.of(new LogicLink("bank__mpl_mem0", -1, 1)))
        ), readProcessorPlacements(temporaryDirectory.resolve("runtime.msch")));
    }

    @Test
    void reportsRealObjectPoolSlotsInsteadOfAPlaceholder() throws Exception {
        TargetProfile profile = KnownProfiles.find("v146").orElseThrow();
        PhysicalMemoryLayout.StorageKey occupancyKey = PhysicalMemoryLayout.objectPoolOccupancyKey("Counter");
        PhysicalMemoryLayout.StorageKey fieldKey = PhysicalMemoryLayout.objectPoolFieldKey("Counter", "value");
        PhysicalMemoryLayout.Allocation occupancy = new PhysicalMemoryLayout.Allocation(occupancyKey, 1,
            List.of(new PhysicalMemoryLayout.Slice(0, 0, 0, 1)));
        PhysicalMemoryLayout.Allocation field = new PhysicalMemoryLayout.Allocation(fieldKey, 1,
            List.of(new PhysicalMemoryLayout.Slice(0, 1, 0, 1)));
        Map<PhysicalMemoryLayout.StorageKey, PhysicalMemoryLayout.Allocation> allocations = new java.util.LinkedHashMap<>();
        allocations.put(occupancyKey, occupancy);
        allocations.put(fieldKey, field);
        PhysicalMemoryLayout.ObjectPool pool = new PhysicalMemoryLayout.ObjectPool("Counter", 1, 0, occupancy,
            Map.of("value", new PhysicalMemoryLayout.PoolField("value", ValueType.INT, 1, field)));
        PhysicalMemoryLayout layout = new PhysicalMemoryLayout(
            List.of(new PhysicalMemoryLayout.Segment("__mpl_mem0", RuntimePreferences.MemoryKind.CELL, 64, 2)),
            allocations, Map.of("Counter", pool), 2, 2);
        RuntimePlan plan = new RuntimePlanner().plan("write 0 __mpl_mem0 0\nstop\n", profile,
            RuntimePreferences.defaults(), layout);

        new BuildArtifactWriter().write(temporaryDirectory, "write 0 __mpl_mem0 0\nstop\n", "", profile,
            new HardwareContract(List.of(), Map.of()), plan, new ProjectMetadata("pool-demo", "1.0.0"));

        JsonNode report = new ObjectMapper().readTree(java.nio.file.Files.readString(temporaryDirectory.resolve("report.json")));
        assertEquals(2, report.path("totals").path("physicalSlots").asInt());
        assertEquals(2, report.path("totals").path("objectPoolSlots").asInt());
    }

    private Map<String, String> readTags(Path file) throws Exception {
        byte[] bytes = java.nio.file.Files.readAllBytes(file);
        try (DataInputStream stream = new DataInputStream(new InflaterInputStream(new ByteArrayInputStream(bytes, 5, bytes.length - 5)))) {
            stream.readShort();
            stream.readShort();
            int count = stream.readUnsignedByte();
            Map<String, String> tags = new HashMap<>();
            for (int index = 0; index < count; index++) tags.put(stream.readUTF(), stream.readUTF());
            return tags;
        }
    }

    private ProcessorConfig readProcessorConfig(Path file) throws Exception {
        byte[] bytes = java.nio.file.Files.readAllBytes(file);
        try (DataInputStream stream = new DataInputStream(
            new InflaterInputStream(new ByteArrayInputStream(bytes, 5, bytes.length - 5)))) {
            stream.readShort();
            stream.readShort();
            int tagCount = stream.readUnsignedByte();
            for (int index = 0; index < tagCount; index++) {
                stream.readUTF();
                stream.readUTF();
            }
            int blockCount = stream.readUnsignedByte();
            List<String> blocks = new java.util.ArrayList<>();
            for (int index = 0; index < blockCount; index++) blocks.add(stream.readUTF());
            int tileCount = stream.readInt();
            for (int index = 0; index < tileCount; index++) {
                String block = blocks.get(stream.readUnsignedByte());
                stream.readInt();
                int configType = stream.readUnsignedByte();
                byte[] config = configType == 14 ? stream.readNBytes(stream.readInt()) : null;
                stream.readUnsignedByte();
                if (block.endsWith("processor") && config != null) return readLogicConfig(config);
            }
        }
        throw new IllegalStateException("蓝图中缺少处理器配置");
    }

    private List<TilePlacement> readTilePlacements(Path file) throws Exception {
        byte[] bytes = java.nio.file.Files.readAllBytes(file);
        try (DataInputStream stream = new DataInputStream(
            new InflaterInputStream(new ByteArrayInputStream(bytes, 5, bytes.length - 5)))) {
            stream.readShort();
            stream.readShort();
            int tagCount = stream.readUnsignedByte();
            for (int index = 0; index < tagCount; index++) {
                stream.readUTF();
                stream.readUTF();
            }
            int blockCount = stream.readUnsignedByte();
            List<String> blocks = new java.util.ArrayList<>();
            for (int index = 0; index < blockCount; index++) blocks.add(stream.readUTF());
            int tileCount = stream.readInt();
            List<TilePlacement> placements = new java.util.ArrayList<>();
            for (int index = 0; index < tileCount; index++) {
                String block = blocks.get(stream.readUnsignedByte());
                int packed = stream.readInt();
                int x = (short)(packed >>> 16);
                int y = (short)packed;
                int configType = stream.readUnsignedByte();
                if (configType == 14) stream.skipNBytes(stream.readInt());
                stream.readUnsignedByte();
                placements.add(new TilePlacement(block, x, y));
            }
            return List.copyOf(placements);
        }
    }

    private List<ProcessorPlacement> readProcessorPlacements(Path file) throws Exception {
        byte[] bytes = java.nio.file.Files.readAllBytes(file);
        try (DataInputStream stream = new DataInputStream(
            new InflaterInputStream(new ByteArrayInputStream(bytes, 5, bytes.length - 5)))) {
            stream.readShort();
            stream.readShort();
            int tagCount = stream.readUnsignedByte();
            for (int index = 0; index < tagCount; index++) {
                stream.readUTF();
                stream.readUTF();
            }
            int blockCount = stream.readUnsignedByte();
            List<String> blocks = new java.util.ArrayList<>();
            for (int index = 0; index < blockCount; index++) blocks.add(stream.readUTF());
            int tileCount = stream.readInt();
            List<ProcessorPlacement> processors = new java.util.ArrayList<>();
            for (int index = 0; index < tileCount; index++) {
                String block = blocks.get(stream.readUnsignedByte());
                int packed = stream.readInt();
                int x = (short)(packed >>> 16);
                int y = (short)packed;
                int configType = stream.readUnsignedByte();
                byte[] config = configType == 14 ? stream.readNBytes(stream.readInt()) : null;
                stream.readUnsignedByte();
                if (block.endsWith("processor") && config != null) {
                    processors.add(new ProcessorPlacement(block, x, y, readLogicConfig(config).links()));
                }
            }
            return List.copyOf(processors);
        }
    }

    private ProcessorConfig readLogicConfig(byte[] config) throws Exception {
        try (DataInputStream stream = new DataInputStream(new InflaterInputStream(new ByteArrayInputStream(config)))) {
            stream.readUnsignedByte();
            String code = new String(stream.readNBytes(stream.readInt()), java.nio.charset.StandardCharsets.UTF_8);
            int linkCount = stream.readInt();
            List<LogicLink> links = new java.util.ArrayList<>();
            for (int index = 0; index < linkCount; index++) {
                links.add(new LogicLink(stream.readUTF(), stream.readShort(), stream.readShort()));
            }
            return new ProcessorConfig(code, List.copyOf(links));
        }
    }

    private record LogicLink(String alias, int relativeX, int relativeY) {
    }

    private record TilePlacement(String block, int x, int y) {
    }

    private record ProcessorPlacement(String block, int x, int y, List<LogicLink> links) {
    }

    private record ProcessorConfig(String code, List<LogicLink> links) {
    }
}
