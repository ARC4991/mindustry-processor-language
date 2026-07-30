package com.arc.mpl.project;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Comparator;

/** Materializes a Git package into the same content-addressed cache as registry archives. */
final class GitPackageCache {
    private static final Duration TIMEOUT = Duration.ofMinutes(2);
    private final PackageContentHasher hashes = new PackageContentHasher();

    Path materialize(Path projectRoot, String specification) throws IOException {
        String url = source(specification);
        Path cache = projectRoot.toAbsolutePath().normalize().resolve(".mpl").resolve("git");
        Files.createDirectories(cache);
        Path checkout = Files.createTempDirectory(cache, ".checkout-");
        try {
            runGit(checkout, "clone", "--depth", "1", "--", url, checkout.toString());
            Path packageRoot = packageRoot(checkout);
            if (!Files.isRegularFile(packageRoot.resolve("mpl.json"))) {
                throw new IllegalArgumentException("Git 包缺少 mpl.json：" + url);
            }
            String digest = hashes.packageDigest(packageRoot);
            Path destination = projectRoot.toAbsolutePath().normalize().resolve(".mpl").resolve("registry").resolve(digest);
            if (Files.isDirectory(destination)) return destination;
            Files.createDirectories(destination.getParent());
            copyTree(packageRoot, destination);
            return destination;
        } finally {
            deleteTree(checkout);
        }
    }

    Path cached(Path projectRoot, String digest) {
        return projectRoot.toAbsolutePath().normalize().resolve(".mpl").resolve("registry").resolve(digest);
    }

    private String source(String specification) {
        String value = specification.substring("git:".length()).trim();
        if (value.isBlank()) throw new IllegalArgumentException("git 依赖 URL 不能为空");
        return value;
    }

    private Path packageRoot(Path checkout) throws IOException {
        if (Files.isRegularFile(checkout.resolve("mpl.json"))) return checkout;
        try (var children = Files.list(checkout)) {
            var roots = children.filter(Files::isDirectory)
                .filter(path -> Files.isRegularFile(path.resolve("mpl.json"))).toList();
            if (roots.size() == 1) return roots.get(0);
        }
        throw new IllegalArgumentException("Git 包必须在根目录或唯一顶层目录提供 mpl.json");
    }

    private void runGit(Path directory, String... arguments) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(arguments).directory(directory.toFile())
            .redirectErrorStream(true);
        Process process = builder.start();
        try {
            if (!process.waitFor(TIMEOUT.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw new IOException("Git 操作超时");
            }
            if (process.exitValue() != 0) {
                String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
                throw new IOException("Git 克隆失败：" + (output.isBlank() ? "退出码 " + process.exitValue() : output));
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Git 操作被中断", exception);
        }
    }

    private void copyTree(Path source, Path target) throws IOException {
        try (var paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                Path relative = source.relativize(path);
                if (relative.getNameCount() > 0 && relative.getName(0).toString().equals(".git")) continue;
                Path destination = target.resolve(relative);
                if (Files.isDirectory(path)) Files.createDirectories(destination);
                else Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }
}
