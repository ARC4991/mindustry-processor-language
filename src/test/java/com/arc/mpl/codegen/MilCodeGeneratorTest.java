package com.arc.mpl.codegen;

import com.arc.mpl.hir.HirAssignment;
import com.arc.mpl.hir.HirBinary;
import com.arc.mpl.hir.HirConstant;
import com.arc.mpl.hir.HirExpressionStatement;
import com.arc.mpl.hir.HirDynamicCollectionSet;
import com.arc.mpl.hir.HirDynamicIndexAccess;
import com.arc.mpl.hir.HirIntrinsicCall;
import com.arc.mpl.hir.HirMemberAccess;
import com.arc.mpl.hir.HirPrintStatement;
import com.arc.mpl.hir.HirProgram;
import com.arc.mpl.hir.HirText;
import com.arc.mpl.hir.HirUnitControl;
import com.arc.mpl.hir.HirUnitIteration;
import com.arc.mpl.hir.HirVariable;
import com.arc.mpl.hir.HirVariableDeclaration;
import com.arc.mpl.hir.HirWhile;
import com.arc.mpl.hir.CollectionType;
import com.arc.mpl.hir.ValueType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MilCodeGeneratorTest {
    private final MilCodeGenerator generator = new MilCodeGenerator();

    @Test
    void retainsOrdinaryMplStructureAndLowersOnlyTargetBoundOperations() {
        HirVariable total = new HirVariable("total", ValueType.INT);
        HirVariable phase = new HirVariable("phase", ValueType.FLOAT);
        HirVariable unit = new HirVariable("unit", ValueType.UNIT);
        HirUnitIteration iteration = new HirUnitIteration(
            "unit",
            "Dagger",
            "dagger",
            List.of(
                new HirBinary(new HirMemberAccess(unit, "health", ValueType.FLOAT), ">",
                    new HirConstant("0.0", ValueType.FLOAT), ValueType.BOOL),
                new HirMemberAccess(unit, "alive", ValueType.BOOL)),
            List.of(new HirUnitControl("unit", "move", List.of(
                new HirIntrinsicCall("Math", "cos", List.of(phase), ValueType.FLOAT),
                new HirIntrinsicCall("Math", "sin", List.of(phase), ValueType.FLOAT)))));

        String mil = generator.generate(new HirProgram(List.of(
            new HirVariableDeclaration("total", ValueType.INT, true,
                new HirBinary(new HirConstant("1", ValueType.INT), "+",
                    new HirConstant("2", ValueType.INT), ValueType.INT)),
            new HirVariableDeclaration("phase", ValueType.FLOAT, false,
                new HirIntrinsicCall("Clock", "time", List.of(), ValueType.FLOAT)),
            new HirWhile(new HirConstant("1", ValueType.BOOL), List.of(
                new HirExpressionStatement(new HirAssignment("total", "+=", new HirConstant("1", ValueType.INT), ValueType.INT)),
                iteration)),
            new HirPrintStatement("message1", List.of(new HirText("total="), total))
        )));

        assertEquals("""
            // 由 MPL 自动生成的 MIL；请通过 mpl build 重新生成，勿直接编辑。
            // 普通结构保留为 MIL；@unit.* 与 @io.* 是由 target profile 展开的受限宏。
            var total: Int = (1 + 2);
            val phase: Float = Clock.time;
            while (true) {
                total += 1;
                @unit.each(@dagger, unit, (@unit.read(unit, health) > 0.0), @unit.alive(unit)) {
                    @unit.move(unit, Math.cos(phase), Math.sin(phase));
                }
            }
            @io.print(@message1, "total=", total);
            """, mil);
        assertFalse(mil.contains("@logic."));
        assertFalse(mil.contains("ubind"));
        assertFalse(mil.contains("ucontrol"));
    }

    @Test
    void makesThePrivateManagedUnitRuntimeARestrictedMacroBoundary() {
        HirVariable unit = new HirVariable("unit", ValueType.UNIT);
        HirUnitIteration iteration = new HirUnitIteration(
            "unit",
            "Alpha",
            "alpha",
            List.of(new HirMemberAccess(unit, "alive", ValueType.BOOL)),
            3,
            List.of(new HirUnitControl("unit", "move", List.of(
                new HirConstant("12.0", ValueType.FLOAT),
                new HirConstant("34.0", ValueType.FLOAT)))));

        String mil = generator.generate(new HirProgram(List.of(iteration)));

        assertEquals("""
            // 由 MPL 自动生成的 MIL；请通过 mpl build 重新生成，勿直接编辑。
            // 普通结构保留为 MIL；@unit.* 与 @io.* 是由 target profile 展开的受限宏。
            @unit.eachManaged(@alpha, unit, 3, @unit.alive(unit)) {
                @unit.move(unit, 12.0, 34.0);
            }
            """, mil);
        assertFalse(mil.contains("flag"));
        assertFalse(mil.contains("@controller"));
        assertTrue(mil.contains("@unit.eachManaged"));
    }

    @Test
    void refusesToSerializeTheRuntimeOwnedFlagEvenForManuallyBuiltHir() {
        HirVariable unit = new HirVariable("unit", ValueType.UNIT);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> generator.generate(new HirProgram(List.of(new HirExpressionStatement(
                new HirMemberAccess(unit, "flag", ValueType.FLOAT))))));

        assertEquals("Unit.flag 是编译器私有运行时属性，不能出现在 MIL", exception.getMessage());
    }

    @Test
    void preservesDynamicArraySyntaxForTheMilMemoryLoweringStage() {
        CollectionType arrayType = new CollectionType(CollectionType.Kind.ARRAY, ValueType.INT);
        HirVariable values = new HirVariable("values", arrayType);
        HirVariable index = new HirVariable("index", ValueType.INT);

        String mil = generator.generate(new HirProgram(List.of(
            new HirVariableDeclaration("current", ValueType.INT, true,
                new HirDynamicIndexAccess(values, index, ValueType.INT)),
            new HirDynamicCollectionSet("values", index, new HirConstant("7", ValueType.INT))
        )));

        assertTrue(mil.contains("var current: Int = values[index];"));
        assertTrue(mil.contains("values.set(index, 7);"));
        assertFalse(mil.contains("read "));
        assertFalse(mil.contains("write "));
    }
}
