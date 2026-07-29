package com.arc.mpl.project;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Strict deterministic reader/writer for the committed dependency lock. */
public final class PackageLockFile {
    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public PackageLock read(Path projectDirectory) throws IOException {
        Path file = projectDirectory.toAbsolutePath().normalize().resolve("mpl.lock");
        if (!Files.isRegularFile(file)) throw new IllegalArgumentException("缺少 mpl.lock，请先执行 mpl install");
        JsonNode root = JSON.readTree(Files.readString(file));
        if (!root.isObject()) throw new IllegalArgumentException("mpl.lock 顶层必须是对象");
        int schemaVersion = requiredInt(root, "schemaVersion");
        String rootDigest = requiredText(root, "rootManifestSha256");
        JsonNode packageNodes = root.get("packages");
        if (packageNodes == null || !packageNodes.isArray()) throw new IllegalArgumentException("mpl.lock 的 packages 必须是数组");
        List<PackageLock.LockedPackage> packages = new ArrayList<>();
        for (JsonNode node : packageNodes) {
            if (!node.isObject()) throw new IllegalArgumentException("mpl.lock 的 packages 只能包含对象");
            packages.add(new PackageLock.LockedPackage(requiredText(node, "name"), requiredText(node, "version"),
                requiredText(node, "source"), requiredText(node, "contentSha256"), requiredText(node, "hardwareSha256"),
                stringMap(node.get("dependencies"), "dependencies")));
        }
        return new PackageLock(schemaVersion, rootDigest, packages);
    }

    public void write(Path projectDirectory, PackageLock lock) throws IOException {
        Path project = projectDirectory.toAbsolutePath().normalize();
        Files.createDirectories(project);
        ObjectNode root = JSON.createObjectNode();
        root.put("schemaVersion", lock.schemaVersion());
        root.put("rootManifestSha256", lock.rootManifestSha256());
        ArrayNode packages = root.putArray("packages");
        lock.packages().stream().sorted(java.util.Comparator.comparing(PackageLock.LockedPackage::name)).forEach(value -> {
            ObjectNode node = packages.addObject();
            node.put("name", value.name());
            node.put("version", value.version());
            node.put("source", value.source());
            node.put("contentSha256", value.contentSha256());
            node.put("hardwareSha256", value.hardwareSha256());
            ObjectNode dependencies = node.putObject("dependencies");
            new TreeMap<>(value.dependencies()).forEach(dependencies::put);
        });
        byte[] bytes = (JSON.writerWithDefaultPrettyPrinter().writeValueAsString(root) + "\n")
            .getBytes(StandardCharsets.UTF_8);
        Path temporary = Files.createTempFile(project, ".mpl-lock-", ".tmp");
        try {
            Files.write(temporary, bytes);
            Files.move(temporary, project.resolve("mpl.lock"), StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException exception) {
            Files.deleteIfExists(temporary);
            throw exception;
        }
    }

    private Map<String, String> stringMap(JsonNode node, String field) {
        if (node == null || !node.isObject()) throw new IllegalArgumentException("mpl.lock 的 " + field + " 必须是对象");
        Map<String, String> sorted = new TreeMap<>();
        node.properties().forEach(entry -> {
            if (entry.getKey().isBlank() || !entry.getValue().isTextual() || entry.getValue().asText().isBlank()) {
                throw new IllegalArgumentException("mpl.lock 的 " + field + " 必须把非空包名映射到非空版本");
            }
            sorted.put(entry.getKey(), entry.getValue().asText());
        });
        return Collections.unmodifiableMap(new LinkedHashMap<>(sorted));
    }

    private String requiredText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new IllegalArgumentException("mpl.lock 缺少非空字符串字段：" + field);
        }
        return value.asText();
    }

    private int requiredInt(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()) {
            throw new IllegalArgumentException("mpl.lock 缺少整数字段：" + field);
        }
        return value.intValue();
    }
}
