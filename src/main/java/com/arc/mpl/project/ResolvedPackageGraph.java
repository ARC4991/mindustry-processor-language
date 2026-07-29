package com.arc.mpl.project;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Verified package roots and visibility edges consumed by the module linker. */
public record ResolvedPackageGraph(Set<String> rootDependencies, Map<String, ResolvedPackage> packages) {
    public ResolvedPackageGraph {
        rootDependencies = Collections.unmodifiableSet(new LinkedHashSet<>(
            Objects.requireNonNull(rootDependencies, "rootDependencies")));
        packages = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(packages, "packages")));
    }

    public static ResolvedPackageGraph empty() {
        return new ResolvedPackageGraph(Set.of(), Map.of());
    }

    public record ResolvedPackage(
        Path root,
        ProjectManifest manifest,
        ProjectSourceCatalog sources,
        PackageHardwareInterface hardwareInterface,
        Set<String> dependencies
    ) {
        public ResolvedPackage {
            root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
            Objects.requireNonNull(manifest, "manifest");
            Objects.requireNonNull(sources, "sources");
            Objects.requireNonNull(hardwareInterface, "hardwareInterface");
            dependencies = Collections.unmodifiableSet(new LinkedHashSet<>(
                Objects.requireNonNull(dependencies, "dependencies")));
        }
    }
}
