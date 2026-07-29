package com.arc.mpl.project;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Project identity used in deployable artifact names. */
public record ProjectMetadata(String name, String version) {
    private static final ObjectMapper JSON = new ObjectMapper();

    public static ProjectMetadata load(Path projectDirectory) throws IOException {
        Path file = projectDirectory.resolve("mpl.json");
        if (!Files.isRegularFile(file)) return new ProjectMetadata("mpl-project", "0.0.0");
        JsonNode root = JSON.readTree(Files.readString(file));
        return new ProjectMetadata(root.path("name").asText("mpl-project"), root.path("version").asText("0.0.0"));
    }
}
