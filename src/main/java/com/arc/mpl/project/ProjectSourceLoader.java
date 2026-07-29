package com.arc.mpl.project;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/** Loads the configured entry and discovers all future module sources under {@code src}. */
public final class ProjectSourceLoader {
    private static final ObjectMapper JSON = new ObjectMapper();

    public ProjectSourceCatalog load(Path projectDirectory) throws IOException {
        Path project = projectDirectory.toAbsolutePath().normalize();
        Path sourceRoot = project.resolve("src");
        String entryText = readEntry(project.resolve("mpl.json"));
        Path relativeEntry;
        try {
            relativeEntry = Path.of(entryText);
        } catch (InvalidPathException exception) {
            throw new IllegalArgumentException("项目入口路径无效：" + entryText, exception);
        }
        if (relativeEntry.isAbsolute()) throw new IllegalArgumentException("项目入口必须使用相对路径：" + entryText);
        Path entry = project.resolve(relativeEntry).normalize();
        if (!entry.startsWith(sourceRoot)) {
            throw new IllegalArgumentException("项目入口必须位于 src 目录：" + entryText);
        }
        ProjectSourceLanguage language = language(entry);
        return new ProjectSourceCatalog(sourceRoot, entry, language, discover(sourceRoot));
    }

    private String readEntry(Path metadataFile) throws IOException {
        if (!Files.isRegularFile(metadataFile)) return "src/main.mpl";
        JsonNode root = JSON.readTree(Files.readString(metadataFile));
        JsonNode entry = root.get("entry");
        if (entry == null) return "src/main.mpl";
        if (!entry.isTextual() || entry.asText().isBlank()) {
            throw new IllegalArgumentException("mpl.json 的 entry 必须是非空字符串");
        }
        return entry.asText();
    }

    private ProjectSourceLanguage language(Path entry) {
        String name = entry.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".mpl")) return ProjectSourceLanguage.MPL;
        if (name.endsWith(".mil")) return ProjectSourceLanguage.MIL;
        throw new IllegalArgumentException("项目入口只支持 .mpl 或 .mil：" + entry);
    }

    private List<Path> discover(Path sourceRoot) throws IOException {
        if (!Files.isDirectory(sourceRoot)) return List.of();
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            return paths.filter(Files::isRegularFile)
                .filter(this::isSource)
                .map(Path::toAbsolutePath)
                .map(Path::normalize)
                .sorted(Comparator.comparing(Path::toString))
                .toList();
        }
    }

    private boolean isSource(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".mpl") || name.endsWith(".mil");
    }
}
