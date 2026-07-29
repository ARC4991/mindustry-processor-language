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
        assertEquals("set mpl_total 3\nop add mpl_total mpl_total 3\nstop\n",
            result.mlog().orElseThrow());
        assertEquals("""
            // 由 MPL 自动生成的 MIL；请通过 mpl build 重新生成，勿直接编辑。
            // 普通结构保留为 MIL；@unit.* 与 @io.* 是由 target profile 展开的受限宏。
            var total: Int = 3;
            total += 3;
            """, result.mil().orElseThrow());
    }

    @Test
    void optimizesConstantExpressionsAndConstantBranchesBeforeMlogLowering(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), """
            var value: Int = 1 + 2 * 3;
            if (false) {
                value = 99;
            } else {
                value += 1;
            }
            while (false) {
                value += 100;
            }
            """);

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146"));

        assertTrue(result.succeeded());
        assertEquals("set mpl_value 7\nop add mpl_value mpl_value 1\nstop\n", result.mlog().orElseThrow());
        assertEquals(2, result.optimizationReport().constantFolds());
        assertEquals(1, result.optimizationReport().eliminatedBranches());
        assertEquals(1, result.optimizationReport().eliminatedLoops());
    }

    @Test
    void lowersLogicalOperatorsWithShortCircuitControlFlow(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), """
            var enabled: Bool = false;
            var changed: Bool = false;
            enabled && (changed = true);
            enabled || (changed = true);
            """);

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146", true));

        assertTrue(result.succeeded());
        String mlog = result.mlog().orElseThrow();
        assertTrue(mlog.contains("jump mpl_short_circuit_end_0 equal mpl_enabled 0"));
        assertTrue(mlog.contains("jump mpl_short_circuit_end_1 notEqual mpl_enabled 0"));
        assertFalse(mlog.contains("op land"));
        assertFalse(mlog.contains("op or"));
        assertTrue(mlog.indexOf("jump mpl_short_circuit_end_0") < mlog.indexOf("set mpl_changed 1"));
    }

    @Test
    void compilesStructuredIfElseWithBranchLocalVariables(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), """
            var total: Int = 0;
            if (true) {
                total = 1;
            } else {
                total = 2;
            }
            """);

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146", true));

        assertTrue(result.succeeded());
        assertEquals("""
            set mpl_total 0
            set mpl_total 1
            stop
            """, result.mlog().orElseThrow());
        assertFalse(result.mil().orElseThrow().contains("if (true) {"));
        assertFalse(result.mil().orElseThrow().contains("else {"));
    }

    @Test
    void compilesDoWhileBreakAndContinueToTheCorrectLoopTargets(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), """
            var count: Int = 0;
            do {
                count += 1;
                if (count < 2) {
                    continue;
                }
                if (count > 4) {
                    break;
                }
            } while (count < 10);
            """);

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146", true));

        assertTrue(result.succeeded());
        assertEquals("""
            set mpl_count 0
            mpl_do_start_0:
            op add mpl_count mpl_count 1
            op lessThan __mpl_tmp0 mpl_count 2
            jump mpl_if_end_3 equal __mpl_tmp0 0
            jump mpl_do_condition_1 always 0 0
            mpl_if_end_3:
            op greaterThan __mpl_tmp1 mpl_count 4
            jump mpl_if_end_4 equal __mpl_tmp1 0
            jump mpl_do_end_2 always 0 0
            mpl_if_end_4:
            mpl_do_condition_1:
            op lessThan __mpl_tmp2 mpl_count 10
            jump mpl_do_start_0 notEqual __mpl_tmp2 0
            mpl_do_end_2:
            stop
            """, result.mlog().orElseThrow());
        assertTrue(result.mil().orElseThrow().contains("do {"));
        assertTrue(result.mil().orElseThrow().contains("continue;"));
        assertTrue(result.mil().orElseThrow().contains("break;"));
    }

    @Test
    void targetsUnitIterationNextAndEndForContinueAndBreak(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), """
            for (var unit : Unit.getAllDagger()) {
                if (!unit.alive) {
                    continue;
                }
                break;
            }
            """);

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146", true));

        assertTrue(result.succeeded());
        String mlog = result.mlog().orElseThrow();
        assertTrue(mlog.contains("jump mpl_unit_next_1 always 0 0"));
        assertTrue(mlog.contains("jump mpl_unit_end_2 always 0 0"));
    }

    @Test
    void compilesStaticAggregateTraversalWithContinueAndBreak(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), """
            val values: Int[] = [1, 2, 3];
            var total: Int = 0;
            for (var value : values) {
                if (value == 2) {
                    continue;
                }
                total += value;
                if (value == 3) {
                    break;
                }
            }
            """);

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146", true));

        assertTrue(result.succeeded());
        String mlog = result.mlog().orElseThrow();
        assertTrue(mlog.contains("set mpl_value mpl_values_e0"));
        assertTrue(mlog.contains("set mpl_value mpl_values_e1"));
        assertTrue(mlog.contains("set mpl_value mpl_values_e2"));
        assertTrue(mlog.contains("jump mpl_aggregate_next_1 always 0 0"));
        assertTrue(mlog.contains("jump mpl_aggregate_end_0 always 0 0"));
        assertTrue(result.mil().orElseThrow().contains("for (var value : values) {"));
    }

    @Test
    void compilesCountingForAndRunsUpdateAfterContinue(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), """
            var total: Int = 0;
            for (var i: Int = 0; i < 3; i += 1) {
                if (i == 1) {
                    continue;
                }
                total += i;
            }
            """);

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146", true));

        assertTrue(result.succeeded());
        assertEquals("""
            set mpl_total 0
            set mpl_i 0
            mpl_for_condition_0:
            op lessThan __mpl_tmp0 mpl_i 3
            jump mpl_for_end_2 equal __mpl_tmp0 0
            op equal __mpl_tmp1 mpl_i 1
            jump mpl_if_end_3 equal __mpl_tmp1 0
            jump mpl_for_update_1 always 0 0
            mpl_if_end_3:
            op add mpl_total mpl_total mpl_i
            mpl_for_update_1:
            op add mpl_i mpl_i 1
            jump mpl_for_condition_0 always 0 0
            mpl_for_end_2:
            stop
            """, result.mlog().orElseThrow());
        assertTrue(result.mil().orElseThrow().contains(
            "for (var i: Int = 0; (i < 3); i += 1) {"));
    }

    @Test
    void compilesNonRecursiveFunctionWithStaticCounterAbi(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), """
            fun add(left: Int, right: Int): Int {
                return left + right;
            }
            var result: Int = add(2, 3);
            """);

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146", true));

        assertTrue(result.succeeded());
        assertEquals("""
            set __mpl_tmp0 2
            set __mpl_tmp1 3
            set __mpl_fn0_arg0 __mpl_tmp0
            set __mpl_fn0_arg1 __mpl_tmp1
            op add __mpl_fn0_return @counter 1
            set @counter 9
            set __mpl_tmp2 __mpl_fn0_result
            set mpl_result __mpl_tmp2
            stop
            mpl_function_add_0:
            op add __mpl_tmp3 __mpl_fn0_arg0 __mpl_fn0_arg1
            set __mpl_fn0_result __mpl_tmp3
            set @counter __mpl_fn0_return
            """, result.mlog().orElseThrow());
        assertEquals("""
            // 由 MPL 自动生成的 MIL；请通过 mpl build 重新生成，勿直接编辑。
            // 普通结构保留为 MIL；@unit.* 与 @io.* 是由 target profile 展开的受限宏。
            fun add(left: Int, right: Int): Int {
                return (left + right);
            }
            var result: Int = add(2, 3);
            """, result.mil().orElseThrow());
    }

    @Test
    void compilesNestedAndImplicitVoidFunctionReturnsWithCollisionFreeSlots(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), """
            fun a(b_c: Int) {
                var doubled: Int = b_c * 2;
            }
            fun a_b(c: Int): Int {
                a(c);
                return c + 1;
            }
            var result: Int = a_b(4);
            """);

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146", true));

        assertTrue(result.succeeded());
        String mlog = result.mlog().orElseThrow();
        assertTrue(mlog.contains("set __mpl_fn1_arg0 __mpl_tmp0"));
        assertTrue(mlog.contains("set __mpl_fn0_arg0 __mpl_tmp"));
        assertTrue(mlog.contains("set __mpl_fn0_local_doubled __mpl_tmp"));
        assertTrue(mlog.contains("set @counter __mpl_fn0_return"));
        assertTrue(mlog.contains("set __mpl_fn1_result __mpl_tmp"));
        assertTrue(mlog.contains("set @counter __mpl_fn1_return"));
        assertFalse(mlog.contains("__mpl_fn_a_param_b_c"));
        assertFalse(mlog.contains("__mpl_fn_a_b_param_c"));
    }

    @Test
    void compilesStaticallyLaidOutTupleAndArrayElements(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), """
            val test : (Int,Int,Int) = (1,2,3);
            val array : Int[] = [1,2,3,4,5];
            var selected: Int = test[1] + array[3];
            var count: Int = array.size;
            """);

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146"));

        assertTrue(result.succeeded());
        assertEquals("""
            set mpl_test_e0 1
            set mpl_test_e1 2
            set mpl_test_e2 3
            set mpl_array_e0 1
            set mpl_array_e1 2
            set mpl_array_e2 3
            set mpl_array_e3 4
            set mpl_array_e4 5
            op add __mpl_tmp0 mpl_test_e1 mpl_array_e3
            set mpl_selected __mpl_tmp0
            set mpl_count 5
            stop
            """, result.mlog().orElseThrow());
        assertTrue(result.mil().orElseThrow().contains("val test: (Int, Int, Int) = (1, 2, 3);"));
        assertTrue(result.mil().orElseThrow().contains("val array: Int[] = [1, 2, 3, 4, 5];"));
    }

    @Test
    void compilesStaticListSetMembershipAndArrayElementUpdate(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), """
            var array: Int[] = [1, 2, 3];
            array.set(1, 9);
            val queue: List<Int> = listOf(2, 4, 6);
            val tags: Set<Int> = Set.of(3, 5, 7);
            var found: Bool = queue.contains(4);
            """);

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146"));

        assertTrue(result.succeeded());
        String mlog = result.mlog().orElseThrow();
        assertTrue(mlog.contains("set mpl_array_e1 9"));
        assertTrue(mlog.contains("set mpl_queue_e0 2"));
        assertTrue(mlog.contains("set mpl_tags_e2 7"));
        assertTrue(mlog.contains("op equal __mpl_tmp2 mpl_queue_e0 __mpl_tmp0"));
        assertTrue(mlog.contains("op or __mpl_tmp1 __mpl_tmp1 __mpl_tmp2"));
        assertTrue(result.mil().orElseThrow().contains("val queue: List<Int> = listOf(2, 4, 6);"));
        assertTrue(result.mil().orElseThrow().contains("val tags: Set<Int> = setOf(3, 5, 7);"));
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
    void lowersDirectDisplayDrawingWithoutLeakingRawMlog(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("hardware.mplh"),
            "const Screen: Display = link(\"display1\");");
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), """
            Screen.clear(Color.black);
            Screen.fill(Color.green);
            Screen.fillRect(1, 2, 30, 40);
            Screen.stroke(Color.white);
            Screen.strokeRect(2, 3, 20, 10);
            Screen.line(0, 0, 80, 80);
            Screen.flush();
            """);

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146"));

        assertTrue(result.succeeded());
        assertEquals("""
            draw clear 0 0 0 0 0 0
            draw color 0 255 0 255 0 0
            draw rect 1 2 30 40 0 0
            draw color 255 255 255 255 0 0
            draw lineRect 2 3 20 10 0 0
            draw line 0 0 80 80 0 0
            drawflush display1
            stop
            """, result.mlog().orElseThrow());
        assertTrue(result.mil().orElseThrow().contains("@io.draw(@display1, clear, 0, 0, 0);"));
        assertTrue(result.mil().orElseThrow().contains("@io.drawFlush(@display1);"));
    }

    @Test
    void rejectsDisplayDrawingInLoopsAndInvalidColors(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("hardware.mplh"),
            "const Screen: Display = link(\"display1\");");
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), """
            while (true) {
                Screen.fill(Color.blue);
            }
            """);

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146"));

        assertFalse(result.succeeded());
        assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("MPL3203")
            && diagnostic.message().contains("循环或函数")));
        assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("MPL3203")
            && diagnostic.message().contains("Color 不支持常量")));
    }

    @Test
    void expandsBuildingTraversalOverDeclaredLinksOnly(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("hardware.mplh"), """
            const NorthTurret: Duo = link("duo1");
            const SouthTurret: Duo = link("duo2");
            const Status: Message = link("message1");
            """);
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), """
            var health: Float = 0.0;
            for (var turret : Building.getAllDuo()) {
                health += turret.health;
                turret.shoot(turret.x, turret.y, true);
            }
            """);

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146", true));

        assertTrue(result.succeeded());
        String mlog = result.mlog().orElseThrow();
        assertTrue(mlog.contains("duo1 @health"));
        assertTrue(mlog.contains("duo2 @health"));
        assertTrue(mlog.contains("control duo1 shoot"));
        assertTrue(mlog.contains("control duo2 shoot"));
        assertTrue(result.mil().orElseThrow().contains("for (var turret : Building.getAllDuo()) {"));
    }

    @Test
    void filtersEachStaticallyLinkedBuildingBeforeExecutingTheTraversalBody(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("hardware.mplh"), """
            const NorthTurret: Duo = link("duo1");
            const SouthTurret: Duo = link("duo2");
            """);
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), """
            val minimum: Float = 10.0;
            var matched: Int = 0;
            for (var turret : Building.getAllDuo(_ => _.enabled).where(candidate => candidate.health > minimum)) {
                matched += 1;
                turret.setEnabled(false);
            }
            """);

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146", true));

        assertTrue(result.succeeded());
        String mlog = result.mlog().orElseThrow();
        assertTrue(mlog.contains("duo1 @enabled"));
        assertTrue(mlog.contains("duo1 @health"));
        assertTrue(mlog.contains("duo2 @enabled"));
        assertTrue(mlog.contains("duo2 @health"));
        assertTrue(mlog.contains("control duo1 enabled 0 0 0 0 0"));
        assertTrue(mlog.contains("control duo2 enabled 0 0 0 0 0"));
        assertTrue(mlog.indexOf("duo1 @enabled") < mlog.indexOf("control duo1 enabled"));
        assertTrue(mlog.indexOf("duo2 @enabled") < mlog.indexOf("control duo2 enabled"));
        assertTrue(result.mil().orElseThrow().contains(
            "for (var turret : Building.getAllDuo().where(turret => turret.enabled).where(turret => (turret.health > minimum))) {"));
    }

    @Test
    void rejectsImpureOrNonLambdaBuildingFilters(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("hardware.mplh"),
            "const Turret: Duo = link(\"duo1\");");
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), """
            var threshold: Float = 5.0;
            for (var turret : Building.getAllDuo().where(_ => _.health > threshold)) {
                turret.setEnabled(true);
            }
            for (var turret : Building.getAllDuo().where(true)) {
                turret.setEnabled(true);
            }
            """);

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146"));

        assertFalse(result.succeeded());
        assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("MPL3201")
            && diagnostic.message().contains("val 标量")));
        assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("MPL3201")
            && diagnostic.message().contains("必须是 lambda")));
    }

    @Test
    void removesBuildingTraversalWithAConstantFalseFilter(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("hardware.mplh"),
            "const Turret: Duo = link(\"duo1\");");
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), """
            for (var turret : Building.getAllDuo(_ => false).where(_ => true)) {
                turret.setEnabled(true);
            }
            """);

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146"));

        assertTrue(result.succeeded());
        assertEquals("stop\n", result.mlog().orElseThrow());
        assertEquals(1, result.optimizationReport().eliminatedStatements());
    }

    @Test
    void removesBuildingTraversalWhenNoMatchingLinkIsDeclared(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("hardware.mplh"),
            "const Status: Message = link(\"message1\");");
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), """
            for (var turret : Building.getAllDuo()) {
                turret.shoot(1.0, 2.0, true);
            }
            """);

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146"));

        assertTrue(result.succeeded());
        assertEquals("stop\n", result.mlog().orElseThrow());
    }

    @Test
    void compilesImmutableStaticStringsAndFoldsLiteralConcatenation(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("hardware.mplh"),
            "const AlertBoard: Message = link(\"message1\");");
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), """
            val title: String = "MPL" + " demo";
            AlertBoard.print(title, " v1");
            """);

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146"));

        assertTrue(result.succeeded());
        assertEquals("""
            set mpl_title "MPL demo"
            print mpl_title
            print " v1"
            printflush message1
            stop
            """, result.mlog().orElseThrow());
        assertTrue(result.mil().orElseThrow().contains("val title: String = \"MPL demo\";"));
    }

    @Test
    void rejectsStaticPrintTextThatExceedsTheTargetMessageLimit(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("hardware.mplh"),
            "const AlertBoard: Message = link(\"message1\");");
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"),
            "AlertBoard.print(\"" + "x".repeat(401) + "\");");

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146"));

        assertFalse(result.succeeded());
        assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("MPL3202")
            && diagnostic.message().contains("400")));
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
