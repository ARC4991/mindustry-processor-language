package com.arc.mpl.project;

import com.arc.mpl.profile.TargetProfile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Verifies {@code mpl.lock} without updating it or resolving new dependency versions. */
public final class LockedPackageResolver {
    private final ProjectManifestLoader manifests = new ProjectManifestLoader();
    private final ProjectSourceLoader sources = new ProjectSourceLoader();
    private final PackageContentHasher hashes = new PackageContentHasher();
    private final PackageLockFile lockFiles = new PackageLockFile();
    private final HardwareLoader hardware = new HardwareLoader();
    private final RegistryPackageCache registry = new RegistryPackageCache();
    private final GitPackageCache git = new GitPackageCache();

    public ResolvedPackageGraph resolve(Path projectDirectory, TargetProfile profile) throws IOException {
        Path root = projectDirectory.toAbsolutePath().normalize();
        return resolve(root, manifests.load(root), profile);
    }

    public ResolvedPackageGraph resolve(Path projectDirectory, ProjectManifest rootManifest,
                                        TargetProfile profile) throws IOException {
        Path root = projectDirectory.toAbsolutePath().normalize();
        if (rootManifest.dependencies().isEmpty()) return ResolvedPackageGraph.empty();
        PackageLock lock = lockFiles.read(root);
        String currentRootDigest = hashes.fileDigest(root.resolve("mpl.json"));
        if (!lock.rootManifestSha256().equals(currentRootDigest)) {
            throw new IllegalArgumentException("mpl.lock 已过期：mpl.json 摘要不匹配，请执行 mpl install");
        }

        Resolver resolver = new Resolver(root, profile, lock);
        Set<String> direct = resolver.resolveDependencies(root, rootManifest.dependencies());
        return resolver.finish(direct);
    }

    private final class Resolver {
        private final Path root;
        private final TargetProfile profile;
        private final Map<String, PackageLock.LockedPackage> locks;
        private final Map<String, ResolvedPackageGraph.ResolvedPackage> resolved = new LinkedHashMap<>();
        private final Map<String, VisitState> states = new HashMap<>();
        private final Deque<String> stack = new ArrayDeque<>();

        private Resolver(Path root, TargetProfile profile, PackageLock lock) {
            this.root = root;
            this.profile = profile;
            Map<String, PackageLock.LockedPackage> indexed = new TreeMap<>();
            for (PackageLock.LockedPackage value : lock.packages()) {
                if (indexed.putIfAbsent(value.name(), value) != null) {
                    throw new IllegalArgumentException("mpl.lock 包含重复包：" + value.name());
                }
            }
            locks = Collections.unmodifiableMap(new LinkedHashMap<>(indexed));
        }

        private Set<String> resolveDependencies(Path owner, Map<String, String> dependencies) throws IOException {
            Set<String> result = new LinkedHashSet<>();
            for (Map.Entry<String, String> dependency : new TreeMap<>(dependencies).entrySet()) {
                resolve(owner, dependency.getKey(), dependency.getValue());
                result.add(dependency.getKey());
            }
            return Collections.unmodifiableSet(result);
        }

        private void resolve(Path owner, String name, String specification) throws IOException {
            PackageLock.LockedPackage locked = locks.get(name);
            if (locked == null) throw new IllegalArgumentException("mpl.lock 未锁定依赖：" + name);
            Path packageRoot;
            if (specification.startsWith("workspace:")) {
                if (!locked.source().startsWith("workspace:")) {
                    throw new IllegalArgumentException("mpl.lock 来源与 manifest 不一致：" + name);
                }
                String sourceText = specification.substring("workspace:".length());
                if (sourceText.isBlank()) throw new IllegalArgumentException("workspace 依赖路径不能为空：" + name);
                Path sourcePath = Path.of(sourceText);
                if (sourcePath.isAbsolute()) throw new IllegalArgumentException("workspace 依赖必须使用相对路径：" + name);
                Path requestedRoot = owner.resolve(sourcePath).toAbsolutePath().normalize();
                Path lockedPath = Path.of(locked.source().substring("workspace:".length()));
                if (lockedPath.isAbsolute()) throw new IllegalArgumentException("mpl.lock workspace 来源必须是相对路径：" + name);
                Path lockedRoot = root.resolve(lockedPath).toAbsolutePath().normalize();
                if (!requestedRoot.equals(lockedRoot)) {
                    throw new IllegalArgumentException("mpl.lock 来源与 manifest 不一致：" + name);
                }
                packageRoot = lockedRoot;
            } else if (specification.startsWith("registry:")) {
                if (!specification.equals(locked.source())) {
                    throw new IllegalArgumentException("mpl.lock registry 来源与 manifest 不一致：" + name);
                }
                packageRoot = registry.cached(root, locked.contentSha256());
                if (!Files.isDirectory(packageRoot)) {
                    throw new IllegalArgumentException("缺少锁定 registry 包缓存：" + name + "，请先执行 mpl install");
                }
            } else if (specification.startsWith("git:")) {
                if (!specification.equals(locked.source())) {
                    throw new IllegalArgumentException("mpl.lock git 来源与 manifest 不一致：" + name);
                }
                packageRoot = git.cached(root, locked.contentSha256());
                if (!Files.isDirectory(packageRoot)) {
                    throw new IllegalArgumentException("缺少锁定 Git 包缓存：" + name + "，请先执行 mpl install");
                }
            } else {
                throw new IllegalArgumentException("依赖来源必须以 workspace:、registry: 或 git: 开头：" + name);
            }

            VisitState state = states.get(name);
            if (state == VisitState.COMPLETE) return;
            if (state == VisitState.VISITING) {
                List<String> cycle = new ArrayList<>(stack);
                cycle.add(name);
                throw new IllegalArgumentException("锁定包依赖形成循环：" + String.join(" -> ", cycle));
            }
            states.put(name, VisitState.VISITING);
            stack.addLast(name);
            try {
                verifyAndResolve(locked, packageRoot);
                states.put(name, VisitState.COMPLETE);
            } finally {
                stack.removeLast();
            }
        }

