package com.arc.mpl.codegen;

import com.arc.mpl.hir.HirConstant;
import com.arc.mpl.hir.HirMemberAccess;
import com.arc.mpl.hir.HirProgram;
import com.arc.mpl.hir.HirUnitControl;
import com.arc.mpl.hir.HirUnitIteration;
import com.arc.mpl.hir.HirVariable;
import com.arc.mpl.hir.ValueType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MlogCodeGeneratorTest {
    @Test
    void emitsATerminatingV146UnitCarouselAndInvertsAlive() {
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
            jump mpl_unit_end_2 strictEqual @unit null
            set __mpl_unit_sentinel0 @unit
            mpl_unit_scan_0:
            sensor __mpl_tmp0 @unit @dead
            op equal __mpl_tmp1 __mpl_tmp0 0
            jump mpl_unit_next_1 equal __mpl_tmp1 0
            ucontrol move 12 34 0 0 0
            mpl_unit_next_1:
            ubind __mpl_unit_sentinel0
            jump mpl_unit_end_2 strictEqual @unit null
            sensor __mpl_tmp2 @unit @dead
            jump mpl_unit_end_2 equal __mpl_tmp2 1
            ubind @dagger
            jump mpl_unit_end_2 strictEqual @unit null
            jump mpl_unit_end_2 strictEqual @unit __mpl_unit_sentinel0
            jump mpl_unit_scan_0 always 0 0
            mpl_unit_end_2:
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
}
