package com.arc.mpl.project;

import com.arc.mpl.profile.TargetProfile;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Locale;

/** Loads the optional runtime policy from mpl.json without exposing physical layout to source code. */
public final class RuntimePreferencesLoader {
    private static final ObjectMapper JSON = new ObjectMapper();

    public RuntimePreferences load(Path projectDirectory) throws IOException {
        Path file = projectDirectory.resolve("mpl.json");
        if (!Files.isRegularFile(file)) return RuntimePreferences.defaults();
        JsonNode runtime = JSON.readTree(Files.readString(file)).path("runtime");
        if (runtime.isMissingNode() || runtime.isNull()) return RuntimePreferences.defaults();
        RuntimePreferences.Goal goal = parseGoal(runtime.path("goal").asText("minResources"));
        RuntimePreferences defaults = RuntimePreferences.defaults();
        return new RuntimePreferences(goal, counts(runtime.path("processors"), TargetProfile.ProcessorKind.class, defaults.processors()),
            counts(runtime.path("memory"), RuntimePreferences.MemoryKind.class, defaults.memory()));
    }

    private RuntimePreferences.Goal parseGoal(String source) {
        return switch (source) {
            case "minResources" -> RuntimePreferences.Goal.MIN_RESOURCES;
            case "balanced" -> RuntimePreferences.Goal.BALANCED;
            case "maxPerformance" -> RuntimePreferences.Goal.MAX_PERFORMANCE;
            default -> throw new IllegalArgumentException("未知 runtime.goal：" + source);
        };
    }

    private <E extends Enum<E>> java.util.Map<E, Integer> counts(JsonNode node, Class<E> type, java.util.Map<E, Integer> defaults) {
        if (!node.isObject()) return defaults;
        java.util.Map<E, Integer> result = new EnumMap<>(type);
        node.properties().forEach(entry -> {
            try {
                int count = entry.getValue().intValue();
                if (count < 0) throw new IllegalArgumentException("runtime 数量不得为负数：" + entry.getKey());
                result.put(Enum.valueOf(type, entry.getKey().toUpperCase(Locale.ROOT)), count);
            } catch (IllegalArgumentException exception) { throw new IllegalArgumentException("未知 runtime 类型：" + entry.getKey(), exception); }
        });
        return result;
    }
}
