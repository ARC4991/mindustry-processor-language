package com.arc.mpl.project;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/** Computes platform-independent package and interface digests for lock validation. */
public final class PackageContentHasher {
    public String packageDigest(Path packageDirectory) throws IOException {
        Path root = packageDirectory.toAbsolutePath().normalize();
        List<Path> files;
        try (Stream<Path> paths = Files.walk(root)) {
            files = paths.filter(Files::isRegularFile)
                .filter(file -> included(root, file))
                .sorted(Comparator.comparing(file -> relative(root, file)))
                .toList();
        }
        MessageDigest digest = sha256();
        for (Path file : files) {
            byte[] relative = relative(root, file).getBytes(StandardCharsets.UTF_8);
            byte[] content = Files.readAllBytes(file);
            updateLength(digest, relative.length);
            digest.update(relative);
            updateLength(digest, content.length);
            digest.update(content);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    public String fileDigest(Path file) throws IOException {
        MessageDigest digest = sha256();
        if (Files.isRegularFile(file)) digest.update(Files.readAllBytes(file));
        return HexFormat.of().formatHex(digest.digest());
    }

    private boolean included(Path root, Path file) {
        Path relative = root.relativize(file);
        if (relative.getNameCount() == 1 && relative.toString().equals("mpl.json")) return true;
        if (relative.getNameCount() < 2 || !relative.getName(0).toString().equals("src")) return false;
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".mpl") || name.endsWith(".mil") || name.endsWith(".mplh");
    }

    private String relative(Path root, Path file) {
        return root.relativize(file).toString().replace('\\', '/');
    }

    private void updateLength(MessageDigest digest, int length) {
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(length).array());
    }

    private MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK 缺少 SHA-256", exception);
        }
    }
}