        private void verifyAndResolve(PackageLock.LockedPackage locked, Path packageRoot) throws IOException {
            if (!Files.isDirectory(packageRoot) || !Files.isRegularFile(packageRoot.resolve("mpl.json"))) {
                throw new IllegalArgumentException("锁定包不存在：" + locked.name() + " -> " + packageRoot);
            }
            ProjectManifest manifest = manifests.load(packageRoot);
            if (!manifest.name().equals(locked.name()) || !manifest.version().equals(locked.version())) {
                throw new IllegalArgumentException("锁定包身份已变化：" + locked.name() + "@" + locked.version());
            }
            PackageCompatibility.requireSemanticVersion(manifest.version(), manifest.name());
            PackageCompatibility.validate(manifest, profile);
            Path hardware = WorkspacePackageInstaller.resolveInside(packageRoot, manifest.hardware(), "hardware");
            if (!Files.isRegularFile(hardware)) {
                throw new IllegalArgumentException("锁定包缺少硬件接口：" + manifest.name());
            }
            if (!hashes.packageDigest(packageRoot).equals(locked.contentSha256())) {
                throw new IllegalArgumentException("锁定包内容摘要不匹配：" + manifest.name() + "，请执行 mpl install");
            }
            if (!hashes.fileDigest(hardware).equals(locked.hardwareSha256())) {
                throw new IllegalArgumentException("锁定包硬件接口摘要不匹配：" + manifest.name() + "，请执行 mpl install");
            }
            PackageHardwareInterface hardwareInterface = LockedPackageResolver.this.hardware
                .loadPackageInterface(packageRoot, manifest);
            PackageHardwareValidator.validate(hardwareInterface, profile, manifest.name());

            Set<String> direct = resolveDependencies(packageRoot, manifest.dependencies());
            Map<String, String> exact = new TreeMap<>();
            for (String dependency : direct) exact.put(dependency, locks.get(dependency).version());
            if (!exact.equals(new TreeMap<>(locked.dependencies()))) {
                throw new IllegalArgumentException("锁定包依赖清单不匹配：" + manifest.name() + "，请执行 mpl install");
            }
            ProjectSourceCatalog catalog = sources.load(packageRoot, manifest);
            if (!Files.isRegularFile(catalog.entryFile())) {
                throw new IllegalArgumentException("锁定包入口不存在：" + manifest.name() + " -> " + manifest.entry());
            }
            resolved.put(manifest.name(), new ResolvedPackageGraph.ResolvedPackage(
                packageRoot, manifest, catalog, hardwareInterface, direct));
        }

        private ResolvedPackageGraph finish(Set<String> direct) {
            if (resolved.size() != locks.size()) {
                List<String> extras = locks.keySet().stream().filter(name -> !resolved.containsKey(name)).toList();
                throw new IllegalArgumentException("mpl.lock 包含根依赖图不可达的包：" + extras + "，请执行 mpl install");
            }
            Map<String, ResolvedPackageGraph.ResolvedPackage> sorted = new TreeMap<>(resolved);
            return new ResolvedPackageGraph(direct, sorted);
        }
    }

    private enum VisitState {
        VISITING,
        COMPLETE
    }
}
