package com.arc.mpl.semantic;

import com.arc.mpl.ast.Program;
import com.arc.mpl.hir.HirVariableDeclaration;
import com.arc.mpl.syntax.MplSyntaxParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SemanticAnalyzerTest {
    private final MplSyntaxParser parser = new MplSyntaxParser();
    private final SemanticAnalyzer analyzer = new SemanticAnalyzer();

    @Test
    void rejectsImplicitBoolToIntConversion() {
        Program program = parser.parse("var number: Int = true;", Path.of("main.mpl")).program().orElseThrow();

        SemanticResult result = analyzer.analyze(program, Path.of("main.mpl"));

        assertTrue(result.program().isEmpty());
        assertEquals("MPL3103", result.diagnostics().get(0).code());
    }

    @Test
    void rejectsReassignmentOfVal() {
        Program program = parser.parse("val enabled = true;\nenabled = false;", Path.of("main.mpl")).program().orElseThrow();

        SemanticResult result = analyzer.analyze(program, Path.of("main.mpl"));

        assertTrue(result.program().isEmpty());
        assertEquals("MPL3104", result.diagnostics().get(0).code());
    }

    @Test
    void retainsVarAndValMutabilityInStructuredHir() {
        Program program = parser.parse("var mutable: Int = 1;\nval fixed: Int = 2;", Path.of("main.mpl"))
            .program().orElseThrow();

        SemanticResult result = analyzer.analyze(program, Path.of("main.mpl"));

        assertTrue(result.program().isPresent());
        HirVariableDeclaration mutable = assertInstanceOf(HirVariableDeclaration.class,
            result.program().orElseThrow().statements().get(0));
        HirVariableDeclaration fixed = assertInstanceOf(HirVariableDeclaration.class,
            result.program().orElseThrow().statements().get(1));
        assertTrue(mutable.mutable());
        assertFalse(fixed.mutable());
    }
}
