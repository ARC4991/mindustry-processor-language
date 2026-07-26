package com.arc.mpl.semantic;

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
import com.arc.mpl.diagnostic.Severity;
import com.arc.mpl.hir.HirAssignment;
import com.arc.mpl.hir.HirBinary;
import com.arc.mpl.hir.HirConstant;
import com.arc.mpl.hir.HirExpression;
import com.arc.mpl.hir.HirExpressionStatement;
import com.arc.mpl.hir.HirProgram;
import com.arc.mpl.hir.HirStatement;
import com.arc.mpl.hir.HirUnary;
import com.arc.mpl.hir.HirVariable;
import com.arc.mpl.hir.HirVariableDeclaration;
import com.arc.mpl.hir.ValueType;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Name resolution and strict type checks for the non-control-flow MPL subset. */
public final class SemanticAnalyzer {
    private final Map<String, Symbol> symbols = new HashMap<>();
    private final List<Diagnostic> diagnostics = new ArrayList<>();
    private Path file;

    public SemanticResult analyze(Program program, Path sourceFile) {
        symbols.clear();
        diagnostics.clear();
        file = sourceFile;
        List<HirStatement> statements = new ArrayList<>();
        for (Statement statement : program.statements()) {
            statements.add(analyzeStatement(statement));
        }
        return new SemanticResult(diagnostics.isEmpty() ? Optional.of(new HirProgram(statements)) : Optional.empty(), diagnostics);
    }

    private HirStatement analyzeStatement(Statement statement) {
        if (statement instanceof VariableDeclaration declaration) {
            return analyzeDeclaration(declaration);
        }
        ExpressionStatement expressionStatement = (ExpressionStatement) statement;
        return new HirExpressionStatement(analyzeExpression(expressionStatement.expression()));
    }

    private HirStatement analyzeDeclaration(VariableDeclaration declaration) {
        HirExpression initializer = analyzeExpression(declaration.initializer());
        ValueType type = declaration.declaredType().map(value -> parseType(value, declaration.span())).orElse(initializer.type());
        if (!type.canAssignFrom(initializer.type())) {
            error("MPL3103", "不能将 " + display(initializer.type()) + " 赋给 " + display(type), declaration.initializer().span());
        }
        Symbol previous = symbols.putIfAbsent(declaration.name(), new Symbol(type, declaration.mutable()));
        if (previous != null) {
            error("MPL3101", "变量已声明：" + declaration.name(), declaration.span());
        }
        return new HirVariableDeclaration(declaration.name(), type, initializer);
    }

    private HirExpression analyzeExpression(Expression expression) {
        if (expression instanceof IntegerLiteral integer) {
            return new HirConstant(Long.toString(integer.value()), ValueType.INT);
        }
        if (expression instanceof FloatLiteral decimal) {
            return new HirConstant(Double.toString(decimal.value()), ValueType.FLOAT);
        }
        if (expression instanceof BooleanLiteral bool) {
            return new HirConstant(bool.value() ? "1" : "0", ValueType.BOOL);
        }
        if (expression instanceof Identifier identifier) {
            Symbol symbol = symbols.get(identifier.name());
            if (symbol == null) {
                error("MPL3102", "未声明的变量：" + identifier.name(), identifier.span());
                return new HirVariable(identifier.name(), ValueType.ERROR);
            }
            return new HirVariable(identifier.name(), symbol.type());
        }
        if (expression instanceof UnaryExpression unary) {
            HirExpression operand = analyzeExpression(unary.operand());
            ValueType type = switch (unary.operator()) {
                case "+", "-" -> requireNumeric(operand.type(), unary.span(), "一元运算符 " + unary.operator());
                case "!" -> requireBool(operand.type(), unary.span(), "一元运算符 !");
                default -> ValueType.ERROR;
            };
            return new HirUnary(unary.operator(), operand, type);
        }
        if (expression instanceof BinaryExpression binary) {
            HirExpression left = analyzeExpression(binary.left());
            HirExpression right = analyzeExpression(binary.right());
            ValueType type = binaryType(binary.operator(), left.type(), right.type(), binary.span());
            return new HirBinary(left, binary.operator(), right, type);
        }
        AssignmentExpression assignment = (AssignmentExpression) expression;
        Symbol target = symbols.get(assignment.target().name());
        HirExpression value = analyzeExpression(assignment.value());
        if (target == null) {
            error("MPL3102", "未声明的变量：" + assignment.target().name(), assignment.target().span());
            return new HirAssignment(assignment.target().name(), assignment.operator(), value, ValueType.ERROR);
        }
        if (!target.mutable()) {
            error("MPL3104", "不能给 val 重新赋值：" + assignment.target().name(), assignment.target().span());
        }
        if ("=".equals(assignment.operator())) {
            if (!target.type().canAssignFrom(value.type())) {
                error("MPL3103", "不能将 " + display(value.type()) + " 赋给 " + display(target.type()), assignment.value().span());
            }
        } else {
            ValueType result = binaryType(assignment.operator().substring(0, 1), target.type(), value.type(), assignment.span());
            if (!target.type().canAssignFrom(result)) {
                error("MPL3103", "复合赋值结果不能赋给 " + display(target.type()), assignment.span());
            }
        }
        return new HirAssignment(assignment.target().name(), assignment.operator(), value, target.type());
    }

