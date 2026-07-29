package com.arc.mpl.project;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Immutable, validated view of one root project or package manifest. */
public record ProjectManifest(
    int schemaVersion,
    String name,
    String version,
    Optional<String> targetMindustry,
    String entry,
    String hardware,
    Map<String, String> dependencies,
    PackageRequirements requires,
    RuntimePreferences runtime
) {
    public ProjectManifest {
        if (schemaVersion != 1) throw new IllegalArgumentException("不支持的 mpl.json schemaVersion：" + schemaVersion);
        name = requireText(name, "name");
        version = requireText(version, "version");
        targetMindustry = Objects.requireNonNull(targetMindustry, "targetMindustry")
            .map(value -> requireText(value, "target.mindustry"));
        entry = requireText(entry, "entry");
        hardware = requireText(hardware, "hardware");
        dependencies = Collections.unmodifiableMap(new LinkedHashMap<>(
            Objects.requireNonNull(dependencies, "dependencies")));
        requires = Objects.requireNonNull(requires, "requires");
        runtime = Objects.requireNonNull(runtime, "runtime");
    }

    public record PackageRequirements(Optional<String> minimumMindustry, Set<String> capabilities) {
        public PackageRequirements {
            minimumMindustry = Objects.requireNonNull(minimumMindustry, "minimumMindustry")
                .map(value -> requireText(value, "requires.mindustry.min"));
            capabilities = Set.copyOf(Objects.requireNonNull(capabilities, "capabilities"));
        }

        public static PackageRequirements none() {
            return new PackageRequirements(Optional.empty(), Set.of());
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("mpl.json 的 " + field + " 必须是非空字符串");
        return value;
    }
}
