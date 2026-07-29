package com.arc.mpl.project;

import com.arc.mpl.profile.KnownProfiles;
import com.arc.mpl.profile.TargetProfile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Resolves local workspace packages and writes the only mutable dependency artifact. */
public final class WorkspacePackageInstaller {
    private final ProjectManifestLoader manifests = new ProjectManifestLoader();
    private final PackageContentHasher hashes = new PackageContentHasher();
    private final PackageLockFile lockFile = new PackageLockFile();
    private final HardwareLoader hardware = new HardwareLoader();

    public PackageLock install(Path projectDirectory) throws IOException {
        Path root = projectDirectory.toAbsolutePath().normalize();
        Path rootManifestFile = root.resolve("mpl.json");
        if (!Files.isRegularFile(rootManifestFile)) throw new IllegalArgumentException("mpl install 需要项目根目录中的 mpl.json");
        ProjectManifest rootManifest = manifests.load(root);
        TargetProfile target = rootManifest.targetMindustry().flatMap(KnownProfiles::find)
            .orElseThrow(() -> new IllegalArgumentException("mpl install 需要有效的 target.mindustry"));

        Resolver resolver = new Resolver(root, target);
        resolver.resolveDependencies(root, rootManifest.dependencies());
        PackageLock lock = new PackageLock(1, hashes.fileDigest(rootManifestFile), resolver.lockedPackages());
        lockFile.write(root, lock);
        return lock;
    }

    private final class Resolver {
        private final Path root;
        private final TargetProfile target;
        private final Map<String, Resolved> resolved = new LinkedHashMap<>();
        private final Deque<String> stack = new ArrayDeque<>();

        private Resolver(Path root, TargetProfile target) {
            this.root = root;
            this.target = target;
        }

        private Map<String, String> resolveDependencies(Path owner, Map<String, String> dependencies) throws IOException {
            Map<String, String> exact = new TreeMap<>();
            for (Map.Entry<String, String> dependency : new TreeMap<>(dependencies).entrySet()) {
                Resolved value = resolve(owner, dependency.getKey(), dependency.getValue());
                exact.put(value.manifest().name(), value.manifest().version());
            }
            return Collections.unmodifiableMap(new LinkedHashMap<>(exact));
        }

        private Resolved resolve(Path owner, String requestedName, String specification) throws IOException {
            if (!specification.startsWith("workspace:")) {
                throw new IllegalArgumentException("registry 依赖尚未实现，请改用 workspace:：" + requestedName + " -> " + specification);
            }
            String sourceText = specification.substring("workspace:".length());
            if (sourceText.isBlank()) throw new IllegalArgumentException("workspace 依赖路径不能为空：" + requestedName);
            Path sourcePath = Path.of(sourceText);
            if (sourcePath.isAbsolute()) throw new IllegalArgumentException("workspace 依赖必须使用相对路径：" + requestedName);
            Path packageRoot = owner.resolve(sourcePath).toAbsolutePath().normalize();
            if (!Files.isDirectory(packageRoot) || !Files.isRegularFile(packageRoot.resolve("mpl.json"))) {
                throw new IllegalArgumentException("找不到 workspace 包：" + requestedName + " -> " + packageRoot);
            }
            ProjectManifest manifest = manifests.load(packageRoot);
            if (!manifest.name().equals(requestedName)) {
                throw new IllegalArgumentException("workspace 包名不匹配：依赖声明为 " + requestedName + "，manifest 为 " + manifest.name());
            }
            PackageCompatibility.requireSemanticVersion(manifest.version(), manifest.name());
            PackageCompatibility.validate(manifest, target);

            Resolved existing = resolved.get(requestedName);
            if (existing != null) {
                if (!existing.root().equals(packageRoot) || !existing.manifest().version().equals(manifest.version())) {
                    throw new IllegalArgumentException("同一链接单元只能锁定一个包版本：" + requestedName + " 已为 "
                        + existing.manifest().version() + "，又请求 " + manifest.version());
                }
                return existing;
            }
            if (stack.contains(requestedName)) {
                List<String> cycle = new ArrayList<>(stack);
                cycle.add(requestedName);
                throw new IllegalArgumentException("包依赖形成循环：" + String.join(" -> ", cycle));
            }

            Path hardware = resolveInside(packageRoot, manifest.hardware(), "hardware");
            if (!Files.isRegularFile(hardware) || !hardware.getFileName().toString().endsWith(".mplh")) {
                throw new IllegalArgumentException("外部包必须提供 .mplh 硬件接口：" + manifest.name() + " -> " + manifest.hardware());
            }
            PackageHardwareInterface hardwareInterface = WorkspacePackageInstaller.this.hardware
                .loadPackageInterface(packageRoot, manifest);
            PackageHardwareValidator.validate(hardwareInterface, target, manifest.name());
            resolveInside(packageRoot, manifest.entry(), "entry");
            stack.addLast(requestedName);
            Map<String, String> dependencies;
            try {
                dependencies = resolveDependencies(packageRoot, manifest.dependencies());
            } finally {
                stack.removeLast();
            }
            String relative = root.relativize(packageRoot).toString().replace('\\', '/');
            Resolved value = new Resolved(packageRoot, manifest,
                new PackageLock.LockedPackage(manifest.name(), manifest.version(), "workspace:" + relative,
                    hashes.packageDigest(packageRoot), hashes.fileDigest(hardware), dependencies));
            resolved.put(requestedName, value);
            return value;
        }

        private List<PackageLock.LockedPackage> lockedPackages() {
            return resolved.values().stream().map(Resolved::lock)
                .sorted(java.util.Comparator.comparing(PackageLock.LockedPackage::name)).toList();
        }
    }

    static Path resolveInside(Path packageRoot, String relativeText, String field) {
        Path relative;
        try {
            relative = Path.of(relativeText);
        } catch (java.nio.file.InvalidPathException exception) {
            throw new IllegalArgumentException("包 " + field + " 路径无效：" + relativeText, exception);
        }
        if (relative.isAbsolute()) throw new IllegalArgumentException("包 " + field + " 必须是相对路径：" + relativeText);
        Path resolved = packageRoot.resolve(relative).normalize();
        if (!resolved.startsWith(packageRoot.resolve("src").normalize())) {
            throw new IllegalArgumentException("包 " + field + " 必须位于 src：" + relativeText);
        }
        return resolved;
    }

    private record Resolved(Path root, ProjectManifest manifest, PackageLock.LockedPackage lock) {
    }
}
