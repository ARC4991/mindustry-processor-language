package com.arc.mpl.project;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Downloads and verifies immutable registry package archives in a project-local cache. */
final class RegistryPackageCache {
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).build();
    private final PackageContentHasher hashes = new PackageContentHasher();

    Path materialize(Path projectRoot, String specification) throws IOException {
        String source = source(specification);
        URI uri = parseUri(source);
        Path cache = projectRoot.resolve(".mpl").resolve("registry");
        Files.createDirectories(cache);
        Path archive = Files.createTempFile(cache, ".download-", ".mplpkg");
        Path extracted = Files.createTempDirectory(cache, ".extract-");
        try {
            download(uri, archive);
            extract(archive, extracted);
            Path packageRoot = packageRoot(extracted);
            Path manifest = packageRoot.resolve("mpl.json");
            if (!Files.isRegularFile(manifest)) throw new IllegalArgumentException("registry 包缺少 mpl.json：" + source);
            String digest = hashes.packageDigest(packageRoot);
            Path destination = cache.resolve(digest);
            if (Files.exists(destination)) {
                deleteTree(extracted);
                return destination;
            }
            Files.move(packageRoot, destination, StandardCopyOption.ATOMIC_MOVE);
            return destination;
        } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
            Path packageRoot = packageRoot(extracted);
            String digest = hashes.packageDigest(packageRoot);
            Path destination = cache.resolve(digest);
            if (!Files.exists(destination)) Files.move(packageRoot, destination);
            return destination;
        } finally {
            Files.deleteIfExists(archive);
            deleteTree(extracted);
        }
    }

    Path cached(Path projectRoot, String contentSha256) {
        return projectRoot.toAbsolutePath().normalize().resolve(".mpl").resolve("registry").resolve(contentSha256);
    }

    private void download(URI uri, Path destination) throws IOException {
        if ("file".equalsIgnoreCase(uri.getScheme())) {
            Path source = Path.of(uri);
            if (!Files.isRegularFile(source)) throw new IOException("registry 文件不存在：" + source);
            Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
            return;
        }
        if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("registry 只支持 file、http 或 https：" + uri);
        }
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(REQUEST_TIMEOUT).GET().build();
        try {
            HttpResponse<Path> response = client.send(request, HttpResponse.BodyHandlers.ofFile(destination));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                Files.deleteIfExists(destination);
                throw new IOException("registry 下载失败，HTTP " + response.statusCode() + "：" + uri);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("registry 下载被中断：" + uri, exception);
        }
    }

    private void extract(Path archive, Path destination) throws IOException {
        try (InputStream input = Files.newInputStream(archive); ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                Path target = safeEntry(destination, entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(zip, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private Path packageRoot(Path extracted) throws IOException {
        if (Files.isRegularFile(extracted.resolve("mpl.json"))) return extracted;
        try (var children = Files.list(extracted)) {
            var roots = children.filter(Files::isDirectory).filter(path -> Files.isRegularFile(path.resolve("mpl.json"))).toList();
            if (roots.size() == 1) return roots.get(0);
        }
        throw new IllegalArgumentException("registry 压缩包必须在根目录或唯一顶层目录提供 mpl.json");
    }

    private Path safeEntry(Path root, String name) {
        if (name == null || name.isBlank() || name.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("registry 压缩包包含无效路径");
        }
        Path target = root.resolve(name).normalize();
        if (!target.startsWith(root)) throw new IllegalArgumentException("registry 压缩包包含目录穿越路径：" + name);
        return target;
    }

    private URI parseUri(String source) {
        try {
            return new URI(source);
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("registry URL 无效：" + source, exception);
        }
    }

    private String source(String specification) {
        String source = specification.substring("registry:".length());
        if (source.isBlank()) throw new IllegalArgumentException("registry 依赖 URL 不能为空");
        return source;
    }

    private void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }
}
