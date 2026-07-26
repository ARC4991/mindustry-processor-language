package com.arc.mpl.project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Temporary focused loader for the first Message declarations in a project .mplh file. */
public final class HardwareLoader {
    private static final Pattern MESSAGE = Pattern.compile("(?:export\\s+)?const\\s+(?<name>[A-Za-z_][A-Za-z0-9_]*)\\s*:\\s*Message\\s*=\\s*link\\(\\s*(?:\\\"(?<link>[A-Za-z_][A-Za-z0-9_]*)\\\")?\\s*\\)\\s*;");

    public Map<String, String> loadMessages(Path projectDirectory) throws IOException {
        Path file = projectDirectory.resolve("src/hardware.mplh");
        if (!Files.isRegularFile(file)) return Map.of();
        Matcher matcher = MESSAGE.matcher(Files.readString(file));
        Map<String, String> messages = new HashMap<>();
        int automaticIndex = 1;
        while (matcher.find()) {
            String link = matcher.group("link");
            messages.put(matcher.group("name"), link == null ? "message" + automaticIndex++ : link);
        }
        return Map.copyOf(messages);
    }
}
