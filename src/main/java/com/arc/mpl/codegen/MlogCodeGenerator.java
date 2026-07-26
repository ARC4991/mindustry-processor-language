package com.arc.mpl.codegen;

import com.arc.mpl.hir.HirAssignment;
import com.arc.mpl.hir.HirBinary;
import com.arc.mpl.hir.HirConstant;
import com.arc.mpl.hir.HirExpression;
import com.arc.mpl.hir.HirExpressionStatement;
import com.arc.mpl.hir.HirProgram;
import com.arc.mpl.hir.HirPrintStatement;
import com.arc.mpl.hir.HirStatement;
import com.arc.mpl.hir.HirUnary;
import com.arc.mpl.hir.HirText;
import com.arc.mpl.hir.HirVariable;
import com.arc.mpl.hir.HirVariableDeclaration;

import java.util.ArrayList;
import java.util.List;

/** Deterministic baseline mlog emission for the first arithmetic HIR subset. */
public final class MlogCodeGenerator {
    private final List<String> lines = new ArrayList<>();
    private int temporaryIndex;

    public String generate(HirProgram program) {
        lines.clear();
        temporaryIndex = 0;
        for (HirStatement statement : program.statements()) {
            emitStatement(statement);
        }
        return lines.isEmpty() ? "" : String.join("\n", lines) + "\n";
    }

    private void emitStatement(HirStatement statement) {
        if (statement instanceof HirVariableDeclaration declaration) {
            lines.add("set " + variable(declaration.name()) + " " + emitExpression(declaration.initializer()));
            return;
        }
        if (statement instanceof HirPrintStatement print) {
            for (HirExpression argument : print.arguments()) lines.add("print " + emitExpression(argument));
            lines.add("printflush " + print.linkName());
            return;
        }
        emitExpression(((HirExpressionStatement) statement).expression());
    }

    private String emitExpression(HirExpression expression) {
        if (expression instanceof HirConstant constant) return constant.mlogLiteral();
        if (expression instanceof HirText text) return quote(text.value());
        if (expression instanceof HirVariable variable) return variable(variable.name());
        if (expression instanceof HirUnary unary) return emitUnary(unary);
        if (expression instanceof HirBinary binary) return emitBinary(binary);
        return emitAssignment((HirAssignment) expression);
    }

    private String emitUnary(HirUnary unary) {
        String operand = emitExpression(unary.operand());
        if ("+".equals(unary.operator())) return operand;
        String temporary = temporary();
        String operation = switch (unary.operator()) {
            case "-" -> "sub";
            case "!" -> "equal";
            default -> throw new IllegalArgumentException("unsupported unary operator " + unary.operator());
        };
        lines.add("op " + operation + " " + temporary + " 0 " + operand);
        return temporary;
    }

    private String emitBinary(HirBinary binary) {
        String left = emitExpression(binary.left());
        String right = emitExpression(binary.right());
        String temporary = temporary();
        lines.add("op " + operation(binary.operator()) + " " + temporary + " " + left + " " + right);
        return temporary;
    }

    private String emitAssignment(HirAssignment assignment) {
        String target = variable(assignment.target());
        String value = emitExpression(assignment.value());
        if ("=".equals(assignment.operator())) {
            lines.add("set " + target + " " + value);
        } else {
            lines.add("op " + operation(assignment.operator().substring(0, 1)) + " " + target + " " + target + " " + value);
        }
        return target;
    }

    private String operation(String operator) {
        return switch (operator) {
            case "+" -> "add";
            case "-" -> "sub";
            case "*" -> "mul";
            case "/" -> "div";
            case "%" -> "mod";
            case "==" -> "equal";
            case "!=" -> "notEqual";
            case "<" -> "lessThan";
            case "<=" -> "lessThanEq";
            case ">" -> "greaterThan";
            case ">=" -> "greaterThanEq";
            case "&&" -> "land";
            case "||" -> "or";
            default -> throw new IllegalArgumentException("unsupported binary operator " + operator);
        };
    }

    private String variable(String name) {
        return "mpl_" + name;
    }

    private String temporary() {
        return "mpl_tmp" + temporaryIndex++;
    }

    private String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r").replace("\"", "\\\"") + "\"";
    }
}
