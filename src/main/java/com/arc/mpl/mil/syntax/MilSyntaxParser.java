package com.arc.mpl.mil.syntax;

import com.arc.mpl.ast.ArrayLiteral;
import com.arc.mpl.ast.AssignmentExpression;
import com.arc.mpl.ast.BinaryExpression;
import com.arc.mpl.ast.BlockStatement;
import com.arc.mpl.ast.BooleanLiteral;
import com.arc.mpl.ast.BreakStatement;
import com.arc.mpl.ast.CallExpression;
import com.arc.mpl.ast.ContinueStatement;
import com.arc.mpl.ast.DoWhileStatement;
import com.arc.mpl.ast.Expression;
import com.arc.mpl.ast.ExpressionStatement;
import com.arc.mpl.ast.ExportDeclaration;
import com.arc.mpl.ast.FloatLiteral;
import com.arc.mpl.ast.ForEachStatement;
import com.arc.mpl.ast.ForStatement;
import com.arc.mpl.ast.FunctionDeclaration;
import com.arc.mpl.ast.FunctionParameter;
import com.arc.mpl.ast.Identifier;
import com.arc.mpl.ast.IfStatement;
import com.arc.mpl.ast.ImportDeclaration;
import com.arc.mpl.ast.IndexExpression;
import com.arc.mpl.ast.IntegerLiteral;
import com.arc.mpl.ast.LambdaExpression;
import com.arc.mpl.ast.MemberAccessExpression;
import com.arc.mpl.ast.MilGameSymbolExpression;
import com.arc.mpl.ast.MilMacroBlockStatement;
import com.arc.mpl.ast.MilMacroCallExpression;
import com.arc.mpl.ast.NullLiteral;
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
import com.arc.mpl.diagnostic.Severity;
import com.arc.mpl.profile.TargetProfile;
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

/** Independent ANTLR entry for structured, profile-restricted MIL source. */
public final class MilSyntaxParser {
    public MilParseResult parse(String source, Path file, TargetProfile profile, MilSourceKind sourceKind) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        SyntaxErrorListener errors = new SyntaxErrorListener(file, diagnostics);
        MilLexer lexer = new MilLexer(CharStreams.fromString(source));
        lexer.removeErrorListeners();
        lexer.addErrorListener(errors);

        MilParser parser = new MilParser(new CommonTokenStream(lexer));
        parser.removeErrorListeners();
        parser.addErrorListener(errors);
        MilParser.ProgramContext program = parser.program();
        if (!diagnostics.isEmpty()) return new MilParseResult(Optional.empty(), diagnostics);

