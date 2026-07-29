package com.arc.mpl.project;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Reproducible dependency snapshot stored in {@code mpl.lock}. */
public record PackageLock(int schemaVersion, String rootManifestSha256, List<LockedPackage> packages) {
    public PackageLock {
        if (schemaVersion != 1) throw new IllegalArgumentException("不支持的 mpl.lock schemaVersion：" + schemaVersion);
        rootManifestSha256 = requireDigest(rootManifestSha256, "rootManifestSha256");
        packages = List.copyOf(Objects.requireNonNull(packages, "packages"));
    }

    public record LockedPackage(
        String name,
        String version,
        String source,
        String contentSha256,
        String hardwareSha256,
        Map<String, String> dependencies
    ) {
        public LockedPackage {
            if (name == null || name.isBlank()) throw new IllegalArgumentException("mpl.lock 包名不能为空");
            if (version == null || version.isBlank()) throw new IllegalArgumentException("mpl.lock 包版本不能为空：" + name);
            if (source == null || !source.startsWith("workspace:") || source.length() == "workspace:".length()) {
                throw new IllegalArgumentException("mpl.lock 包来源无效：" + name);
            }
            contentSha256 = requireDigest(contentSha256, name + ".contentSha256");
            hardwareSha256 = requireDigest(hardwareSha256, name + ".hardwareSha256");
            dependencies = Collections.unmodifiableMap(new LinkedHashMap<>(
                Objects.requireNonNull(dependencies, "dependencies")));
        }
    }

    private static String requireDigest(String value, String field) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("mpl.lock 的 " + field + " 必须是小写 SHA-256");
        }
        return value;
    }
}
