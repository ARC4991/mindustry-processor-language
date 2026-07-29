package com.arc.mpl.project;

import com.arc.mpl.profile.KnownProfiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/** Creates a small, immediately buildable MPL project without overwriting user files. */
public final class ProjectInitializer {
    public void initialize(Path projectDirectory, String target) throws IOException {
        if (KnownProfiles.find(target).isEmpty()) {
            throw new IllegalArgumentException("不支持的 Mindustry target profile：" + target);
        }
        if (Files.exists(projectDirectory)) {
            try (Stream<Path> entries = Files.list(projectDirectory)) {
                if (entries.findAny().isPresent()) {
                    throw new IllegalArgumentException("目标目录不是空目录：" + projectDirectory);
                }
            }
        }
        Files.createDirectories(projectDirectory.resolve("src"));
        String name = projectName(projectDirectory);
        Files.writeString(projectDirectory.resolve("mpl.json"), """
            {
              "schemaVersion": 1,
              "name": "%s",
              "version": "0.1.0",
              "target": {
                "mindustry": "%s"
              },
              "entry": "src/main.mpl",
              "hardware": "src/hardware.mplh",
              "dependencies": {}
            }
            """.formatted(name, target));
        Files.writeString(projectDirectory.resolve("src/hardware.mplh"), """
            // Message Block 在游戏中的链接变量名由处理器提供。
            const AlertBoard: Message = link("message1");
            """);
        Files.writeString(projectDirectory.resolve("src/main.mpl"), """
            // main.mpl 的顶层代码会由处理器顺序执行。
            var answer: Int = 21 * 2;
            AlertBoard.print("MPL 项目初始化成功，答案：", answer);
            """);
    }

    private String projectName(Path directory) {
        Path fileName = directory.getFileName();
        String raw = fileName == null ? "mpl-project" : fileName.toString();
        String sanitized = raw.replaceAll("[^A-Za-z0-9._-]", "-");
        return sanitized.isBlank() ? "mpl-project" : sanitized;
    }
}
