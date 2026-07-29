package com.arc.mpl.codegen;

import com.arc.mpl.hir.HirAssignment;
import com.arc.mpl.hir.HirBinary;
import com.arc.mpl.hir.HirBlock;
import com.arc.mpl.hir.HirBuildingControl;
import com.arc.mpl.hir.HirHardwareLink;
import com.arc.mpl.hir.HirConstant;
import com.arc.mpl.hir.HirExpression;
import com.arc.mpl.hir.HirExpressionStatement;
import com.arc.mpl.hir.HirIntrinsicCall;
import com.arc.mpl.hir.HirIf;
import com.arc.mpl.hir.HirMemberAccess;
import com.arc.mpl.hir.HirPrintStatement;
import com.arc.mpl.hir.HirProgram;
import com.arc.mpl.hir.HirStatement;
import com.arc.mpl.hir.HirText;
import com.arc.mpl.hir.HirUnary;
import com.arc.mpl.hir.HirUnitControl;
import com.arc.mpl.hir.HirUnitIteration;
import com.arc.mpl.hir.HirVariable;
import com.arc.mpl.hir.HirVariableDeclaration;
import com.arc.mpl.hir.HirWhile;
import com.arc.mpl.hir.ValueType;

import java.util.List;
import java.util.Objects;

/**
 * Serializes validated HIR as structured macro intermediate language (MIL).
 *
 * <p>MIL is deliberately not a spelling of already-expanded mlog. Ordinary
 * declarations, expressions, and structured control flow remain source-like
 * so that a generated file is useful both for inspection and as the model for
 * the future MIL front end. Only operations that need a target-level binding
 * are lowered to profile-controlled macros: Unit traversal/control/property
 * reads and hardware output.</p>
 */
public final class MilCodeGenerator {
    /**
     * Emits a deterministic, inspectable MIL program.
     *
     * <p>Top-level completion is intentionally not rendered as a source macro.
     * The target emitter appends its mandatory {@code stop} instruction after
     * this structured program has completed, just as it does for MPL.</p>
     */
    public String generate(HirProgram program) {
        Objects.requireNonNull(program, "program");
        Writer writer = new Writer();
        writer.line("// 由 MPL 自动生成的 MIL；请通过 mpl build 重新生成，勿直接编辑。");
        writer.line("// 普通结构保留为 MIL；@unit.* 与 @io.* 是由 target profile 展开的受限宏。");
        for (HirStatement statement : program.statements()) {
            emitStatement(writer, statement);
        }
        return writer.render();
    }

    private void emitStatement(Writer writer, HirStatement statement) {
        if (statement instanceof HirVariableDeclaration declaration) {
            writer.line((declaration.mutable() ? "var " : "val ")
                + identifier(declaration.name(), "变量") + ": " + displayType(declaration.type())
                + " = " + expression(declaration.initializer()) + ";");
            return;
        }
        if (statement instanceof HirExpressionStatement expressionStatement) {
            writer.line(expression(expressionStatement.expression()) + ";");
            return;
        }
        if (statement instanceof HirPrintStatement print) {
            emitPrint(writer, print);
            return;
        }
        if (statement instanceof HirBlock block) {
            emitBlock(writer, block.statements());
            return;
        }
        if (statement instanceof HirWhile loop) {
            writer.append("while (").append(expression(loop.condition())).append(") ");
            emitBlock(writer, loop.body());
            return;
        }
        if (statement instanceof HirIf branch) {
            writer.append("if (").append(expression(branch.condition())).append(") ");
            emitBlock(writer, branch.thenBody());
            if (branch.elseBody().isPresent()) {
                writer.append("else ");
                emitBlock(writer, branch.elseBody().orElseThrow());
            }
            return;
        }
        if (statement instanceof HirUnitIteration iteration) {
            emitUnitIteration(writer, iteration);
            return;
        }
        if (statement instanceof HirUnitControl control) {
            emitUnitControl(writer, control);
            return;
        }
        if (statement instanceof HirBuildingControl control) {
            emitBuildingControl(writer, control);
            return;
        }
        throw new IllegalArgumentException("MIL 尚不能序列化 HIR 语句：" + statement.getClass().getSimpleName());
    }

