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
            // 普通结构保留为 MIL；@unit.* 与 @io.* 是由 target profile 展开的受限宏。
            var total: Int = (1 + 2);
            total += 3;
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
    void readsAndControlsOnlyTypedDeclaredHardware(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("hardware.mplh"), """
            const LaunchSwitch: Switch = link("switch1");
            const Gun: Duo = link("duo1");
            """);
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), """
            val enabled: Bool = LaunchSwitch.enabled;
            LaunchSwitch.setEnabled(!enabled);
            Gun.shoot(12.0, 24.0, enabled);
            """);

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146"));

        assertTrue(result.succeeded());
        assertEquals("""
            sensor __mpl_tmp0 switch1 @enabled
            set mpl_enabled __mpl_tmp0
            op equal __mpl_tmp1 0 mpl_enabled
            control switch1 enabled __mpl_tmp1 0 0 0 0
            control duo1 shoot 12.0 24.0 mpl_enabled 0 0
            stop
            """, result.mlog().orElseThrow());
        assertEquals("""
            // 由 MPL 自动生成的 MIL；请通过 mpl build 重新生成，勿直接编辑。
            // 普通结构保留为 MIL；@unit.* 与 @io.* 是由 target profile 展开的受限宏。
            val enabled: Bool = @building.read(@switch1, enabled);
            @building.control(@switch1, enabled, (!enabled));
            @building.control(@duo1, shoot, 12.0, 24.0, enabled);
            """, result.mil().orElseThrow());
    }

    @Test
    void rejectsUndeclaredOrUnsupportedHardwareMembers(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("hardware.mplh"),
            "const LaunchSwitch: Switch = link(\"switch1\");");
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), "LaunchSwitch.shoot(1.0, 2.0, true);");

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146"));

        assertFalse(result.succeeded());
        assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("MPL3201")
            && diagnostic.message().contains("不支持控制方法")));
    }

    @Test
    void rejectsHardwareLinkTypesOutsideTheSelectedProfile(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("hardware.mplh"),
            "const Foreign: UnknownBlock = link(\"unknown1\");");
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), "var value: Int = 1;");

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146"));

        assertFalse(result.succeeded());
        assertTrue(result.mlog().isEmpty());
        assertEquals("MPL1201", result.diagnostics().get(0).code());
        assertEquals("compiler.hardware.type.unsupported", result.diagnostics().get(0).messageKey().orElseThrow());
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
        assertEquals(release.mil(), debug.mil());
        assertTrue(debug.mil().orElseThrow().contains("while (true) {"));
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
