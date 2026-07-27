package com.arc.mpl.syntax;

import com.arc.mpl.ast.BlockStatement;
import com.arc.mpl.ast.CallExpression;
import com.arc.mpl.ast.ForEachStatement;
import com.arc.mpl.ast.LambdaExpression;
import com.arc.mpl.ast.WhileStatement;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnitSetSyntaxParserTest {
    private final MplSyntaxParser parser = new MplSyntaxParser();

    @Test
    void parsesAWhileWrappedUnitSetQueryWithChainedFilters() {
        String source = """
            while (true) {
                for (var unit : Unit.getAllDagger()
                    .where(_.health > 0.0)
                    .where(_.dead == false)) {
                    unit.move(Math.cos(Clock.time), Math.sin(Clock.time));
                }
            }
            """;

        ParseResult result = parser.parse(source, Path.of("main.mpl"));

        assertTrue(result.succeeded());
        WhileStatement loop = assertInstanceOf(WhileStatement.class,
            result.program().orElseThrow().statements().get(0));
        BlockStatement whileBody = loop.body();
        ForEachStatement iteration = assertInstanceOf(ForEachStatement.class, whileBody.statements().get(0));
        assertEquals("unit", iteration.name());
        assertInstanceOf(CallExpression.class, iteration.iterable());
    }

    @Test
    void parsesAnExplicitSingleParameterWhereLambda() {
        ParseResult result = parser.parse("""
            for (var unit : Unit.getAllDagger().where(candidate => candidate.health > 0.0)) {
                unit.move(1.0, 2.0);
            }
            """, Path.of("main.mpl"));

        assertTrue(result.succeeded());
        ForEachStatement iteration = assertInstanceOf(ForEachStatement.class,
            result.program().orElseThrow().statements().get(0));
        CallExpression where = assertInstanceOf(CallExpression.class, iteration.iterable());
        assertInstanceOf(LambdaExpression.class, where.arguments().get(0));
    }
}
