package com.arc.mpl.project;

import com.arc.mpl.profile.KnownProfiles;
import com.arc.mpl.profile.TargetProfile;
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
}
