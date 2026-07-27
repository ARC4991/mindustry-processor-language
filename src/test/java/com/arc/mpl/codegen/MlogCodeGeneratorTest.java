package com.arc.mpl.codegen;

import com.arc.mpl.hir.HirConstant;
import com.arc.mpl.hir.HirIntrinsicCall;
import com.arc.mpl.hir.HirMemberAccess;
import com.arc.mpl.hir.HirProgram;
import com.arc.mpl.hir.HirUnitControl;
import com.arc.mpl.hir.HirUnitIteration;
import com.arc.mpl.hir.HirVariable;
import com.arc.mpl.hir.HirVariableDeclaration;
import com.arc.mpl.hir.HirWhile;
import com.arc.mpl.hir.ValueType;
import org.junit.jupiter.api.Test;

import java.util.List;

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

    private HirVariableDeclaration clockVariable(String variable, String member, ValueType type) {
        return new HirVariableDeclaration(variable, type,
            new HirIntrinsicCall("Clock", member, List.of(), type));
    }
}
