package com.arc.mpl.syntax;

import com.arc.mpl.ast.AssignmentExpression;
import com.arc.mpl.ast.BinaryExpression;
import com.arc.mpl.ast.BooleanLiteral;
import com.arc.mpl.ast.Expression;
import com.arc.mpl.ast.ExpressionStatement;
import com.arc.mpl.ast.FloatLiteral;
import com.arc.mpl.ast.Identifier;
import com.arc.mpl.ast.IntegerLiteral;
import com.arc.mpl.ast.Program;
import com.arc.mpl.ast.Statement;
import com.arc.mpl.ast.UnaryExpression;
import com.arc.mpl.ast.VariableDeclaration;
import com.arc.mpl.diagnostic.Diagnostic;
import com.arc.mpl.diagnostic.Diagnostic.SourceSpan;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** ANTLR-backed parser for the currently implemented MPL expression subset. */
public final class MplSyntaxParser {
    public ParseResult parse(String source, Path file) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        SyntaxErrorListener errors = new SyntaxErrorListener(file, diagnostics);
        MplGrammarLexer lexer = new MplGrammarLexer(CharStreams.fromString(source));
        lexer.removeErrorListeners();
        lexer.addErrorListener(errors);

        MplGrammarParser parser = new MplGrammarParser(new CommonTokenStream(lexer));
        parser.removeErrorListeners();
        parser.addErrorListener(errors);
        MplGrammarParser.ProgramContext program = parser.program();
        if (!diagnostics.isEmpty()) {
            return new ParseResult(Optional.empty(), diagnostics);
        }
        return new ParseResult(Optional.of(new AstBuilder().visitProgram(program)), diagnostics);
    }

    private static final class SyntaxErrorListener extends BaseErrorListener {
        private final Path file;
        private final List<Diagnostic> diagnostics;

        private SyntaxErrorListener(Path file, List<Diagnostic> diagnostics) {
            this.file = file;
            this.diagnostics = diagnostics;
        }

        @Override
        public void syntaxError(
            Recognizer<?, ?> recognizer,
            Object offendingSymbol,
            int line,
            int charPositionInLine,
            String message,
            RecognitionException exception
        ) {
            SourceSpan span = new SourceSpan(line, charPositionInLine + 1, line, charPositionInLine + 2);
            diagnostics.add(new Diagnostic(
                com.arc.mpl.diagnostic.Severity.ERROR,
                "MPL2001",
                "语法错误：" + message,
                Optional.ofNullable(file),
                Optional.of(span)));
        }
    }

    private static final class AstBuilder extends MplGrammarBaseVisitor<Object> {
        @Override
        public Program visitProgram(MplGrammarParser.ProgramContext context) {
            List<Statement> statements = new ArrayList<>();
            for (MplGrammarParser.StatementContext statement : context.statement()) {
                statements.add((Statement) visit(statement));
            }
            return new Program(statements);
        }

        @Override
        public Statement visitStatement(MplGrammarParser.StatementContext context) {
            if (context.variableDeclaration() != null) {
                return (Statement) visit(context.variableDeclaration());
            }
            Expression expression = (Expression) visit(context.expression());
            return new ExpressionStatement(expression, span(context));
        }

        @Override
        public VariableDeclaration visitVariableDeclaration(MplGrammarParser.VariableDeclarationContext context) {
            boolean mutable = context.kind.getType() == MplGrammarParser.VAR;
            Optional<String> declaredType = context.typeName == null
                ? Optional.empty()
                : Optional.of(context.typeName.getText());
            return new VariableDeclaration(
                mutable,
                context.name.getText(),
                declaredType,
                (Expression) visit(context.expression()),
                span(context));
        }

        @Override
        public Object visitExpression(MplGrammarParser.ExpressionContext context) {
            return visit(context.assignmentExpression());
        }

        @Override
        public Object visitAssignmentExpression(MplGrammarParser.AssignmentExpressionContext context) {
            if (context.IDENTIFIER() == null) {
                return visit(context.logicalOrExpression());
            }
            Identifier target = new Identifier(context.IDENTIFIER().getText(), span(context.IDENTIFIER().getSymbol()));
            return new AssignmentExpression(target, context.operator.getText(),
                (Expression) visit(context.assignmentExpression()), span(context));
        }

        @Override
        public Object visitLogicalOrExpression(MplGrammarParser.LogicalOrExpressionContext context) {
            return fold(context, context.logicalAndExpression());
        }

        @Override
        public Object visitLogicalAndExpression(MplGrammarParser.LogicalAndExpressionContext context) {
            return fold(context, context.equalityExpression());
        }

        @Override
        public Object visitEqualityExpression(MplGrammarParser.EqualityExpressionContext context) {
            return fold(context, context.comparisonExpression());
        }

        @Override
        public Object visitComparisonExpression(MplGrammarParser.ComparisonExpressionContext context) {
            return fold(context, context.additiveExpression());
        }

        @Override
        public Object visitAdditiveExpression(MplGrammarParser.AdditiveExpressionContext context) {
            return fold(context, context.multiplicativeExpression());
        }

        @Override
        public Object visitMultiplicativeExpression(MplGrammarParser.MultiplicativeExpressionContext context) {
            return fold(context, context.unaryExpression());
        }

        @Override
        public Object visitUnaryExpression(MplGrammarParser.UnaryExpressionContext context) {
            if (context.operator == null) {
                return visit(context.primaryExpression());
            }
            return new UnaryExpression(context.operator.getText(),
                (Expression) visit(context.unaryExpression()), span(context));
        }

        @Override
        public Object visitPrimaryExpression(MplGrammarParser.PrimaryExpressionContext context) {
            if (context.INT_LITERAL() != null) {
                return new IntegerLiteral(Long.parseLong(context.INT_LITERAL().getText()), span(context));
            }
            if (context.FLOAT_LITERAL() != null) {
                return new FloatLiteral(Double.parseDouble(context.FLOAT_LITERAL().getText()), span(context));
            }
            if (context.TRUE() != null || context.FALSE() != null) {
                return new BooleanLiteral(context.TRUE() != null, span(context));
            }
            if (context.IDENTIFIER() != null) {
                return new Identifier(context.IDENTIFIER().getText(), span(context));
            }
            return visit(context.expression());
        }

        private Expression fold(org.antlr.v4.runtime.ParserRuleContext context, List<? extends ParseTree> operands) {
            Expression result = (Expression) visit(operands.get(0));
            for (int index = 1; index < operands.size(); index++) {
                String operator = context.getChild(2 * index - 1).getText();
                result = new BinaryExpression(result, operator, (Expression) visit(operands.get(index)), span(context));
            }
            return result;
        }

        private SourceSpan span(org.antlr.v4.runtime.ParserRuleContext context) {
            return span(context.getStart(), context.getStop());
        }

        private SourceSpan span(Token token) {
            return span(token, token);
        }

        private SourceSpan span(Token start, Token end) {
            int endColumn = end.getCharPositionInLine() + end.getText().length() + 1;
            return new SourceSpan(start.getLine(), start.getCharPositionInLine() + 1, end.getLine(), endColumn);
        }
    }
}
