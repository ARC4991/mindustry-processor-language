package com.arc.mpl.semantic;

import com.arc.mpl.ast.Program;
import com.arc.mpl.hir.HirIntrinsicCall;
import com.arc.mpl.hir.HirVariableDeclaration;
import com.arc.mpl.hir.ValueType;
import com.arc.mpl.syntax.MplSyntaxParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClockIntrinsicSemanticAnalyzerTest {
    private final MplSyntaxParser parser = new MplSyntaxParser();
    private final SemanticAnalyzer analyzer = new SemanticAnalyzer();

    @Test
    void exposesExplicitMillisecondTimeAndFloatDerivedTimes() {
        Program program = parser.parse("""
            val milliseconds: Float = Clock.timeMs;
            val seconds: Float = Clock.time;
            val minutes: Float = Clock.timeMinutes;
            val hours: Float = Clock.timeHours;
            val tick: Float = Clock.tick;
            """, Path.of("main.mpl")).program().orElseThrow();

        SemanticResult result = analyzer.analyze(program, Path.of("main.mpl"));

        assertTrue(result.program().isPresent());
        List<HirVariableDeclaration> declarations = result.program().orElseThrow().statements().stream()
            .map(statement -> assertInstanceOf(HirVariableDeclaration.class, statement))
            .toList();
        assertIntrinsic(declarations.get(0), "timeMs", ValueType.FLOAT);
        assertIntrinsic(declarations.get(1), "time", ValueType.FLOAT);
        assertIntrinsic(declarations.get(2), "timeMinutes", ValueType.FLOAT);
        assertIntrinsic(declarations.get(3), "timeHours", ValueType.FLOAT);
        assertIntrinsic(declarations.get(4), "tick", ValueType.FLOAT);
    }

    @Test
    void rejectsUnknownClockMembersWithTheFullSupportedApiInTheDiagnostic() {
        Program program = parser.parse("val value: Float = Clock.frame;", Path.of("main.mpl"))
            .program().orElseThrow();

        SemanticResult result = analyzer.analyze(program, Path.of("main.mpl"));

        assertTrue(result.program().isEmpty());
        assertEquals("MPL3201", result.diagnostics().get(0).code());
        assertTrue(result.diagnostics().get(0).message().contains("timeMs"));
    }

    private void assertIntrinsic(HirVariableDeclaration declaration, String name, ValueType type) {
        HirIntrinsicCall call = assertInstanceOf(HirIntrinsicCall.class, declaration.initializer());
        assertEquals("Clock", call.namespace());
        assertEquals(name, call.name());
        assertEquals(type, call.type());
    }
}
