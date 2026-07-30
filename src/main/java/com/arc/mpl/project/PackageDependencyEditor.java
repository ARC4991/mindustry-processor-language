package com.arc.mpl.project;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.TreeMap;
import java.util.Map;

/** Adds one exact dependency while preserving all other manifest fields. */
public final class PackageDependencyEditor {
    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public void add(Path projectDirectory, String name, String specification) throws IOException {
        Path file = projectDirectory.toAbsolutePath().normalize().resolve("mpl.json");
        if (!Files.isRegularFile(file)) throw new IllegalArgumentException("项目缺少 mpl.json：" + projectDirectory);
        JsonNode parsed = JSON.readTree(Files.readString(file));
        if (!parsed.isObject()) throw new IllegalArgumentException("mpl.json 顶层必须是对象");
        ObjectNode root = (ObjectNode) parsed;
        JsonNode existing = root.get("dependencies");
        ObjectNode dependencies;
        if (existing == null || existing.isNull()) dependencies = root.putObject("dependencies");
        else if (existing.isObject()) dependencies = (ObjectNode) existing;
        else throw new IllegalArgumentException("mpl.json 的 dependencies 必须是对象");
        dependencies.put(name, specification);
        Map<String, JsonNode> ordered = new TreeMap<>();
        dependencies.properties().forEach(entry -> ordered.put(entry.getKey(), entry.getValue()));
        ObjectNode sorted = JSON.createObjectNode();
        ordered.forEach(sorted::set);
        root.set("dependencies", sorted);
        Files.writeString(file, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(root) + "\n",
            StandardCharsets.UTF_8);
    }
}
