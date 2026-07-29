package com.arc.mpl.syntax;

import com.arc.mpl.ast.AssignmentExpression;
import com.arc.mpl.ast.BinaryExpression;
import com.arc.mpl.ast.ExpressionStatement;
import com.arc.mpl.ast.ForStatement;
import com.arc.mpl.ast.IfStatement;
import com.arc.mpl.ast.VariableDeclaration;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MplSyntaxParserTest {
    private final MplSyntaxParser parser = new MplSyntaxParser();

    @Test
    void buildsAnAstWithExpressionPrecedenceAndSourcePositions() {
        ParseResult result = parser.parse("var total: Int = 1 + 2 * 3;\ntotal += 4;", Path.of("main.mpl"));

        assertTrue(result.succeeded());
        assertEquals(2, result.program().orElseThrow().statements().size());
        VariableDeclaration declaration = assertInstanceOf(VariableDeclaration.class,
            result.program().orElseThrow().statements().get(0));
        assertTrue(declaration.mutable());
        assertEquals("total", declaration.name());
        assertEquals("Int", declaration.declaredType().orElseThrow());
        BinaryExpression sum = assertInstanceOf(BinaryExpression.class, declaration.initializer());
        assertEquals("+", sum.operator());
        assertInstanceOf(BinaryExpression.class, sum.right());
        assertEquals(1, declaration.span().startLine());

        ExpressionStatement assignmentStatement = assertInstanceOf(ExpressionStatement.class,
            result.program().orElseThrow().statements().get(1));
        AssignmentExpression assignment = assertInstanceOf(AssignmentExpression.class, assignmentStatement.expression());
        assertEquals("+=", assignment.operator());
        assertEquals(2, assignment.span().startLine());
    }

    @Test
    void reportsAStableDiagnosticForMalformedSource() {
        ParseResult result = parser.parse("var total = ;", Path.of("main.mpl"));

        assertFalse(result.succeeded());
        assertTrue(result.program().isEmpty());
        assertEquals("MPL2001", result.diagnostics().get(0).code());
        assertEquals(Path.of("main.mpl"), result.diagnostics().get(0).file().orElseThrow());
    }

    @Test
    void representsElseIfAsANestedConditionalBranch() {
        ParseResult result = parser.parse("if (false) { } else if (true) { } else { }", Path.of("main.mpl"));

        assertTrue(result.succeeded());
        IfStatement outer = assertInstanceOf(IfStatement.class, result.program().orElseThrow().statements().get(0));
        IfStatement nested = assertInstanceOf(IfStatement.class, outer.elseBranch().orElseThrow());
        assertTrue(nested.elseBranch().isPresent());
    }

    @Test
    void distinguishesCountingForFromForEach() {
        ParseResult result = parser.parse("for (var i: Int = 0; i < 3; i += 1) { }", Path.of("main.mpl"));

        assertTrue(result.succeeded());
        ForStatement loop = assertInstanceOf(ForStatement.class, result.program().orElseThrow().statements().get(0));
        assertEquals("i", loop.declarationInitializer().orElseThrow().name());
        assertEquals("+=", assertInstanceOf(AssignmentExpression.class, loop.update().orElseThrow()).operator());
    }

    @Test
    void acceptsOmittedCountingForSections() {
        ParseResult result = parser.parse("for (;;) { break; }", Path.of("main.mpl"));

        assertTrue(result.succeeded());
        ForStatement loop = assertInstanceOf(ForStatement.class, result.program().orElseThrow().statements().get(0));
        assertTrue(loop.declarationInitializer().isEmpty());
        assertTrue(loop.expressionInitializer().isEmpty());
        assertTrue(loop.condition().isEmpty());
        assertTrue(loop.update().isEmpty());
    }
}
