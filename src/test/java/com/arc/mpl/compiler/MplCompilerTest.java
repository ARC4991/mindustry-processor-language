package com.arc.mpl.compiler;

import com.arc.mpl.diagnostic.Severity;
import com.arc.mpl.diagnostic.DiagnosticLanguage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MplCompilerTest {
    private final MplCompiler compiler = new MplCompiler();

    @Test
    void reportsAnUnknownTargetBeforeReadingTheProject() {
        CompilationResult result = compiler.compile(new CompilationRequest(Path.of("demo"), "v999"));

        assertFalse(result.succeeded());
        assertTrue(result.profile().isEmpty());
        assertEquals("MPL1001", result.diagnostics().get(0).code());
        assertEquals(Severity.ERROR, result.diagnostics().get(0).severity());
        assertEquals("compiler.target.unsupported", result.diagnostics().get(0).messageKey().orElseThrow());
        assertEquals("不支持的 Mindustry target profile：v999",
            result.diagnostics().get(0).render(DiagnosticLanguage.ZH_CN));
    }

    @Test
    void compilesTheImplementedSubsetForAKnownTarget(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), "var total: Int = 1 + 2;\ntotal += 3;");

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146"));

        assertTrue(result.succeeded());
        assertEquals("v146", result.profile().orElseThrow().id());
        assertTrue(result.diagnostics().isEmpty());
        assertEquals("op add __mpl_tmp0 1 2\nset mpl_total __mpl_tmp0\nop add mpl_total mpl_total 3\nstop\n",
            result.mlog().orElseThrow());
        assertEquals("""
            // 由 MPL 自动生成的 MIL；请通过 mpl build 重新生成，勿直接编辑。
            // @logic.* 由所选 target profile 展开为游戏 mlog 指令。
            @logic.op(add, __mpl_tmp0, 1, 2);
            @logic.set(mpl_total, __mpl_tmp0);
            @logic.op(add, mpl_total, mpl_total, 3);
            @logic.stop();
            """, result.mil().orElseThrow());
    }

    @Test
    void compilesMessagePrintUsingTheAutomaticFirstMessageLink(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("hardware.mplh"), "const AlertBoard: Message = link(\"message1\");");
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), "var frog: Int = 21;\nAlertBoard.print(\"frog=\", frog * 2);");

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146"));

        assertTrue(result.succeeded());
        assertEquals("set mpl_frog 21\nprint \"frog=\"\nop mul __mpl_tmp0 mpl_frog 2\nprint __mpl_tmp0\nprintflush message1\nstop\n",
            result.mlog().orElseThrow());
    }

    @Test
    void keepsCompilerTemporariesSeparateFromUserVariableNames(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), "var tmp0: Int = 7;\nvar total: Int = tmp0 + 1;");

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146"));

        assertTrue(result.succeeded());
        assertEquals("set mpl_tmp0 7\nop add __mpl_tmp0 mpl_tmp0 1\nset mpl_total __mpl_tmp0\nstop\n",
            result.mlog().orElseThrow());
    }

    @Test
    void usesReadableLabelsOnlyForAnExplicitDebugBuild(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), "while (true) { }");

        CompilationResult release = compiler.compile(new CompilationRequest(project, "v146"));
        CompilationResult debug = compiler.compile(new CompilationRequest(project, "v146", true));

        assertTrue(release.succeeded());
        assertTrue(debug.succeeded());
        assertTrue(release.mlog().orElseThrow().contains("_0:"));
        assertFalse(release.mlog().orElseThrow().contains("mpl_while_start_0"));
        assertTrue(debug.mlog().orElseThrow().contains("mpl_while_start_0:"));
        assertTrue(debug.mil().orElseThrow().contains("@logic.label(mpl_while_start_0);"));
    }

    @Test
    void lowersV146UnitSetTraversalWithFiltersAndUnitControl(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), """
            while (true) {
                for (var unit : Unit.getAllDagger().where(_.health > 0.0).where(_.alive)) {
                    unit.move(Math.cos(0.0), Math.sin(0.0));
                }
            }
            """);

        // Keep the detailed lowering golden readable; release labels are
        // exercised in usesReadableLabelsOnlyForAnExplicitDebugBuild.
        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146", true));

        assertTrue(result.succeeded());
        assertEquals("""
            mpl_while_start_0:
            jump mpl_while_end_1 equal 1 0
            ubind @dagger
            jump mpl_unit_end_4 strictEqual @unit null
            set __mpl_unit_sentinel0 @unit
            mpl_unit_scan_2:
            sensor __mpl_tmp0 @unit @health
            op greaterThan __mpl_tmp1 __mpl_tmp0 0.0
            jump mpl_unit_next_3 equal __mpl_tmp1 0
            sensor __mpl_tmp2 @unit @dead
            op equal __mpl_tmp3 __mpl_tmp2 0
            jump mpl_unit_next_3 equal __mpl_tmp3 0
            op cos __mpl_tmp4 0.0 0
            op sin __mpl_tmp5 0.0 0
            ucontrol move __mpl_tmp4 __mpl_tmp5 0 0 0
            mpl_unit_next_3:
            ubind __mpl_unit_sentinel0
            jump mpl_unit_end_4 strictEqual @unit null
            sensor __mpl_tmp6 @unit @dead
            jump mpl_unit_end_4 equal __mpl_tmp6 1
            ubind @dagger
            jump mpl_unit_end_4 strictEqual @unit null
            jump mpl_unit_end_4 strictEqual @unit __mpl_unit_sentinel0
            jump mpl_unit_scan_2 always 0 0
            mpl_unit_end_4:
            jump mpl_while_start_0 always 0 0
            mpl_while_end_1:
            stop
            """, result.mlog().orElseThrow());
    }

    @Test
    void lowersManagedUnitSetTakeWithoutExposingFlagToMpl(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), """
            while (true) {
                for (var unit : Unit.getAllDagger().where(_.alive).take(3)) {
                    unit.move(10.0, 20.0);
                }
            }
            """);

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146"));

        assertTrue(result.succeeded());
        String mlog = result.mlog().orElseThrow();
        assertTrue(mlog.contains("sensor __mpl_tmp5 @unit @flag"));
        assertTrue(mlog.contains("@unit @controlled"));
        assertTrue(mlog.contains("ucontrol flag __mpl_managed_owner0 0 0 0 0"));
        assertTrue(mlog.contains("ucontrol move 10.0 20.0 0 0 0"));
    }

    @Test
    void rejectsGeneratedMlogThatExceedsTheTargetInstructionLimit(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        StringBuilder source = new StringBuilder();
        for (int index = 0; index < 1_000; index++) {
            source.append("var value").append(index).append(": Int = 0;\n");
        }
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), source);

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146"));

        assertFalse(result.succeeded());
        assertTrue(result.mlog().isEmpty());
        assertTrue(result.mil().isEmpty());
        assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> "MPL5001".equals(diagnostic.code())));
    }
}
