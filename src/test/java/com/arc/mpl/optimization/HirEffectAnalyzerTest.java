package com.arc.mpl.optimization;

import com.arc.mpl.hir.HirAssignment;
import com.arc.mpl.hir.HirBinary;
import com.arc.mpl.hir.HirExpressionStatement;
import com.arc.mpl.hir.HirFunction;
import com.arc.mpl.hir.HirFunctionCall;
import com.arc.mpl.hir.HirHardwareLink;
import com.arc.mpl.hir.HirIntrinsicCall;
import com.arc.mpl.hir.HirNewObject;
import com.arc.mpl.hir.HirObjectRelease;
import com.arc.mpl.hir.HirProgram;
import com.arc.mpl.hir.HirReturn;
import com.arc.mpl.hir.HirVariable;
import com.arc.mpl.hir.HirVariableDeclaration;
import com.arc.mpl.hir.ObjectType;
import com.arc.mpl.hir.ValueType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HirEffectAnalyzerTest {
    @Test
    void acceptsOnlyScalarPureFunctionsAsWorkerHelpers() {
        HirFunction add = new HirFunction("add", List.of(
            new com.arc.mpl.hir.HirFunctionParameter("left", ValueType.INT),
            new com.arc.mpl.hir.HirFunctionParameter("right", ValueType.INT)), ValueType.INT,
            List.of(new HirReturn(Optional.of(new HirBinary(new HirVariable("left", ValueType.INT), "+",
                new HirVariable("right", ValueType.INT), ValueType.INT)))));
        HirFunction caller = new HirFunction("caller", List.of(
            new com.arc.mpl.hir.HirFunctionParameter("value", ValueType.INT)), ValueType.INT,
            List.of(new HirReturn(Optional.of(new HirFunctionCall("add", List.of(
                new HirVariable("value", ValueType.INT), new com.arc.mpl.hir.HirConstant("2", ValueType.INT)),
                ValueType.INT)))));

        HirEffectAnalyzer.Analysis analysis = new HirEffectAnalyzer().analyze(new HirProgram(List.of(add, caller), List.of()));

        assertTrue(analysis.function("add").pureNumeric());
        assertTrue(analysis.function("caller").pureNumeric());
        assertEquals(List.of("add", "caller"), analysis.pureNumericFunctions().stream()
            .map(HirEffectAnalyzer.FunctionEffect::function).sorted().toList());
    }

    @Test
    void rejectsCapturedStateClockHardwareAllocationAndNonScalarAbi() {
        HirFunction captured = new HirFunction("captured", List.of(), ValueType.INT,
            List.of(new HirReturn(Optional.of(new HirVariable("global", ValueType.INT)))));
        HirFunction clock = new HirFunction("clock", List.of(), ValueType.FLOAT,
            List.of(new HirReturn(Optional.of(new HirIntrinsicCall("Clock", "time", List.of(), ValueType.FLOAT)))));
        HirFunction hardware = new HirFunction("hardware", List.of(), ValueType.INT,
            List.of(new HirReturn(Optional.of(new HirHardwareLink("Status", "message1", "Message")))));
        ObjectType object = new ObjectType("Counter", false);
        HirFunction allocation = new HirFunction("allocation", List.of(), object,
            List.of(new HirReturn(Optional.of(new HirNewObject(1, "Counter", "Counter_init", List.of(), object)))));
        HirFunction aggregate = new HirFunction("aggregate", List.of(), ValueType.INT,
            List.of(new HirExpressionStatement(new HirAssignment("state", "=",
                new com.arc.mpl.hir.HirConstant("1", ValueType.INT), ValueType.INT)),
                new HirReturn(Optional.of(new com.arc.mpl.hir.HirConstant("0", ValueType.INT)))));

        HirEffectAnalyzer.Analysis analysis = new HirEffectAnalyzer().analyze(new HirProgram(
            List.of(captured, clock, hardware, allocation, aggregate), List.of()));

        assertFalse(analysis.function("captured").pureNumeric());
        assertTrue(analysis.function("captured").effects().contains(HirEffectAnalyzer.EffectKind.READS_STATE));
        assertTrue(analysis.function("clock").effects().contains(HirEffectAnalyzer.EffectKind.READS_STATE));
        assertTrue(analysis.function("hardware").effects().contains(HirEffectAnalyzer.EffectKind.HARDWARE_IO));
        assertTrue(analysis.function("allocation").effects().contains(HirEffectAnalyzer.EffectKind.ALLOCATION));
        assertTrue(analysis.function("aggregate").effects().contains(HirEffectAnalyzer.EffectKind.WRITES_STATE));
    }

    @Test
    void marksAllMembersOfARecursiveCycleAndRejectsNonScalarParameters() {
        HirFunction first = new HirFunction("first", List.of(), ValueType.INT,
            List.of(new HirReturn(Optional.of(new HirFunctionCall("second", List.of(), ValueType.INT)))));
        HirFunction second = new HirFunction("second", List.of(), ValueType.INT,
            List.of(new HirReturn(Optional.of(new HirFunctionCall("first", List.of(), ValueType.INT)))));
        HirFunction tuple = new HirFunction("tuple", List.of(),
            new com.arc.mpl.hir.TupleType(List.of(ValueType.INT, ValueType.INT)),
            List.of(new HirReturn(Optional.of(new com.arc.mpl.hir.HirTupleLiteral(List.of(
                new com.arc.mpl.hir.HirConstant("1", ValueType.INT), new com.arc.mpl.hir.HirConstant("2", ValueType.INT)),
                new com.arc.mpl.hir.TupleType(List.of(ValueType.INT, ValueType.INT)))))));

        HirEffectAnalyzer.Analysis analysis = new HirEffectAnalyzer().analyze(new HirProgram(
            List.of(first, second, tuple), List.of()));

        assertTrue(analysis.function("first").effects().contains(HirEffectAnalyzer.EffectKind.UNKNOWN_CALL));
        assertTrue(analysis.function("second").effects().contains(HirEffectAnalyzer.EffectKind.UNKNOWN_CALL));
        assertFalse(analysis.function("tuple").pureNumeric());
        assertTrue(analysis.function("tuple").effects().contains(HirEffectAnalyzer.EffectKind.NON_SCALAR_ABI));
    }
}
