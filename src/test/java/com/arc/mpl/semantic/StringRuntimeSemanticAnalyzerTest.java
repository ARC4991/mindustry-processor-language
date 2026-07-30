package com.arc.mpl.semantic;

import com.arc.mpl.ast.Program;
import com.arc.mpl.hir.HirAssignment;
import com.arc.mpl.hir.HirExpressionStatement;
import com.arc.mpl.hir.HirFunctionCall;
import com.arc.mpl.hir.HirStringComparison;
import com.arc.mpl.hir.HirStringConcat;
import com.arc.mpl.hir.HirStringLength;
import com.arc.mpl.hir.HirStringSnapshot;
import com.arc.mpl.hir.HirVariableDeclaration;
import com.arc.mpl.syntax.MplSyntaxParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StringRuntimeSemanticAnalyzerTest {
    private final MplSyntaxParser parser = new MplSyntaxParser();
    private final SemanticAnalyzer analyzer = new SemanticAnalyzer();

    @Test
    void lowersBoundedConcatenationLengthEqualityAndMutableAssignment() {
        SemanticResult result = analyze("""
            var prefix: String = "M";
            prefix = "MP";
            val text: String = prefix + "L";
            val length: Int = text.length;
            val same: Bool = text == prefix;
            """);

        assertTrue(result.program().isPresent(), () -> result.diagnostics().toString());
        var statements = result.program().orElseThrow().statements();
        HirVariableDeclaration prefix = assertInstanceOf(HirVariableDeclaration.class, statements.get(0));
        assertEquals(400, prefix.stringCapacity());
        assertInstanceOf(HirAssignment.class,
            assertInstanceOf(HirExpressionStatement.class, statements.get(1)).expression());
        HirVariableDeclaration text = assertInstanceOf(HirVariableDeclaration.class, statements.get(2));
        assertEquals(3, text.stringCapacity());
        assertEquals(3, assertInstanceOf(HirStringConcat.class, text.initializer()).maxCodeUnits());
        assertInstanceOf(HirStringLength.class,
            assertInstanceOf(HirVariableDeclaration.class, statements.get(3)).initializer());
        assertInstanceOf(HirStringComparison.class,
            assertInstanceOf(HirVariableDeclaration.class, statements.get(4)).initializer());
    }

    @Test
    void rejectsSelfGrowingStringInsideAnUnboundedLoop() {
        SemanticResult result = analyze("""
            var text: String = "";
            while (true) {
                text = text + "x";
            }
            """);

        assertTrue(result.program().isEmpty());
        assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("MPL3103")
            && diagnostic.message().contains("无法证明长度上界")));
    }

    @Test
    void snapshotsStringArgumentsInLeftToRightEvaluationOrder() {
        SemanticResult result = analyze("""
            fun select(first: String, second: String): String {
                return first;
            }

            var source: String = "A";
            val selected: String = select(source, "B");
            """);

        assertTrue(result.program().isPresent(), () -> result.diagnostics().toString());
        HirVariableDeclaration selected = assertInstanceOf(HirVariableDeclaration.class,
            result.program().orElseThrow().statements().get(1));
        HirFunctionCall call = assertInstanceOf(HirFunctionCall.class, selected.initializer());
        assertEquals(2, call.arguments().size());
        assertInstanceOf(HirStringSnapshot.class, call.arguments().get(0));
        assertInstanceOf(HirStringSnapshot.class, call.arguments().get(1));
        assertTrue(call.stringResultAllocationId() > 0);
    }

    private SemanticResult analyze(String source) {
        Path file = Path.of("main.mpl");
        Program program = parser.parse(source, file).program().orElseThrow();
        return analyzer.analyze(program, file);
    }
}
