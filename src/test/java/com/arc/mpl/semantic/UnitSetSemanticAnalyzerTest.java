package com.arc.mpl.semantic;

import com.arc.mpl.ast.Program;
import com.arc.mpl.hir.HirBinary;
import com.arc.mpl.hir.HirConstant;
import com.arc.mpl.hir.HirIntrinsicCall;
import com.arc.mpl.hir.HirMemberAccess;
import com.arc.mpl.hir.HirUnitControl;
import com.arc.mpl.hir.HirUnitIteration;
import com.arc.mpl.hir.HirVariable;
import com.arc.mpl.hir.HirVariableDeclaration;
import com.arc.mpl.hir.HirWhile;
import com.arc.mpl.syntax.MplSyntaxParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnitSetSemanticAnalyzerTest {
    private final MplSyntaxParser parser = new MplSyntaxParser();
    private final SemanticAnalyzer analyzer = new SemanticAnalyzer();

    @Test
    void lowersTheUnitSetVerticalSliceToStructuredHir() {
        Program program = parser.parse("""
            val phase: Float = Clock.time;
            while (true) {
                for (var unit : Unit.getAllDagger()
                    .where(_.health > 0.0)
                    .where(_.dead == false)) {
                    unit.move(Math.cos(phase), Math.sin(phase));
                }
            }
            """, Path.of("main.mpl")).program().orElseThrow();

        SemanticResult result = analyzer.analyze(program, Path.of("main.mpl"));

        assertTrue(result.program().isPresent());
        HirVariableDeclaration phase = assertInstanceOf(HirVariableDeclaration.class,
            result.program().orElseThrow().statements().get(0));
        HirIntrinsicCall clock = assertInstanceOf(HirIntrinsicCall.class, phase.initializer());
        assertEquals("Clock", clock.namespace());
        assertEquals("time", clock.name());

        HirWhile whileLoop = assertInstanceOf(HirWhile.class,
            result.program().orElseThrow().statements().get(1));
        assertInstanceOf(HirConstant.class, whileLoop.condition());
        HirUnitIteration iteration = assertInstanceOf(HirUnitIteration.class, whileLoop.body().get(0));
        assertEquals("Dagger", iteration.unitType());
        assertEquals("dagger", iteration.mlogType());
        assertEquals(2, iteration.filters().size());

        HirBinary healthFilter = assertInstanceOf(HirBinary.class, iteration.filters().get(0));
        HirMemberAccess health = assertInstanceOf(HirMemberAccess.class, healthFilter.left());
        assertEquals("health", health.member());
        HirVariable healthTarget = assertInstanceOf(HirVariable.class, health.target());
        assertEquals("unit", healthTarget.name());

        HirBinary deadFilter = assertInstanceOf(HirBinary.class, iteration.filters().get(1));
        HirMemberAccess dead = assertInstanceOf(HirMemberAccess.class, deadFilter.left());
        assertEquals("dead", dead.member());

        HirUnitControl move = assertInstanceOf(HirUnitControl.class, iteration.body().get(0));
        assertEquals("unit", move.bindingName());
        assertEquals("move", move.command());
        assertEquals(2, move.arguments().size());
        assertEquals("cos", assertInstanceOf(HirIntrinsicCall.class, move.arguments().get(0)).name());
        assertEquals("sin", assertInstanceOf(HirIntrinsicCall.class, move.arguments().get(1)).name());
    }

    @Test
    void rejectsUserAccessToTheRuntimeOwnedFlag() {
        Program program = parser.parse("""
            for (var unit : Unit.getAllDagger().where(_.flag == 0.0)) {
                unit.move(1.0, 2.0);
            }
            """, Path.of("main.mpl")).program().orElseThrow();

        SemanticResult result = analyzer.analyze(program, Path.of("main.mpl"));

        assertTrue(result.program().isEmpty());
        assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> "MPL3304".equals(diagnostic.code())));
    }

    @Test
    void rejectsTheNonexistentDraggerUnitName() {
        Program program = parser.parse("""
            for (var unit : Unit.getAllDragger()) {
                unit.move(1.0, 2.0);
            }
            """, Path.of("main.mpl")).program().orElseThrow();

        SemanticResult result = analyzer.analyze(program, Path.of("main.mpl"));

        assertTrue(result.program().isEmpty());
        assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> "MPL3302".equals(diagnostic.code())));
    }

    @Test
    void rejectsUnitFieldsThatAreNotInTheV146WhereWhitelist() {
        Program program = parser.parse("""
            for (var unit : Unit.getAllDagger().where(_.team == 1)) {
                unit.move(1.0, 2.0);
            }
            """, Path.of("main.mpl")).program().orElseThrow();

        SemanticResult result = analyzer.analyze(program, Path.of("main.mpl"));

        assertTrue(result.program().isEmpty());
        assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> "MPL3304".equals(diagnostic.code())));
    }
}
