package com.arc.mpl.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MplCliTest {
    @Test
    void buildWritesMilBesideMlogAndAppliesTheDebugLabelStyle(@TempDir Path project) throws IOException {
        Path sourceDirectory = Files.createDirectories(project.resolve("src"));
        Files.writeString(sourceDirectory.resolve("main.mpl"), "while (true) { }");
        Path outputDirectory = Files.createDirectories(project.resolve("artifacts"));
        Path mlog = outputDirectory.resolve("program.mlog");
        Path mil = outputDirectory.resolve("program.mil");

        MplCli.main(new String[]{
            "build", "--debug", "--lang=zh-CN", "--target=v146", project.toString(), mlog.toString()
        });

        assertEquals("""
            mpl_while_start_0:
            jump mpl_while_end_1 equal 1 0
            jump mpl_while_start_0 always 0 0
            mpl_while_end_1:
            stop
            """, Files.readString(mlog));
        assertEquals("""
            // 由 MPL 自动生成的 MIL；请通过 mpl build 重新生成，勿直接编辑。
            // @logic.* 由所选 target profile 展开为游戏 mlog 指令。
            @logic.label(mpl_while_start_0);
            @logic.jump(mpl_while_end_1, equal, 1, 0);
            @logic.jump(mpl_while_start_0, always, 0, 0);
            @logic.label(mpl_while_end_1);
            @logic.stop();
            """, Files.readString(mil));
        assertTrue(Files.isRegularFile(mlog));
        assertTrue(Files.isRegularFile(mil));
    }
}