    private ValueType binaryType(String operator, ValueType left, ValueType right, SourceSpan span) {
        return switch (operator) {
            case "+", "-", "*" -> numericResult(left, right, span, "运算符 " + operator);
            case "/" -> {
                requireNumeric(left, span, "运算符 /");
                requireNumeric(right, span, "运算符 /");
                yield left == ValueType.ERROR || right == ValueType.ERROR ? ValueType.ERROR : ValueType.FLOAT;
            }
            case "%" -> left == ValueType.INT && right == ValueType.INT ? ValueType.INT
                : typeError("运算符 % 只接受 Int", span);
            case "<", "<=", ">", ">=" -> {
                requireNumeric(left, span, "比较运算符 " + operator);
                requireNumeric(right, span, "比较运算符 " + operator);
                yield left == ValueType.ERROR || right == ValueType.ERROR ? ValueType.ERROR : ValueType.BOOL;
            }
            case "==", "!=" -> compatibleForEquality(left, right, span) ? ValueType.BOOL : ValueType.ERROR;
            case "&&", "||" -> {
                requireBool(left, span, "逻辑运算符 " + operator);
                requireBool(right, span, "逻辑运算符 " + operator);
                yield left == ValueType.ERROR || right == ValueType.ERROR ? ValueType.ERROR : ValueType.BOOL;
            }
            default -> typeError("暂不支持的运算符：" + operator, span);
        };
    }

    private ValueType numericResult(ValueType left, ValueType right, SourceSpan span, String context) {
        requireNumeric(left, span, context);
        requireNumeric(right, span, context);
        if (left == ValueType.ERROR || right == ValueType.ERROR) return ValueType.ERROR;
        return left == ValueType.FLOAT || right == ValueType.FLOAT ? ValueType.FLOAT : ValueType.INT;
    }

    private ValueType requireNumeric(ValueType type, SourceSpan span, String context) {
        return type == ValueType.INT || type == ValueType.FLOAT || type == ValueType.ERROR ? type
            : typeError(context + " 只接受 Int 或 Float", span);
    }

    private ValueType requireBool(ValueType type, SourceSpan span, String context) {
        return type == ValueType.BOOL || type == ValueType.ERROR ? type
            : typeError(context + " 只接受 Bool", span);
    }

    private boolean compatibleForEquality(ValueType left, ValueType right, SourceSpan span) {
        if (left == ValueType.ERROR || right == ValueType.ERROR || left == right
            || left == ValueType.INT && right == ValueType.FLOAT || left == ValueType.FLOAT && right == ValueType.INT) {
            return true;
        }
        error("MPL3103", "不能比较 " + display(left) + " 与 " + display(right), span);
        return false;
    }

    private ValueType parseType(String name, SourceSpan span) {
        return switch (name) {
            case "Int" -> ValueType.INT;
            case "Float" -> ValueType.FLOAT;
            case "Bool" -> ValueType.BOOL;
            default -> typeError("当前阶段不支持类型：" + name, span);
        };
    }

    private ValueType typeError(String message, SourceSpan span) {
        error("MPL3103", message, span);
        return ValueType.ERROR;
    }

    private void error(String code, String message, SourceSpan span) {
        diagnostics.add(new Diagnostic(Severity.ERROR, code, message, Optional.ofNullable(file), Optional.of(span)));
    }

    private String display(ValueType type) {
        return switch (type) {
            case INT -> "Int";
            case FLOAT -> "Float";
            case BOOL -> "Bool";
            case ERROR -> "错误类型";
        };
    }

    private record Symbol(ValueType type, boolean mutable) {
    }
}