    private void emitBlock(Writer writer, List<HirStatement> statements) {
        writer.line("{");
        writer.indent();
        for (HirStatement nested : statements) {
            emitStatement(writer, nested);
        }
        writer.unindent();
        writer.line("}");
    }

    /**
     * {@link HirUnitIteration} is the lowering boundary for high-level
     * {@code Unit.getAll...()}, chained {@code where}, and {@code take}.
     * Its macro owns the target's one current-unit binding and, for the
     * managed form, all private flag/runtime bookkeeping.
     */
    private void emitUnitIteration(Writer writer, HirUnitIteration iteration) {
        String macro = iteration.hasManagedLimit() ? "@unit.eachManaged" : "@unit.each";
        writer.append(macro)
            .append("(@")
            .append(identifier(iteration.mlogType(), "单位内容名"))
            .append(", ")
            .append(identifier(iteration.bindingName(), "单位绑定变量"));
        if (iteration.hasManagedLimit()) {
            writer.append(", ").append(Integer.toString(iteration.managedLimit()));
        }
        for (HirExpression filter : iteration.filters()) {
            writer.append(", ").append(expression(filter));
        }
        writer.append(") ");
        emitBlock(writer, iteration.body());
    }

    /** Emits the automatic-flush Message API using its target link alias. */
    private void emitPrint(Writer writer, HirPrintStatement print) {
        writer.append("@io.print(")
            .append(targetLink(print.linkName()));
        for (HirExpression argument : print.arguments()) {
            writer.append(", ").append(expression(argument));
        }
        writer.line(");");
    }

    /** Emits the current first-slice Unit object operation as an explicit macro. */
    private void emitUnitControl(Writer writer, HirUnitControl control) {
        if (!"move".equals(control.command()) || control.arguments().size() != 2) {
            throw new IllegalArgumentException("MIL 尚不能序列化 Unit 操作：" + control.command());
        }
        writer.append("@unit.move(")
            .append(identifier(control.bindingName(), "单位绑定变量"));
        for (HirExpression argument : control.arguments()) {
            writer.append(", ").append(expression(argument));
        }
        writer.line(");");
    }

    /** Keeps building control target-aware in MIL instead of leaking a raw mlog instruction. */
    private void emitBuildingControl(Writer writer, HirBuildingControl control) {
        writer.append("@building.control(")
            .append(targetLink(control.target().gameAlias()))
            .append(", ").append(identifier(control.action(), "建筑控制动作"));
        for (HirExpression argument : control.arguments()) writer.append(", ").append(expression(argument));
        writer.line(");");
    }

    private String expression(HirExpression value) {
        if (value instanceof HirConstant constant) return constant(constant);
        if (value instanceof HirText text) return quote(text.value());
        if (value instanceof HirVariable variable) return identifier(variable.name(), "变量");
        if (value instanceof HirUnary unary) {
            return "(" + unary.operator() + expression(unary.operand()) + ")";
        }
        if (value instanceof HirBinary binary) {
            return "(" + expression(binary.left()) + " " + binary.operator() + " " + expression(binary.right()) + ")";
        }
        if (value instanceof HirAssignment assignment) {
            return identifier(assignment.target(), "赋值目标") + " " + assignment.operator()
                + " " + expression(assignment.value());
        }
        if (value instanceof HirMemberAccess member) return unitMember(member);
        if (value instanceof HirIntrinsicCall call) return intrinsic(call);
        throw new IllegalArgumentException("MIL 尚不能序列化 HIR 表达式：" + value.getClass().getSimpleName());
    }