        AstBuilder builder = new AstBuilder();
        Program ast = builder.visitProgram(program);
        MilDocument document = new MilDocument(ast, builder.macroCalls, builder.gameSymbols);
        diagnostics.addAll(validateMacros(document, file, profile, sourceKind));
        return new MilParseResult(diagnostics.isEmpty() ? Optional.of(document) : Optional.empty(), diagnostics);
    }

    private List<Diagnostic> validateMacros(MilDocument document, Path file, TargetProfile profile,
                                            MilSourceKind sourceKind) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        for (MilDocument.MacroCall call : document.macroCalls()) {
            Optional<TargetProfile.Macro> declaration = profile.macro(call.name());
            if (declaration.isEmpty()) {
                diagnostics.add(error("MIL3001", "target " + profile.id() + " 未声明 MIL 宏 " + call.name(), file, call.span()));
                continue;
            }
            TargetProfile.Macro macro = declaration.orElseThrow();
            if (sourceKind == MilSourceKind.USER
                && macro.visibility() == TargetProfile.MacroVisibility.RUNTIME_PRIVATE) {
                diagnostics.add(error("MIL3002", "MIL 宏 " + call.name() + " 仅供编译器 runtime 使用", file, call.span()));
            }
            int fixedParameters = macro.parameters().size();
            boolean variadic = fixedParameters > 0
                && macro.parameters().get(fixedParameters - 1).type().endsWith("[]");
            int minimum = variadic ? fixedParameters - 1 : fixedParameters;
            if (call.argumentCount() < minimum || (!variadic && call.argumentCount() != fixedParameters)) {
                String expected = variadic ? "至少 " + minimum : Integer.toString(fixedParameters);
                diagnostics.add(error("MIL3003", "MIL 宏 " + call.name() + " 需要 " + expected
                    + " 个参数，实际为 " + call.argumentCount(), file, call.span()));
            }
            boolean bodyRequired = macro.body() == TargetProfile.MacroBody.REQUIRED;
            if (call.hasBody() != bodyRequired) {
                diagnostics.add(error("MIL3004", "MIL 宏 " + call.name()
                    + (bodyRequired ? " 必须带结构化代码块" : " 不能带结构化代码块"), file, call.span()));
            }
        }
        return List.copyOf(diagnostics);
    }

    private Diagnostic error(String code, String message, Path file, SourceSpan span) {
        return new Diagnostic(Severity.ERROR, code, message, Optional.ofNullable(file), Optional.of(span));
    }

    private static SourceSpan span(Token start, Token stop) {
        int endColumn = stop.getCharPositionInLine() + Math.max(1, stop.getText().length()) + 1;
        return new SourceSpan(start.getLine(), start.getCharPositionInLine() + 1,
            stop.getLine(), endColumn);
    }

    private static SourceSpan span(org.antlr.v4.runtime.ParserRuleContext context) {
        return span(context.getStart(), context.getStop());
    }

    private static SourceSpan span(Token token) {
        return span(token, token);
    }

    /** Builds the shared structured AST while retaining MIL-only macro and game-symbol nodes. */
    private static final class AstBuilder extends MilParserBaseVisitor<Object> {
        private final List<MilDocument.MacroCall> macroCalls = new ArrayList<>();
        private final List<MilDocument.GameSymbol> gameSymbols = new ArrayList<>();

        @Override
        public Program visitProgram(MilParser.ProgramContext context) {
            List<ImportDeclaration> imports = context.importDeclaration().stream()
                .map(value -> (ImportDeclaration) visit(value)).toList();
            List<ExportDeclaration> exports = new ArrayList<>();
            List<FunctionDeclaration> functions = new ArrayList<>();
            List<Statement> statements = new ArrayList<>();
            for (MilParser.TopLevelDeclarationContext declaration : context.topLevelDeclaration()) {
                if (declaration.functionDeclaration() != null) {
                    FunctionDeclaration function = (FunctionDeclaration) visit(declaration.functionDeclaration());
                    functions.add(function);
                    if (declaration.exported != null) {
                        exports.add(new ExportDeclaration(function.name(), span(declaration)));
                    }
                } else if (declaration.variableDeclaration() != null) {
                    VariableDeclaration variable = (VariableDeclaration) visit(declaration.variableDeclaration());
                    statements.add(variable);
                    if (declaration.exported != null) {
                        exports.add(new ExportDeclaration(variable.name(), span(declaration)));
                    }
                } else {
                    statements.add((Statement) visit(declaration.statement()));
                }
            }
            return new Program(imports, exports, functions, statements);
        }

        @Override
        public ImportDeclaration visitImportDeclaration(MilParser.ImportDeclarationContext context) {
            List<ImportDeclaration.HardwareArgument> hardware = context.hardwareArgument().stream()
                .map(value -> new ImportDeclaration.HardwareArgument(value.name.getText(), value.value.getText(), span(value)))
                .toList();
            return new ImportDeclaration(context.importedName.stream().map(Token::getText).toList(),
                stringValue(context.source.getText()), hardware, span(context));
        }

        @Override
        public FunctionDeclaration visitFunctionDeclaration(MilParser.FunctionDeclarationContext context) {
            List<FunctionParameter> parameters = context.parameter().stream()
                .map(value -> (FunctionParameter) visit(value)).toList();
            return new FunctionDeclaration(context.name.getText(), parameters,
                context.returnType == null ? Optional.empty() : Optional.of(context.returnType.getText()),
                (BlockStatement) visit(context.body), span(context));
        }

        @Override
        public FunctionParameter visitParameter(MilParser.ParameterContext context) {
            return new FunctionParameter(context.name.getText(), context.typeName.getText(), span(context));
        }

        @Override
        public Statement visitStatement(MilParser.StatementContext context) {
            if (context.macroBlockStatement() != null) return (Statement) visit(context.macroBlockStatement());
            if (context.whileStatement() != null) return (Statement) visit(context.whileStatement());
            if (context.doWhileStatement() != null) return (Statement) visit(context.doWhileStatement());
            if (context.ifStatement() != null) return (Statement) visit(context.ifStatement());
            if (context.forStatement() != null) return (Statement) visit(context.forStatement());
            if (context.forEachStatement() != null) return (Statement) visit(context.forEachStatement());
            if (context.block() != null) return (Statement) visit(context.block());
            if (context.variableDeclaration() != null) return (Statement) visit(context.variableDeclaration());
            if (context.BREAK() != null) return new BreakStatement(span(context));
            if (context.CONTINUE() != null) return new ContinueStatement(span(context));
            if (context.RETURN() != null) {
                return new ReturnStatement(context.returnValue == null ? Optional.empty()
                    : Optional.of((Expression) visit(context.returnValue)), span(context));
            }
            return new ExpressionStatement((Expression) visit(context.expression()), span(context));
        }

        @Override
        public MilMacroBlockStatement visitMacroBlockStatement(MilParser.MacroBlockStatementContext context) {
            return new MilMacroBlockStatement((MilMacroCallExpression) visit(context.macroInvocation()),
                (BlockStatement) visit(context.block()), span(context));
        }

        @Override
        public BlockStatement visitBlock(MilParser.BlockContext context) {
            return new BlockStatement(context.statement().stream().map(value -> (Statement) visit(value)).toList(),
                span(context));
        }

        @Override
        public WhileStatement visitWhileStatement(MilParser.WhileStatementContext context) {
            return new WhileStatement((Expression) visit(context.condition),
                (BlockStatement) visit(context.body), span(context));
        }

        @Override
        public DoWhileStatement visitDoWhileStatement(MilParser.DoWhileStatementContext context) {
            return new DoWhileStatement((BlockStatement) visit(context.body),
                (Expression) visit(context.condition), span(context));
        }

        @Override
        public IfStatement visitIfStatement(MilParser.IfStatementContext context) {
            Optional<Statement> alternative = Optional.empty();
            if (context.elseBlock != null) alternative = Optional.of((BlockStatement) visit(context.elseBlock));
            if (context.elseIf != null) alternative = Optional.of((IfStatement) visit(context.elseIf));
            return new IfStatement((Expression) visit(context.condition), (BlockStatement) visit(context.thenBlock),
                alternative, span(context));
        }

        @Override
        public ForEachStatement visitForEachStatement(MilParser.ForEachStatementContext context) {
            return new ForEachStatement(context.name.getText(), (Expression) visit(context.iterable),
                (BlockStatement) visit(context.body), span(context));
        }

        @Override
        public ForStatement visitForStatement(MilParser.ForStatementContext context) {
            return new ForStatement(
                context.initializerDeclaration == null ? Optional.empty()
                    : Optional.of((VariableDeclaration) visit(context.initializerDeclaration)),
                context.initializerExpression == null ? Optional.empty()
                    : Optional.of((Expression) visit(context.initializerExpression)),
                context.condition == null ? Optional.empty() : Optional.of((Expression) visit(context.condition)),
                context.update == null ? Optional.empty() : Optional.of((Expression) visit(context.update)),
                (BlockStatement) visit(context.body), span(context));
        }

        @Override
        public VariableDeclaration visitVariableDeclaration(MilParser.VariableDeclarationContext context) {
            return new VariableDeclaration(context.kind.getType() == MilParser.VAR, context.name.getText(),
                context.typeName == null ? Optional.empty() : Optional.of(context.typeName.getText()),
                (Expression) visit(context.expression()), span(context));
        }

        @Override
        public Object visitExpression(MilParser.ExpressionContext context) {
            return context.lambdaExpression() == null
                ? visit(context.assignmentExpression()) : visit(context.lambdaExpression());
        }

        @Override
        public LambdaExpression visitLambdaExpression(MilParser.LambdaExpressionContext context) {
            return new LambdaExpression(context.lambdaParameter.getText(), (Expression) visit(context.body), span(context));
        }

        @Override
        public Object visitAssignmentExpression(MilParser.AssignmentExpressionContext context) {
            if (context.IDENTIFIER() == null) return visit(context.logicalOrExpression());
            Identifier target = new Identifier(context.IDENTIFIER().getText(), span(context.IDENTIFIER().getSymbol()));
            return new AssignmentExpression(target, context.operator.getText(),
                (Expression) visit(context.assignmentExpression()), span(context));
        }

        @Override public Object visitLogicalOrExpression(MilParser.LogicalOrExpressionContext context) {
            return fold(context, context.logicalAndExpression());
        }
        @Override public Object visitLogicalAndExpression(MilParser.LogicalAndExpressionContext context) {
            return fold(context, context.equalityExpression());
        }
        @Override public Object visitEqualityExpression(MilParser.EqualityExpressionContext context) {
            return fold(context, context.comparisonExpression());
        }
        @Override public Object visitComparisonExpression(MilParser.ComparisonExpressionContext context) {
            return fold(context, context.additiveExpression());
        }
        @Override public Object visitAdditiveExpression(MilParser.AdditiveExpressionContext context) {
            return fold(context, context.multiplicativeExpression());
        }
        @Override public Object visitMultiplicativeExpression(MilParser.MultiplicativeExpressionContext context) {
            return fold(context, context.unaryExpression());
        }

        @Override
        public Object visitUnaryExpression(MilParser.UnaryExpressionContext context) {
            if (context.operator == null) return visit(context.postfixExpression());
            return new UnaryExpression(context.operator.getText(), (Expression) visit(context.unaryExpression()), span(context));
        }

        @Override
        public Object visitPostfixExpression(MilParser.PostfixExpressionContext context) {
            Expression result = (Expression) visit(context.primaryExpression());
            for (MilParser.PostfixSuffixContext suffix : context.postfixSuffix()) {
                if (suffix.member != null) {
                    result = new MemberAccessExpression(result, suffix.member.getText(), span(context));
                } else if (suffix.index != null) {
                    result = new IndexExpression(result, (Expression) visit(suffix.index), span(context));
                } else {
                    List<Expression> arguments = suffix.expression().stream()
                        .map(value -> (Expression) visit(value)).toList();
                    result = new CallExpression(result, arguments, span(context));
                }
            }
            return result;
        }

        @Override
        public Object visitPrimaryExpression(MilParser.PrimaryExpressionContext context) {
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
            if (context.NULL() != null) return new NullLiteral(span(context));
            if (context.name != null) return new Identifier(context.name.getText(), span(context));
            if (context.macroInvocation() != null) return visit(context.macroInvocation());
            if (context.gameSymbol() != null) return visit(context.gameSymbol());
            if (context.LBRACK() != null) {
                return new ArrayLiteral(context.expression().stream()
                    .map(value -> (Expression) visit(value)).toList(), span(context));
            }
            if (!context.tupleElement.isEmpty()) {
                return new TupleLiteral(context.tupleElement.stream()
                    .map(value -> (Expression) visit(value)).toList(), span(context));
            }
            return visit(context.grouped);
        }

        @Override
        public MilMacroCallExpression visitMacroInvocation(MilParser.MacroInvocationContext context) {
            boolean hasBody = context.getParent() instanceof MilParser.MacroBlockStatementContext;
            macroCalls.add(new MilDocument.MacroCall(context.macroName().getText(), context.expression().size(),
                hasBody, span(context.getStart(), context.getStop())));
            return new MilMacroCallExpression(context.macroName().getText(), context.expression().stream()
                .map(value -> (Expression) visit(value)).toList(), span(context));
        }

        @Override
        public MilGameSymbolExpression visitGameSymbol(MilParser.GameSymbolContext context) {
            gameSymbols.add(new MilDocument.GameSymbol(context.IDENTIFIER().getText(),
                span(context.getStart(), context.getStop())));
            return new MilGameSymbolExpression(context.IDENTIFIER().getText(), span(context));
        }

        private Expression fold(org.antlr.v4.runtime.ParserRuleContext context,
                                List<? extends ParseTree> operands) {
            Expression result = (Expression) visit(operands.get(0));
            for (int index = 1; index < operands.size(); index++) {
                String operator = context.getChild(2 * index - 1).getText();
                result = new BinaryExpression(result, operator, (Expression) visit(operands.get(index)), span(context));
            }
            return result;
        }

        private String unescape(String text) {
            return text.replace("\\n", "\n").replace("\\r", "\r").replace("\\t", "\t")
                .replace("\\\"", "\"").replace("\\\\", "\\");
        }

        private String stringValue(String token) {
            return unescape(token.substring(1, token.length() - 1));
        }
    }

    private static final class SyntaxErrorListener extends BaseErrorListener {
        private final Path file;
        private final List<Diagnostic> diagnostics;

        private SyntaxErrorListener(Path file, List<Diagnostic> diagnostics) {
            this.file = file;
            this.diagnostics = diagnostics;
        }

        @Override
        public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line,
                                int charPositionInLine, String message, RecognitionException exception) {
            SourceSpan span = new SourceSpan(line, charPositionInLine + 1, line, charPositionInLine + 2);
            diagnostics.add(new Diagnostic(Severity.ERROR, "MIL2001", "MIL 语法错误：" + message,
                Optional.ofNullable(file), Optional.of(span)));
        }
    }
}
