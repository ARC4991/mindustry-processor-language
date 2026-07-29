package com.arc.mpl.syntax;

import com.arc.mpl.ast.AssignmentExpression;
import com.arc.mpl.ast.ArrayLiteral;
import com.arc.mpl.ast.BinaryExpression;
import com.arc.mpl.ast.BooleanLiteral;
import com.arc.mpl.ast.BlockStatement;
import com.arc.mpl.ast.BreakStatement;
import com.arc.mpl.ast.CallExpression;
import com.arc.mpl.ast.ContinueStatement;
import com.arc.mpl.ast.DoWhileStatement;
import com.arc.mpl.ast.Expression;
import com.arc.mpl.ast.ExpressionStatement;
import com.arc.mpl.ast.ForEachStatement;
import com.arc.mpl.ast.ForStatement;
import com.arc.mpl.ast.FunctionDeclaration;
import com.arc.mpl.ast.FunctionParameter;
import com.arc.mpl.ast.FloatLiteral;
import com.arc.mpl.ast.Identifier;
import com.arc.mpl.ast.IndexExpression;
import com.arc.mpl.ast.IfStatement;
import com.arc.mpl.ast.IntegerLiteral;
import com.arc.mpl.ast.LambdaExpression;
import com.arc.mpl.ast.MemberAccessExpression;
import com.arc.mpl.ast.Program;
import com.arc.mpl.ast.ReturnStatement;
import com.arc.mpl.ast.Statement;
import com.arc.mpl.ast.StringLiteral;
import com.arc.mpl.ast.TupleLiteral;
import com.arc.mpl.ast.UnaryExpression;
import com.arc.mpl.ast.VariableDeclaration;
import com.arc.mpl.ast.WhileStatement;
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
        MplLexer lexer = new MplLexer(CharStreams.fromString(source));
        lexer.removeErrorListeners();
        lexer.addErrorListener(errors);

        MplParser parser = new MplParser(new CommonTokenStream(lexer));
        parser.removeErrorListeners();
        parser.addErrorListener(errors);
        MplParser.ProgramContext program = parser.program();
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

    private static final class AstBuilder extends MplParserBaseVisitor<Object> {
        @Override
        public Program visitProgram(MplParser.ProgramContext context) {
            List<FunctionDeclaration> functions = new ArrayList<>();
            for (MplParser.FunctionDeclarationContext function : context.functionDeclaration()) {
                functions.add((FunctionDeclaration) visit(function));
            }
            List<Statement> statements = new ArrayList<>();
            for (MplParser.StatementContext statement : context.statement()) {
                statements.add((Statement) visit(statement));
            }
            return new Program(functions, statements);
        }

        @Override
        public FunctionDeclaration visitFunctionDeclaration(MplParser.FunctionDeclarationContext context) {
            List<FunctionParameter> parameters = context.parameter().stream()
                .map(value -> (FunctionParameter) visit(value)).toList();
            return new FunctionDeclaration(context.name.getText(), parameters,
                context.returnType == null ? Optional.empty() : Optional.of(context.returnType.getText()),
                (BlockStatement) visit(context.body), span(context));
        }

        @Override
        public FunctionParameter visitParameter(MplParser.ParameterContext context) {
            return new FunctionParameter(context.name.getText(), context.typeName.getText(), span(context));
        }

        @Override
        public Statement visitStatement(MplParser.StatementContext context) {
            if (context.whileStatement() != null) {
                return (Statement) visit(context.whileStatement());
            }
            if (context.doWhileStatement() != null) {
                return (Statement) visit(context.doWhileStatement());
            }
            if (context.ifStatement() != null) {
                return (Statement) visit(context.ifStatement());
            }
            if (context.forStatement() != null) {
                return (Statement) visit(context.forStatement());
            }
            if (context.forEachStatement() != null) {
                return (Statement) visit(context.forEachStatement());
            }
            if (context.block() != null) {
                return (Statement) visit(context.block());
            }
            if (context.variableDeclaration() != null) {
                return (Statement) visit(context.variableDeclaration());
            }
            if (context.BREAK() != null) return new BreakStatement(span(context));
            if (context.CONTINUE() != null) return new ContinueStatement(span(context));
            if (context.RETURN() != null) {
                return new ReturnStatement(context.returnValue == null ? Optional.empty()
                    : Optional.of((Expression) visit(context.returnValue)), span(context));
            }
            Expression expression = (Expression) visit(context.expression());
            return new ExpressionStatement(expression, span(context));
        }

        @Override
        public BlockStatement visitBlock(MplParser.BlockContext context) {
            List<Statement> statements = new ArrayList<>();
            for (MplParser.StatementContext statement : context.statement()) {
                statements.add((Statement) visit(statement));
            }
            return new BlockStatement(statements, span(context));
        }

        @Override
        public WhileStatement visitWhileStatement(MplParser.WhileStatementContext context) {
            return new WhileStatement(
                (Expression) visit(context.condition),
                (BlockStatement) visit(context.body),
                span(context));
        }

        @Override
        public DoWhileStatement visitDoWhileStatement(MplParser.DoWhileStatementContext context) {
            return new DoWhileStatement(
                (BlockStatement) visit(context.body),
                (Expression) visit(context.condition),
                span(context));
        }

        @Override
        public IfStatement visitIfStatement(MplParser.IfStatementContext context) {
            Optional<Statement> alternative = Optional.empty();
            if (context.elseBlock != null) alternative = Optional.of((BlockStatement) visit(context.elseBlock));
            if (context.elseIf != null) alternative = Optional.of((IfStatement) visit(context.elseIf));
            return new IfStatement(
                (Expression) visit(context.condition),
                (BlockStatement) visit(context.thenBlock),
                alternative,
                span(context));
        }

        @Override
        public ForEachStatement visitForEachStatement(MplParser.ForEachStatementContext context) {
            return new ForEachStatement(
                context.name.getText(),
                (Expression) visit(context.iterable),
                (BlockStatement) visit(context.body),
                span(context));
        }

        @Override
        public ForStatement visitForStatement(MplParser.ForStatementContext context) {
            return new ForStatement(
                context.initializerDeclaration == null ? Optional.empty()
                    : Optional.of((VariableDeclaration) visit(context.initializerDeclaration)),
                context.initializerExpression == null ? Optional.empty()
                    : Optional.of((Expression) visit(context.initializerExpression)),
                context.condition == null ? Optional.empty() : Optional.of((Expression) visit(context.condition)),
                context.update == null ? Optional.empty() : Optional.of((Expression) visit(context.update)),
                (BlockStatement) visit(context.body),
                span(context));
        }

        @Override
        public VariableDeclaration visitVariableDeclaration(MplParser.VariableDeclarationContext context) {
            boolean mutable = context.kind.getType() == MplParser.VAR;
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
        public Object visitExpression(MplParser.ExpressionContext context) {
            if (context.lambdaExpression() != null) {
                return visit(context.lambdaExpression());
            }
            return visit(context.assignmentExpression());
        }

        @Override
        public LambdaExpression visitLambdaExpression(MplParser.LambdaExpressionContext context) {
            return new LambdaExpression(
                context.lambdaParameter.getText(),
                (Expression) visit(context.body),
                span(context));
        }

        @Override
        public Object visitAssignmentExpression(MplParser.AssignmentExpressionContext context) {
            if (context.IDENTIFIER() == null) {
                return visit(context.logicalOrExpression());
            }
            Identifier target = new Identifier(context.IDENTIFIER().getText(), span(context.IDENTIFIER().getSymbol()));
            return new AssignmentExpression(target, context.operator.getText(),
                (Expression) visit(context.assignmentExpression()), span(context));
        }

        @Override
        public Object visitLogicalOrExpression(MplParser.LogicalOrExpressionContext context) {
            return fold(context, context.logicalAndExpression());
        }

        @Override
        public Object visitLogicalAndExpression(MplParser.LogicalAndExpressionContext context) {
            return fold(context, context.equalityExpression());
        }

        @Override
        public Object visitEqualityExpression(MplParser.EqualityExpressionContext context) {
            return fold(context, context.comparisonExpression());
        }

        @Override
        public Object visitComparisonExpression(MplParser.ComparisonExpressionContext context) {
            return fold(context, context.additiveExpression());
        }

        @Override
        public Object visitAdditiveExpression(MplParser.AdditiveExpressionContext context) {
            return fold(context, context.multiplicativeExpression());
        }

        @Override
        public Object visitMultiplicativeExpression(MplParser.MultiplicativeExpressionContext context) {
            return fold(context, context.unaryExpression());
        }

        @Override
        public Object visitUnaryExpression(MplParser.UnaryExpressionContext context) {
            if (context.operator == null) {
                return visit(context.postfixExpression());
            }
            return new UnaryExpression(context.operator.getText(),
                (Expression) visit(context.unaryExpression()), span(context));
        }

        @Override
        public Object visitPostfixExpression(MplParser.PostfixExpressionContext context) {
            Expression result = (Expression) visit(context.primaryExpression());
            for (MplParser.PostfixSuffixContext suffix : context.postfixSuffix()) {
                if (suffix.member != null) {
                    result = new MemberAccessExpression(result, suffix.member.getText(), span(context));
                    continue;
                }
                if (suffix.index != null) {
                    result = new IndexExpression(result, (Expression) visit(suffix.index), span(context));
                    continue;
                }
                List<Expression> arguments = new ArrayList<>();
                for (MplParser.ExpressionContext argument : suffix.expression()) {
                    arguments.add((Expression) visit(argument));
                }
                result = new CallExpression(result, arguments, span(context));
            }
            return result;
        }

        @Override
        public Object visitPrimaryExpression(MplParser.PrimaryExpressionContext context) {
            if (context.INT_LITERAL() != null) {
                return new IntegerLiteral(Long.parseLong(context.INT_LITERAL().getText()), span(context));
            }
            if (context.FLOAT_LITERAL() != null) {
                return new FloatLiteral(Double.parseDouble(context.FLOAT_LITERAL().getText()), span(context));
            }
            if (context.STRING_LITERAL() != null) {
                String token = context.STRING_LITERAL().getText();
                return new StringLiteral(unescape(token.substring(1, token.length() - 1)), span(context));
            }
            if (context.TRUE() != null || context.FALSE() != null) {
                return new BooleanLiteral(context.TRUE() != null, span(context));
            }
            if (context.name != null) {
                return new Identifier(context.name.getText(), span(context));
            }
            if (context.LBRACK() != null) {
                List<Expression> elements = context.expression().stream().map(value -> (Expression) visit(value)).toList();
                return new ArrayLiteral(elements, span(context));
            }
            if (!context.tupleElement.isEmpty()) {
                List<Expression> elements = context.tupleElement.stream().map(value -> (Expression) visit(value)).toList();
                return new TupleLiteral(elements, span(context));
            }
            return visit(context.grouped);
        }

        private String unescape(String text) {
            return text.replace("\\n", "\n").replace("\\r", "\r").replace("\\t", "\t")
                .replace("\\\"", "\"").replace("\\\\", "\\");
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
