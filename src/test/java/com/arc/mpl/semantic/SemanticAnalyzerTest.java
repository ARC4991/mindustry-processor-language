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

    @Test
    void rejectsBreakAndContinueOutsideLoops() {
        Program program = parser.parse("break;\ncontinue;", Path.of("main.mpl")).program().orElseThrow();

        SemanticResult result = analyzer.analyze(program, Path.of("main.mpl"));

        assertTrue(result.program().isEmpty());
        assertEquals(java.util.List.of("MPL3401", "MPL3402"),
            result.diagnostics().stream().map(diagnostic -> diagnostic.code()).toList());
    }

    @Test
    void keepsCountingForInitializerInsideTheLoopScope() {
        Program program = parser.parse("for (var i: Int = 0; i < 2; i += 1) { }\nvar leaked: Int = i;",
            Path.of("main.mpl")).program().orElseThrow();

        SemanticResult result = analyzer.analyze(program, Path.of("main.mpl"));

        assertTrue(result.program().isEmpty());
        assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("MPL3102")
            && diagnostic.message().contains("i")));
    }

    @Test
    void rejectsRecursiveCallsAndMissingReturnPaths() {
        Program program = parser.parse("""
            fun first(value: Int): Int {
                return second(value);
            }
            fun second(value: Int): Int {
                if (value > 0) {
                    return first(value - 1);
                }
            }
            """, Path.of("main.mpl")).program().orElseThrow();

        SemanticResult result = analyzer.analyze(program, Path.of("main.mpl"));

        assertTrue(result.program().isEmpty());
        assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("MPL3504")));
        assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("MPL3505")));
    }

    @Test
    void rejectsReturnAtTopLevel() {
        Program program = parser.parse("return 1;", Path.of("main.mpl")).program().orElseThrow();

        SemanticResult result = analyzer.analyze(program, Path.of("main.mpl"));

        assertTrue(result.program().isEmpty());
        assertEquals("MPL3502", result.diagnostics().get(0).code());
    }

    @Test
    void allowsFunctionToReadAnEarlierInitializedGlobal() {
        Program program = parser.parse("""
            var scale: Int = 2;
            fun multiply(value: Int): Int {
                return value * scale;
            }
            var result: Int = multiply(3);
            """, Path.of("main.mpl")).program().orElseThrow();

        SemanticResult result = analyzer.analyze(program, Path.of("main.mpl"));

        assertTrue(result.program().isPresent());
        assertTrue(result.diagnostics().isEmpty());
    }

    @Test
    void rejectsFunctionAccessToAGlobalDeclaredAfterIt() {
        Program program = parser.parse("""
            fun readScale(): Int {
                return scale;
            }
            var scale: Int = 2;
            """, Path.of("main.mpl")).program().orElseThrow();

        SemanticResult result = analyzer.analyze(program, Path.of("main.mpl"));

        assertTrue(result.program().isEmpty());
        assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("MPL3506")
            && diagnostic.message().contains("scale")));
    }

    @Test
    void rejectsTopLevelCallBeforeAnIndirectGlobalDependencyIsInitialized() {
        Program program = parser.parse("""
            var result: Int = outer();
            var source: Int = 7;
            fun inner(): Int {
                return source;
            }
            fun outer(): Int {
                return inner();
            }
            """, Path.of("main.mpl")).program().orElseThrow();

        SemanticResult result = analyzer.analyze(program, Path.of("main.mpl"));

        assertTrue(result.program().isEmpty());
        assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("MPL3507")
            && diagnostic.message().contains("source")));
    }

    @Test
    void allowsTopLevelCallAfterAnIndirectGlobalDependencyIsInitialized() {
        Program program = parser.parse("""
            var source: Int = 7;
            var result: Int = outer();
            fun inner(): Int {
                return source;
            }
            fun outer(): Int {
                return inner();
            }
            """, Path.of("main.mpl")).program().orElseThrow();

        SemanticResult result = analyzer.analyze(program, Path.of("main.mpl"));

        assertTrue(result.program().isPresent());
        assertTrue(result.diagnostics().isEmpty());
    }

    @Test
    void rejectsReadingAGlobalFromItsOwnFunctionCallInitializer() {
        Program program = parser.parse("""
            var source: Int = readSource();
            fun readSource(): Int {
                return source;
            }
            """, Path.of("main.mpl")).program().orElseThrow();

        SemanticResult result = analyzer.analyze(program, Path.of("main.mpl"));

        assertTrue(result.program().isEmpty());
        assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("MPL3507")
            && diagnostic.message().contains("source")));
    }

    @Test
    void rejectsUnitSetTraversalInsideAFunction() {
        Program program = parser.parse("""
            fun controlUnits() {
                for (var unit : Unit.getAllDagger()) {
                    unit.stop();
                }
            }
            """, Path.of("main.mpl")).program().orElseThrow();

        SemanticResult result = analyzer.analyze(program, Path.of("main.mpl"));

        assertTrue(result.program().isEmpty());
        assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("MPL3508")));
    }
}