    /**
     * Unit fields are not copied back into MIL as ordinary object members.
     * The macro whitelist can therefore keep private fields such as flag out
     * of both MPL and hand-authored MIL.
     */
    private String unitMember(HirMemberAccess member) {
        if (member.target() instanceof HirHardwareLink hardware) {
            return "@building.read(" + targetLink(hardware.gameAlias()) + ", "
                + identifier(member.member(), "建筑属性") + ")";
        }
        if (member.target().type() != ValueType.UNIT) {
            return expression(member.target()) + "." + identifier(member.member(), "成员名");
        }
        if ("flag".equals(member.member())) {
            throw new IllegalArgumentException("Unit.flag 是编译器私有运行时属性，不能出现在 MIL");
        }
        String unit = expression(member.target());
        if ("alive".equals(member.member())) {
            return "@unit.alive(" + unit + ")";
        }
        if ("dead".equals(member.member())) {
            return "(!@unit.alive(" + unit + "))";
        }
        return "@unit.read(" + unit + ", " + identifier(member.member(), "Unit 属性") + ")";
    }

    private String intrinsic(HirIntrinsicCall call) {
        String namespace = identifier(call.namespace(), "内建命名空间");
        String name = identifier(call.name(), "内建名称");
        if ("Clock".equals(namespace) && call.arguments().isEmpty()) {
            return namespace + "." + name;
        }
        StringBuilder result = new StringBuilder(namespace).append('.').append(name).append('(');
        appendExpressions(result, call.arguments());
        return result.append(')').toString();
    }

    private String constant(HirConstant constant) {
        if (constant.type() == ValueType.BOOL) {
            return switch (constant.mlogLiteral()) {
                case "1" -> "true";
                case "0" -> "false";
                default -> throw new IllegalArgumentException("Bool 常量不是 0 或 1：" + constant.mlogLiteral());
            };
        }
        return constant.mlogLiteral();
    }

    private void appendExpressions(StringBuilder target, List<HirExpression> arguments) {
        for (int index = 0; index < arguments.size(); index++) {
            if (index > 0) target.append(", ");
            target.append(expression(arguments.get(index)));
        }
    }

    private String displayType(ValueType type) {
        return switch (type) {
            case INT -> "Int";
            case FLOAT -> "Float";
            case BOOL -> "Bool";
            case STRING -> "String";
            case UNIT -> "Unit";
            case BUILDING -> "Building";
            case ERROR -> throw new IllegalArgumentException("不能将含错误类型的 HIR 序列化为 MIL");
        };
    }

    /** Prefixes game-owned hardware aliases so they cannot look like MPL variables. */
    private String targetLink(String linkName) {
        String bareName = linkName.startsWith("@") ? linkName.substring(1) : linkName;
        return "@" + identifier(bareName, "硬件链接名");
    }

    private String identifier(String value, String role) {
        if (!value.matches("[_A-Za-z][_A-Za-z0-9]*")) {
            throw new IllegalArgumentException(role + "不是有效的 MIL 标识符：" + value);
        }
        return value;
    }

    private String quote(String value) {
        return "\"" + value.replace("\\", "\\\\")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
            .replace("\"", "\\\"") + "\"";
    }

    /** Small indentation-aware writer used to keep generated MIL deterministic. */
    private static final class Writer {
        private final StringBuilder text = new StringBuilder();
        private int indentation;
        private boolean lineOpen;

        Writer append(String value) {
            if (!lineOpen) writeIndentation();
            text.append(value);
            lineOpen = true;
            return this;
        }

        void line(String value) {
            append(value);
            text.append('\n');
            lineOpen = false;
        }

        void indent() {
            if (lineOpen) throw new IllegalStateException("不能在未结束的 MIL 行内增加缩进");
            indentation++;
        }

        void unindent() {
            if (lineOpen) throw new IllegalStateException("不能在未结束的 MIL 行内减少缩进");
            if (indentation == 0) throw new IllegalStateException("MIL 缩进已经在最外层");
            indentation--;
        }

        String render() {
            if (lineOpen) text.append('\n');
            return text.toString();
        }

        private void writeIndentation() {
            text.append("    ".repeat(indentation));
        }
    }
}
