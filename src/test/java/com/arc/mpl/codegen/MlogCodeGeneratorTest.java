package com.arc.mpl.codegen;

import com.arc.mpl.hir.CollectionType;
import com.arc.mpl.hir.HirArrayLiteral;
import com.arc.mpl.hir.HirConstant;
import com.arc.mpl.hir.HirDynamicCollectionSet;
import com.arc.mpl.hir.HirDynamicIndexAccess;
import com.arc.mpl.hir.HirBinary;
import com.arc.mpl.hir.HirFunction;
import com.arc.mpl.hir.HirFunctionParameter;
import com.arc.mpl.hir.HirIntrinsicCall;
import com.arc.mpl.hir.HirMemberAccess;
import com.arc.mpl.hir.HirProgram;
import com.arc.mpl.hir.HirReturn;
import com.arc.mpl.hir.HirUnitControl;
import com.arc.mpl.hir.HirUnitIteration;
import com.arc.mpl.hir.HirVariable;
import com.arc.mpl.hir.HirVariableDeclaration;
import com.arc.mpl.hir.HirWhile;
import com.arc.mpl.hir.ValueType;
import com.arc.mpl.memory.PhysicalMemoryLayout;
import com.arc.mpl.project.RuntimePreferences;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MlogCodeGeneratorTest {
    @Test
    void emitsATerminatingV146UnitCarouselAndUsesCompactReleaseLabels() {
        HirVariable unit = new HirVariable("unit", ValueType.UNIT);
        HirUnitIteration iteration = new HirUnitIteration(
            "unit",
            "Dagger",
            "dagger",
            List.of(new HirMemberAccess(unit, "alive", ValueType.BOOL)),
            List.of(new HirUnitControl("unit", "move", List.of(
                new HirConstant("12", ValueType.INT),
                new HirConstant("34", ValueType.INT)))));

        String mlog = new MlogCodeGenerator().generate(new HirProgram(List.of(iteration)));

        assertEquals("""
            ubind @dagger
            jump _2 strictEqual @unit null
            set __mpl_unit_sentinel0 @unit
            _0:
            sensor __mpl_tmp0 @unit @dead
            op equal __mpl_tmp1 __mpl_tmp0 0
            jump _1 equal __mpl_tmp1 0
            ucontrol move 12 34 0 0 0
            _1:
            ubind __mpl_unit_sentinel0
            jump _2 strictEqual @unit null
            sensor __mpl_tmp2 @unit @dead
            jump _2 equal __mpl_tmp2 1
            ubind @dagger
            jump _2 strictEqual @unit null
            jump _2 strictEqual @unit __mpl_unit_sentinel0
            jump _0 always 0 0
            _2:
            stop
            """, mlog);
    }

    @Test
    void retainsDescriptiveRolesWhenDebugLabelsAreRequested() {
        HirWhile loop = new HirWhile(new HirConstant("1", ValueType.BOOL), List.of());

        String mlog = new MlogCodeGenerator(MlogLabelStyle.DEBUG).generate(new HirProgram(List.of(loop)));

        assertEquals("""
            mpl_while_start_0:
            jump mpl_while_end_1 equal 1 0
            jump mpl_while_start_0 always 0 0
            mpl_while_end_1:
            stop
            """, mlog);
    }

    @Test
    void lowersClockUnitsWithoutTreatingSecondsAsMilliseconds() {
        HirProgram program = new HirProgram(List.of(
            clockVariable("timeMs", "timeMs", ValueType.FLOAT),
            clockVariable("time", "time", ValueType.FLOAT),
            clockVariable("timeMinutes", "timeMinutes", ValueType.FLOAT),
            clockVariable("timeHours", "timeHours", ValueType.FLOAT),
            clockVariable("tick", "tick", ValueType.FLOAT)));

        String mlog = new MlogCodeGenerator().generate(program);

        assertEquals("""
            set mpl_timeMs @time
            op div __mpl_tmp0 @time 1000.0
            set mpl_time __mpl_tmp0
            op div __mpl_tmp1 @time 60000.0
            set mpl_timeMinutes __mpl_tmp1
            op div __mpl_tmp2 @time 3600000.0
            set mpl_timeHours __mpl_tmp2
            set mpl_tick @tick
            stop
            """, mlog);
    }

    @Test
    void emitsPrivateStableOwnershipForManagedUnitSets() {
        HirVariable unit = new HirVariable("unit", ValueType.UNIT);
        HirUnitIteration iteration = new HirUnitIteration(
            "unit",
            "Dagger",
            "dagger",
            List.of(new HirMemberAccess(unit, "alive", ValueType.BOOL)),
            3,
            List.of(new HirUnitControl("unit", "move", List.of(
                new HirConstant("12", ValueType.INT),
                new HirConstant("34", ValueType.INT)))));

        String mlog = new MlogCodeGenerator().generate(new HirProgram(List.of(iteration)));

        assertTrue(mlog.contains("op mul __mpl_tmp0 @thisx 2"));
        assertTrue(mlog.contains("sensor __mpl_tmp5 @unit @flag"));
        assertTrue(mlog.contains("sensor __mpl_tmp6 @unit @controller"));
        assertTrue(mlog.contains("@unit @controlled"));
        assertTrue(mlog.contains("ucontrol flag __mpl_managed_owner0 0 0 0 0"));
        assertTrue(mlog.contains("ucontrol flag 0 0 0 0 0"));
        assertTrue(mlog.contains("ucontrol move 12 34 0 0 0"));
        assertTrue(mlog.indexOf("ucontrol flag __mpl_managed_owner0 0 0 0 0")
            < mlog.indexOf("ucontrol move 12 34 0 0 0"));
    }

    @Test
    void lowersRuntimeIndexedArraysToThePlannedPhysicalMemory() {
        CollectionType type = new CollectionType(CollectionType.Kind.ARRAY, ValueType.INT);
        HirVariable values = new HirVariable("values", type);
        HirVariable index = new HirVariable("index", ValueType.INT);
        HirProgram program = new HirProgram(List.of(
            new HirVariableDeclaration("values", type, true, new HirArrayLiteral(List.of(
                new HirConstant("1", ValueType.INT), new HirConstant("2", ValueType.INT),
                new HirConstant("3", ValueType.INT)), type)),
            new HirVariableDeclaration("index", ValueType.INT, true, new HirConstant("1", ValueType.INT)),
            new HirVariableDeclaration("current", ValueType.INT, true,
                new HirDynamicIndexAccess(values, index, ValueType.INT)),
            new HirDynamicCollectionSet("values", index, new HirConstant("7", ValueType.INT))
        ));
        PhysicalMemoryLayout.StorageKey key = new PhysicalMemoryLayout.StorageKey(null, "values");
        PhysicalMemoryLayout layout = new PhysicalMemoryLayout(
            List.of(new PhysicalMemoryLayout.Segment("__mpl_mem0", RuntimePreferences.MemoryKind.BANK, 512, 3)),
            Map.of(key, new PhysicalMemoryLayout.Allocation(key, 3,
                List.of(new PhysicalMemoryLayout.Slice(0, 0, 0, 3)))), 3);

        String mlog = new MlogCodeGenerator(MlogLabelStyle.RELEASE, layout).generate(program);

        assertEquals("""
            write 1 __mpl_mem0 0
            write 2 __mpl_mem0 1
            write 3 __mpl_mem0 2
            set mpl_index 1
            read __mpl_tmp0 __mpl_mem0 mpl_index
            set mpl_current __mpl_tmp0
            set __mpl_tmp1 mpl_index
            write 7 __mpl_mem0 __mpl_tmp1
            stop
            """, mlog);
    }

    @Test
    void dispatchesDynamicIndexesAcrossPhysicalMemorySlices() {
        CollectionType type = new CollectionType(CollectionType.Kind.ARRAY, ValueType.INT);
        PhysicalMemoryLayout.StorageKey key = new PhysicalMemoryLayout.StorageKey(null, "values");
        PhysicalMemoryLayout layout = new PhysicalMemoryLayout(List.of(
            new PhysicalMemoryLayout.Segment("__mpl_mem0", RuntimePreferences.MemoryKind.CELL, 64, 64),
            new PhysicalMemoryLayout.Segment("__mpl_mem1", RuntimePreferences.MemoryKind.CELL, 64, 16)
        ), Map.of(key, new PhysicalMemoryLayout.Allocation(key, 40, List.of(
            new PhysicalMemoryLayout.Slice(0, 40, 0, 24),
            new PhysicalMemoryLayout.Slice(1, 0, 24, 16)
        ))), 40);
        HirVariable index = new HirVariable("index", ValueType.INT);
        HirProgram program = new HirProgram(List.of(
            new HirVariableDeclaration("index", ValueType.INT, true, new HirConstant("25", ValueType.INT)),
            new HirVariableDeclaration("current", ValueType.INT, true, new HirDynamicIndexAccess(
                new HirVariable("values", type), index, ValueType.INT))
        ));

        String mlog = new MlogCodeGenerator(MlogLabelStyle.DEBUG, layout).generate(program);

        assertTrue(mlog.contains("jump mpl_memory_read_next_1 greaterThanEq mpl_index 24"));
        assertTrue(mlog.contains("op add __mpl_tmp1 mpl_index 40\nread __mpl_tmp0 __mpl_mem0 __mpl_tmp1"));
        assertTrue(mlog.contains("op add __mpl_tmp2 mpl_index -24\nread __mpl_tmp0 __mpl_mem1 __mpl_tmp2"));
        assertTrue(mlog.contains("mpl_memory_read_end_0:\nset mpl_current __mpl_tmp0"));
    }

    @Test
    void recordsExactPerFunctionTargetEmissionCosts() {
        HirFunction identity = new HirFunction("identity", List.of(
            new HirFunctionParameter("value", ValueType.INT)), ValueType.INT,
            List.of(new HirReturn(Optional.of(new HirVariable("value", ValueType.INT)))));
        HirFunction add = new HirFunction("add", List.of(
            new HirFunctionParameter("left", ValueType.INT),
            new HirFunctionParameter("right", ValueType.INT)), ValueType.INT,
            List.of(new HirReturn(Optional.of(new HirBinary(new HirVariable("left", ValueType.INT), "+",
                new HirVariable("right", ValueType.INT), ValueType.INT)))));

        MlogGenerationResult result = new MlogCodeGenerator().generateWithMetrics(
            new HirProgram(List.of(identity, add), List.of()));

        assertEquals(new MlogGenerationResult.FunctionMetrics(2, 1), result.functions().get("identity"));
        assertEquals(new MlogGenerationResult.FunctionMetrics(5, 1), result.functions().get("add"));
        assertEquals(result.mlog(), new MlogCodeGenerator().generate(new HirProgram(List.of(identity, add), List.of())));
    }

    private HirVariableDeclaration clockVariable(String variable, String member, ValueType type) {
        return new HirVariableDeclaration(variable, type,
            new HirIntrinsicCall("Clock", member, List.of(), type));
    }
}
