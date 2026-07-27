package com.arc.mpl.codegen;

import com.arc.mpl.hir.HirAssignment;
import com.arc.mpl.hir.HirBinary;
import com.arc.mpl.hir.HirConstant;
import com.arc.mpl.hir.HirExpression;
import com.arc.mpl.hir.HirExpressionStatement;
import com.arc.mpl.hir.HirBlock;
import com.arc.mpl.hir.HirIntrinsicCall;
import com.arc.mpl.hir.HirMemberAccess;
import com.arc.mpl.hir.HirProgram;
import com.arc.mpl.hir.HirPrintStatement;
import com.arc.mpl.hir.HirStatement;
import com.arc.mpl.hir.HirUnary;
import com.arc.mpl.hir.HirText;
import com.arc.mpl.hir.HirUnitControl;
import com.arc.mpl.hir.HirUnitIteration;
import com.arc.mpl.hir.HirVariable;
import com.arc.mpl.hir.HirVariableDeclaration;
import com.arc.mpl.hir.HirWhile;

import java.util.ArrayList;
import java.util.List;

/** Deterministic baseline mlog emission for the first arithmetic HIR subset. */
public final class MlogCodeGenerator {
    private final List<String> lines = new ArrayList<>();
    private int temporaryIndex;
    private int labelIndex;
    private int unitIterationIndex;

    public String generate(HirProgram program) {
        lines.clear();
        temporaryIndex = 0;
        labelIndex = 0;
        unitIterationIndex = 0;
        for (HirStatement statement : program.statements()) {
            emitStatement(statement);
        }
        // MPL 程序默认只执行一次；持续逻辑必须由用户显式写 while。
        lines.add("stop");
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
        if (statement instanceof HirBlock block) {
            for (HirStatement nested : block.statements()) emitStatement(nested);
            return;
        }
        if (statement instanceof HirWhile loop) {
            emitWhile(loop);
            return;
        }
        if (statement instanceof HirUnitIteration iteration) {
            emitUnitIteration(iteration);
            return;
        }
        if (statement instanceof HirUnitControl control) {
            emitUnitControl(control);
            return;
        }
        emitExpression(((HirExpressionStatement) statement).expression());
    }

    private void emitWhile(HirWhile loop) {
        String start = label("while_start");
        String end = label("while_end");
        emitLabel(start);
        String condition = emitExpression(loop.condition());
        emitJump(end, "equal", condition, "0");
        for (HirStatement statement : loop.body()) emitStatement(statement);
        emitJump(start, "always", "0", "0");
        emitLabel(end);
    }

    /**
     * Traverses the v146 per-type {@code ubind} carousel exactly once.
     *
     * <p>The first bound unit is retained as an object-valued sentinel. Before asking
     * {@code ubind @type} for the next candidate, the sentinel is rebound and checked
     * for removal. This avoids a non-terminating loop when the carousel wraps, while
     * also avoiding the unsafe assumption that a dead/removed unit reference remains
     * usable for the rest of the scan.</p>
     */
    private void emitUnitIteration(HirUnitIteration iteration) {
        int iterationId = unitIterationIndex++;
        String scan = label("unit_scan");
        String next = label("unit_next");
        String end = label("unit_end");
        // Source variables are prefixed with mpl_. Keep compiler temporaries outside
        // that namespace so a perfectly ordinary user variable such as unit_sentinel0
        // can never overwrite the traversal state.
        String sentinel = "__mpl_unit_sentinel" + iterationId;

        // UnitBindI writes null when there is no controllable unit of this type.
        lines.add("ubind @" + iteration.mlogType());
        emitJump(end, "strictEqual", "@unit", "null");
        lines.add("set " + sentinel + " @unit");

        emitLabel(scan);
        // Separate where calls are short-circuited in source order.
        for (HirExpression filter : iteration.filters()) {
            String accepted = emitExpression(filter);
            emitJump(next, "equal", accepted, "0");
        }
        for (HirStatement statement : iteration.body()) emitStatement(statement);

        emitLabel(next);
        // Rebind by object rather than type: this both validates the sentinel and
        // preserves the type carousel position established by the preceding ubind.
        lines.add("ubind " + sentinel);
        emitJump(end, "strictEqual", "@unit", "null");
        String sentinelDead = temporary();
        lines.add("sensor " + sentinelDead + " @unit @dead");
        emitJump(end, "equal", sentinelDead, "1");

        lines.add("ubind @" + iteration.mlogType());
        emitJump(end, "strictEqual", "@unit", "null");
        emitJump(end, "strictEqual", "@unit", sentinel);
        emitJump(scan, "always", "0", "0");
        emitLabel(end);
    }

    private void emitUnitControl(HirUnitControl control) {
        if (!"move".equals(control.command()) || control.arguments().size() != 2) {
            throw new IllegalArgumentException("unsupported Unit control command: " + control.command());
        }
        String x = emitExpression(control.arguments().get(0));
        String y = emitExpression(control.arguments().get(1));
        // v146 serializes all five ucontrol parameter slots even though move consumes x/y.
        lines.add("ucontrol move " + x + " " + y + " 0 0 0");
    }

    private String emitExpression(HirExpression expression) {
        if (expression instanceof HirConstant constant) return constant.mlogLiteral();
        if (expression instanceof HirText text) return quote(text.value());
        if (expression instanceof HirVariable variable) return variable(variable.name());
        if (expression instanceof HirUnary unary) return emitUnary(unary);
        if (expression instanceof HirBinary binary) return emitBinary(binary);
        if (expression instanceof HirMemberAccess member) return emitMemberAccess(member);
        if (expression instanceof HirIntrinsicCall call) return emitIntrinsicCall(call);
        return emitAssignment((HirAssignment) expression);
    }

    private String emitMemberAccess(HirMemberAccess member) {
        if (!(member.target() instanceof HirVariable variable) || variable.type() != com.arc.mpl.hir.ValueType.UNIT) {
            throw new IllegalArgumentException("unsupported member access target: " + member.target());
        }

        String result = temporary();
        String sensor = "alive".equals(member.member()) ? "dead" : member.member();
        lines.add("sensor " + result + " @unit @" + sensor);
        if ("alive".equals(member.member())) {
            String alive = temporary();
            lines.add("op equal " + alive + " " + result + " 0");
            return alive;
        }
        return result;
    }

    private String emitIntrinsicCall(HirIntrinsicCall call) {
        if ("Clock".equals(call.namespace())) {
            return switch (call.name()) {
                case "time" -> "@time";
                case "tick" -> "@tick";
                default -> throw new IllegalArgumentException("unsupported Clock intrinsic: " + call.name());
            };
        }
        if ("Math".equals(call.namespace()) && ("sin".equals(call.name()) || "cos".equals(call.name()))
            && call.arguments().size() == 1) {
            String result = temporary();
            lines.add("op " + call.name() + " " + result + " " + emitExpression(call.arguments().get(0)) + " 0");
            return result;
        }
        throw new IllegalArgumentException("unsupported intrinsic: " + call.namespace() + "." + call.name());
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
        return "__mpl_tmp" + temporaryIndex++;
    }

    private String label(String role) {
        return "mpl_" + role + "_" + labelIndex++;
    }

    private void emitLabel(String label) {
        lines.add(label + ":");
    }

    private void emitJump(String target, String condition, String value, String compare) {
        lines.add("jump " + target + " " + condition + " " + value + " " + compare);
    }

    private String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r").replace("\"", "\\\"") + "\"";
    }
}
