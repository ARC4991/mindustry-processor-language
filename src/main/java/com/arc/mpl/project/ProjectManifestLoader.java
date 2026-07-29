package com.arc.mpl.project;

import com.arc.mpl.profile.TargetProfile;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/** Reads and validates all stable fields in {@code mpl.json} once. */
public final class ProjectManifestLoader {
    private static final ObjectMapper JSON = new ObjectMapper();

    public ProjectManifest load(Path projectDirectory) throws IOException {
        Path project = projectDirectory.toAbsolutePath().normalize();
        Path file = project.resolve("mpl.json");
        if (!Files.isRegularFile(file)) return defaults();
        JsonNode root = JSON.readTree(Files.readString(file));
        if (!root.isObject()) throw new IllegalArgumentException("mpl.json 顶层必须是对象");

        int schemaVersion = integer(root, "schemaVersion", 1);
        String name = text(root, "name", defaultName());
        String version = text(root, "version", "0.0.0");
        Optional<String> target = optionalNestedText(root.path("target"), "mindustry", "target.mindustry");
        String entry = text(root, "entry", "src/main.mpl");
        String hardware = text(root, "hardware", "src/hardware.mplh");
        Map<String, String> dependencies = stringMap(root.path("dependencies"), "dependencies");
        ProjectManifest.PackageRequirements requires = requirements(root.path("requires"));
        RuntimePreferences runtime = runtime(root.path("runtime"));
        return new ProjectManifest(schemaVersion, name, version, target, entry, hardware, dependencies, requires, runtime);
    }

    private ProjectManifest defaults() {
        return new ProjectManifest(1, defaultName(), "0.0.0", Optional.empty(),
            "src/main.mpl", "src/hardware.mplh", Map.of(), ProjectManifest.PackageRequirements.none(),
            RuntimePreferences.defaults());
    }

    private String defaultName() {
        return "mpl-project";
    }

    private ProjectManifest.PackageRequirements requirements(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) return ProjectManifest.PackageRequirements.none();
        if (!node.isObject()) throw new IllegalArgumentException("mpl.json 的 requires 必须是对象");
        Optional<String> minimum = Optional.empty();
        JsonNode mindustry = node.path("mindustry");
        if (!mindustry.isMissingNode() && !mindustry.isNull()) {
            if (!mindustry.isObject()) throw new IllegalArgumentException("mpl.json 的 requires.mindustry 必须是对象");
            minimum = optionalNestedText(mindustry, "min", "requires.mindustry.min");
        }
        Set<String> capabilities = stringSet(node.path("capabilities"), "requires.capabilities");
        return new ProjectManifest.PackageRequirements(minimum, capabilities);
    }

    private RuntimePreferences runtime(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) return RuntimePreferences.defaults();
        if (!node.isObject()) throw new IllegalArgumentException("mpl.json 的 runtime 必须是对象");
        RuntimePreferences.Goal goal = switch (text(node, "goal", "minResources")) {
            case "minResources" -> RuntimePreferences.Goal.MIN_RESOURCES;
            case "balanced" -> RuntimePreferences.Goal.BALANCED;
            case "maxPerformance" -> RuntimePreferences.Goal.MAX_PERFORMANCE;
            default -> throw new IllegalArgumentException("未知 runtime.goal：" + node.path("goal").asText());
        };
        RuntimePreferences defaults = RuntimePreferences.defaults();
        return new RuntimePreferences(goal,
            enumCounts(node.path("processors"), TargetProfile.ProcessorKind.class, defaults.processors(), "runtime.processors"),
            enumCounts(node.path("memory"), RuntimePreferences.MemoryKind.class, defaults.memory(), "runtime.memory"));
    }

    private <E extends Enum<E>> Map<E, Integer> enumCounts(JsonNode node, Class<E> type, Map<E, Integer> defaults,
                                                            String field) {
        if (node.isMissingNode() || node.isNull()) return defaults;
        if (!node.isObject()) throw new IllegalArgumentException("mpl.json 的 " + field + " 必须是对象");
        Map<E, Integer> result = new EnumMap<>(type);
        node.properties().forEach(entry -> {
            if (!entry.getValue().canConvertToInt()) {
                throw new IllegalArgumentException(field + "." + entry.getKey() + " 必须是整数");
            }
            int count = entry.getValue().intValue();
            if (count < 0) throw new IllegalArgumentException("runtime 数量不得为负数：" + entry.getKey());
            try {
                result.put(Enum.valueOf(type, entry.getKey().toUpperCase(Locale.ROOT)), count);
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("未知 runtime 类型：" + entry.getKey(), exception);
            }
        });
        return Map.copyOf(result);
    }

    private Map<String, String> stringMap(JsonNode node, String field) {
        if (node.isMissingNode() || node.isNull()) return Map.of();
        if (!node.isObject()) throw new IllegalArgumentException("mpl.json 的 " + field + " 必须是对象");
        Map<String, String> result = new TreeMap<>();
        node.properties().forEach(entry -> {
            if (entry.getKey().isBlank() || !entry.getValue().isTextual() || entry.getValue().asText().isBlank()) {
                throw new IllegalArgumentException("mpl.json 的 " + field + " 必须把非空包名映射到非空字符串");
            }
            result.put(entry.getKey(), entry.getValue().asText());
        });
        return Collections.unmodifiableMap(new LinkedHashMap<>(result));
    }

    private Set<String> stringSet(JsonNode node, String field) {
        if (node.isMissingNode() || node.isNull()) return Set.of();
        if (!node.isArray()) throw new IllegalArgumentException("mpl.json 的 " + field + " 必须是字符串数组");
        Set<String> result = new LinkedHashSet<>();
        node.forEach(value -> {
            if (!value.isTextual() || value.asText().isBlank()) {
                throw new IllegalArgumentException("mpl.json 的 " + field + " 只能包含非空字符串");
            }
            if (!result.add(value.asText())) throw new IllegalArgumentException("mpl.json 的 " + field + " 包含重复值：" + value.asText());
        });
        return Set.copyOf(result);
    }

    private Optional<String> optionalNestedText(JsonNode object, String field, String qualifiedField) {
        if (object.isMissingNode() || object.isNull()) return Optional.empty();
        if (!object.isObject()) throw new IllegalArgumentException("mpl.json 的 " + qualifiedField.substring(0, qualifiedField.lastIndexOf('.')) + " 必须是对象");
        JsonNode value = object.get(field);
        if (value == null || value.isNull()) return Optional.empty();
        if (!value.isTextual() || value.asText().isBlank()) {
            throw new IllegalArgumentException("mpl.json 的 " + qualifiedField + " 必须是非空字符串");
        }
        return Optional.of(value.asText());
    }

    private String text(JsonNode object, String field, String defaultValue) {
        JsonNode value = object.get(field);
        if (value == null || value.isNull()) return defaultValue;
        if (!value.isTextual() || value.asText().isBlank()) {
            throw new IllegalArgumentException("mpl.json 的 " + field + " 必须是非空字符串");
        }
        return value.asText();
    }

    private int integer(JsonNode object, String field, int defaultValue) {
        JsonNode value = object.get(field);
        if (value == null || value.isNull()) return defaultValue;
        if (!value.isIntegralNumber() || !value.canConvertToInt()) {
            throw new IllegalArgumentException("mpl.json 的 " + field + " 必须是整数");
        }
        return value.intValue();
    }
}
