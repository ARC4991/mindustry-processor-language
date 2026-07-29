package com.arc.mpl.syntax;

import com.arc.mpl.ast.AssignmentExpression;
import com.arc.mpl.ast.ArrayLiteral;
import com.arc.mpl.ast.BinaryExpression;
import com.arc.mpl.ast.AccessModifier;
import com.arc.mpl.ast.ClassDeclaration;
import com.arc.mpl.ast.ExpressionStatement;
import com.arc.mpl.ast.ForStatement;
import com.arc.mpl.ast.IfStatement;
import com.arc.mpl.ast.NullLiteral;
import com.arc.mpl.ast.MemberAssignmentExpression;
import com.arc.mpl.ast.NewExpression;
import com.arc.mpl.ast.TupleLiteral;
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
    void parsesClassesConstructorsMemberAssignmentsAndNew() {
        ParseResult result = parser.parse("""
            export class Counter {
                value: Int;
                public fun Counter(initial: Int) { this.value = initial; }
                public fun add(amount: Int): Int {
                    this.value += amount;
                    return this.value;
                }
            }
            val counter = new Counter(1);
            counter.add(2);
            """, Path.of("main.mpl"));

        assertTrue(result.succeeded(), () -> result.diagnostics().toString());
        var program = result.program().orElseThrow();
        ClassDeclaration type = program.classes().get(0);
        assertEquals("Counter", type.name());
        assertEquals(AccessModifier.PRIVATE, type.fields().get(0).access());
        assertEquals(AccessModifier.PUBLIC, type.methods().get(0).access());
        assertEquals("Counter", program.exports().get(0).name());
        ExpressionStatement constructorAssignment = assertInstanceOf(ExpressionStatement.class,
            type.methods().get(0).function().body().statements().get(0));
        assertInstanceOf(MemberAssignmentExpression.class, constructorAssignment.expression());
        VariableDeclaration declaration = assertInstanceOf(VariableDeclaration.class, program.statements().get(0));
        NewExpression allocation = assertInstanceOf(NewExpression.class, declaration.initializer());
        assertEquals("Counter", allocation.className());
        assertEquals(1, allocation.arguments().size());
    }

    @Test
    void parsesNamedImportsExportsAndHardwareInjection() {
        ParseResult result = parser.parse("""
            import { Dashboard, render } from "@mpl/dashboard" with {
                screen: MainScreen
            };
            export fun twice(value: Int): Int { return value * 2; }
            export val answer: Int = 42;
            """, Path.of("main.mpl"));

        assertTrue(result.succeeded(), () -> result.diagnostics().toString());
        var program = result.program().orElseThrow();
        assertEquals(java.util.List.of("Dashboard", "render"), program.imports().get(0).names());
        assertEquals("@mpl/dashboard", program.imports().get(0).source());
        assertEquals("screen", program.imports().get(0).hardwareArguments().get(0).name());
        assertEquals("MainScreen", program.imports().get(0).hardwareArguments().get(0).value());
        assertEquals(java.util.List.of("twice", "answer"),
            program.exports().stream().map(com.arc.mpl.ast.ExportDeclaration::name).toList());
    }

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

    @Test
    void parsesTypedFunctionsAndReturnsSeparatelyFromTopLevelStatements() {
        ParseResult result = parser.parse("""
            fun add(left: Int, right: Int): Int {
                return left + right;
            }
            var result: Int = add(1, 2);
            """, Path.of("main.mpl"));

        assertTrue(result.succeeded());
        assertEquals(1, result.program().orElseThrow().functions().size());
        assertEquals("add", result.program().orElseThrow().functions().get(0).name());
        assertEquals(2, result.program().orElseThrow().functions().get(0).parameters().size());
        assertEquals(1, result.program().orElseThrow().statements().size());
    }

    @Test
    void parsesTupleAndArrayTypesUsingThePublicAggregateSyntax() {
        ParseResult result = parser.parse("""
            val test : (Int,Int,Int) = (1,2,3);
            val array : Int[] = [1,2,3,4,5];
            """, Path.of("main.mpl"));

        assertTrue(result.succeeded());
        VariableDeclaration tuple = assertInstanceOf(VariableDeclaration.class,
            result.program().orElseThrow().statements().get(0));
        VariableDeclaration array = assertInstanceOf(VariableDeclaration.class,
            result.program().orElseThrow().statements().get(1));
        assertEquals("(Int,Int,Int)", tuple.declaredType().orElseThrow());
        assertInstanceOf(TupleLiteral.class, tuple.initializer());
        assertEquals("Int[]", array.declaredType().orElseThrow());
        assertInstanceOf(ArrayLiteral.class, array.initializer());
    }

    @Test
    void parsesNullableUnitTypesAndNullLiterals() {
        ParseResult result = parser.parse("val leader: Unit<Dagger>? = null;", Path.of("main.mpl"));

        assertTrue(result.succeeded());
        VariableDeclaration declaration = assertInstanceOf(VariableDeclaration.class,
            result.program().orElseThrow().statements().get(0));
        assertEquals("Unit<Dagger>?", declaration.declaredType().orElseThrow());
        assertInstanceOf(NullLiteral.class, declaration.initializer());
    }
}
