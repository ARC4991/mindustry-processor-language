package com.arc.mpl.optimization;

import com.arc.mpl.hir.HirAssignment;
import com.arc.mpl.hir.HirBinary;
import com.arc.mpl.hir.HirConstant;
import com.arc.mpl.hir.HirExpressionStatement;
import com.arc.mpl.hir.HirIf;
import com.arc.mpl.hir.HirProgram;
import com.arc.mpl.hir.HirStatement;
import com.arc.mpl.hir.HirVariable;
import com.arc.mpl.hir.HirVariableDeclaration;
import com.arc.mpl.hir.HirWhile;
import com.arc.mpl.hir.ValueType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HirOptimizerTest {
    @Test
    void foldsPureScalarExpressionsAndPrunesConstantControlFlow() {
        HirProgram input = new HirProgram(List.of(
            new HirVariableDeclaration("value", ValueType.INT, true,
                new HirBinary(new HirConstant("1", ValueType.INT), "+",
                    new HirBinary(new HirConstant("2", ValueType.INT), "*", new HirConstant("3", ValueType.INT), ValueType.INT),
                    ValueType.INT)),
            new HirIf(new HirConstant("0", ValueType.BOOL),
                List.of(new HirExpressionStatement(new HirAssignment("value", "=", new HirConstant("99", ValueType.INT), ValueType.INT))),
                Optional.of(List.of(new HirExpressionStatement(new HirAssignment("value", "=", new HirConstant("7", ValueType.INT), ValueType.INT))))),
            new HirWhile(new HirConstant("0", ValueType.BOOL),
                List.of(new HirExpressionStatement(new HirAssignment("value", "=", new HirConstant("8", ValueType.INT), ValueType.INT))))));

        HirOptimizationResult result = new HirOptimizer().optimize(input);

        assertEquals(2, result.report().constantFolds());
        assertEquals(1, result.report().eliminatedBranches());
        assertEquals(1, result.report().eliminatedLoops());
        assertEquals(2, result.program().statements().size());
        HirVariableDeclaration declaration = assertInstanceOf(HirVariableDeclaration.class, result.program().statements().get(0));
        HirConstant value = assertInstanceOf(HirConstant.class, declaration.initializer());
        assertEquals("7", value.mlogLiteral());
    }

    @Test
    void foldsShortCircuitWithoutTraversingTheUnreachableRightOperand() {
        HirExpressionStatement statement = new HirExpressionStatement(new HirBinary(
            new HirConstant("0", ValueType.BOOL), "&&",
            new HirAssignment("state", "=", new HirVariable("unknown", ValueType.BOOL), ValueType.BOOL), ValueType.BOOL));

        HirOptimizationResult result = new HirOptimizer().optimize(new HirProgram(List.of(statement)));

        HirExpressionStatement optimized = assertInstanceOf(HirExpressionStatement.class, result.program().statements().get(0));
        HirConstant value = assertInstanceOf(HirConstant.class, optimized.expression());
        assertEquals("0", value.mlogLiteral());
        assertTrue(result.report().constantFolds() > 0);
    }
}
