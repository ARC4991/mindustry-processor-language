package com.arc.mpl.codegen;

import com.arc.mpl.hir.HirAssignment;
import com.arc.mpl.hir.HirBinary;
import com.arc.mpl.hir.HirConstant;
import com.arc.mpl.hir.HirExpression;
import com.arc.mpl.hir.HirExpressionStatement;
import com.arc.mpl.hir.HirBlock;
import com.arc.mpl.hir.HirBuildingControl;
import com.arc.mpl.hir.HirHardwareLink;
import com.arc.mpl.hir.HirIntrinsicCall;
import com.arc.mpl.hir.HirIf;
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

import java.util.List;

import com.arc.mpl.codegen.MlogProgramBuilder.JumpCondition;
import com.arc.mpl.codegen.MlogProgramBuilder.Operation;
import com.arc.mpl.codegen.MlogProgramBuilder.UnitControlCommand;

/** Deterministic baseline mlog emission for the first arithmetic HIR subset. */
public final class MlogCodeGenerator {
    private final MlogLabelStyle labelStyle;
    private MlogProgramBuilder output;
    private int temporaryIndex;
    private int unitIterationIndex;

    /** Creates a compact release emitter, suitable for deployment to a processor. */
    public MlogCodeGenerator() {
        this(MlogLabelStyle.RELEASE);
    }

    /** Creates an emitter with an explicit jump-label spelling policy. */
    public MlogCodeGenerator(MlogLabelStyle labelStyle) {
        this.labelStyle = java.util.Objects.requireNonNull(labelStyle, "labelStyle");
    }

    public String generate(HirProgram program) {
        output = new MlogProgramBuilder(labelStyle);
        temporaryIndex = 0;
        unitIterationIndex = 0;
        for (HirStatement statement : program.statements()) {
            emitStatement(statement);
        }
        // MPL 程序默认只执行一次；持续逻辑必须由用户显式写 while。
        output.stop();
        return output.render();
    }

    private void emitStatement(HirStatement statement) {
        if (statement instanceof HirVariableDeclaration declaration) {
            output.set(variable(declaration.name()), emitExpression(declaration.initializer()));
            return;
        }
        if (statement instanceof HirPrintStatement print) {
            for (HirExpression argument : print.arguments()) output.print(emitExpression(argument));
            output.printFlush(print.linkName());
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
        if (statement instanceof HirIf branch) {
            emitIf(branch);
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
        if (statement instanceof HirBuildingControl control) {
            emitBuildingControl(control);
            return;
        }
        emitExpression(((HirExpressionStatement) statement).expression());
    }

    private void emitWhile(HirWhile loop) {
        MlogProgramBuilder.Label start = label("while_start");
        MlogProgramBuilder.Label end = label("while_end");
        emitLabel(start);
        String condition = emitExpression(loop.condition());
        emitJump(end, JumpCondition.EQUAL, condition, "0");
        for (HirStatement statement : loop.body()) emitStatement(statement);
        emitJump(start, JumpCondition.ALWAYS, "0", "0");
        emitLabel(end);
    }

    private void emitIf(HirIf branch) {
        MlogProgramBuilder.Label otherwise = label("if_else");
        MlogProgramBuilder.Label end = label("if_end");
        String condition = emitExpression(branch.condition());
        emitJump(branch.elseBody().isPresent() ? otherwise : end, JumpCondition.EQUAL, condition, "0");
        for (HirStatement statement : branch.thenBody()) emitStatement(statement);
        if (branch.elseBody().isEmpty()) {
            emitLabel(end);
            return;
        }
        emitJump(end, JumpCondition.ALWAYS, "0", "0");
        emitLabel(otherwise);
        for (HirStatement statement : branch.elseBody().orElseThrow()) emitStatement(statement);
        emitLabel(end);
    }

    private void emitUnitIteration(HirUnitIteration iteration) {
        if (iteration.hasManagedLimit()) {
            emitManagedUnitIteration(iteration);
        } else {
            emitDirectUnitIteration(iteration);
        }
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
    private void emitDirectUnitIteration(HirUnitIteration iteration) {
        int iterationId = unitIterationIndex++;
        MlogProgramBuilder.Label scan = label("unit_scan");
        MlogProgramBuilder.Label next = label("unit_next");
        MlogProgramBuilder.Label end = label("unit_end");
        // Source variables are prefixed with mpl_. Keep compiler temporaries outside
        // that namespace so a perfectly ordinary user variable such as unit_sentinel0
        // can never overwrite the traversal state.
        String sentinel = "__mpl_unit_sentinel" + iterationId;

        // UnitBindI writes null when there is no controllable unit of this type.
        output.unitBind("@" + iteration.mlogType());
        emitJump(end, JumpCondition.STRICT_EQUAL, "@unit", "null");
        output.set(sentinel, "@unit");

        emitLabel(scan);
        // Separate where calls are short-circuited in source order.
        for (HirExpression filter : iteration.filters()) {
            String accepted = emitExpression(filter);
            emitJump(next, JumpCondition.EQUAL, accepted, "0");
        }
        for (HirStatement statement : iteration.body()) emitStatement(statement);

        emitLabel(next);
        // Rebind by object rather than type: this both validates the sentinel and
        // preserves the type carousel position established by the preceding ubind.
        output.unitBind(sentinel);
        emitJump(end, JumpCondition.STRICT_EQUAL, "@unit", "null");
        String sentinelDead = temporary();
        output.sensor(sentinelDead, "@unit", "@dead");
        emitJump(end, JumpCondition.EQUAL, sentinelDead, "1");

        output.unitBind("@" + iteration.mlogType());
        emitJump(end, JumpCondition.STRICT_EQUAL, "@unit", "null");
        emitJump(end, JumpCondition.STRICT_EQUAL, "@unit", sentinel);
        emitJump(scan, JumpCondition.ALWAYS, "0", "0");
        emitLabel(end);
    }

    /**
     * Emits the private runtime behind {@code UnitSet.take(n)}.
     *
     * <p>v146 only exposes a per-type {@code ubind} carousel, so taking the
     * first {@code n} candidates on every pass would eventually command every
     * unit as that carousel rotates. Instead, the generated program owns a
     * small stable subset using the target's {@code Unit.flag} field. The
     * field is strictly compiler-private: source MPL and MIL have no access
     * to it.</p>
     *
     * <p>The first scan reconciles already-owned units before considering new
     * candidates. The second scan fills any remaining capacity and runs the
     * user body only for units carrying this traversal's owner token and
     * currently controlled by this processor. This ordering keeps an existing
     * group stable even when the target's bind cursor begins at a different
     * candidate on a later tick.</p>
     */
    private void emitManagedUnitIteration(HirUnitIteration iteration) {
        int iterationId = unitIterationIndex++;
        MlogProgramBuilder.Label reconcileScan = label("managed_reconcile_scan");
        MlogProgramBuilder.Label reconcileTagged = label("managed_reconcile_tagged");
        MlogProgramBuilder.Label reconcileOwned = label("managed_reconcile_owned");
        MlogProgramBuilder.Label reconcileReject = label("managed_reconcile_reject");
        MlogProgramBuilder.Label reconcileExcess = label("managed_reconcile_excess");
        MlogProgramBuilder.Label reconcileNext = label("managed_reconcile_next");
        MlogProgramBuilder.Label reconcileEnd = label("managed_reconcile_end");
        MlogProgramBuilder.Label scan = label("managed_unit_scan");
        MlogProgramBuilder.Label owned = label("managed_unit_owned");
        MlogProgramBuilder.Label unclaimed = label("managed_unit_unclaimed");
        MlogProgramBuilder.Label claimedTagged = label("managed_unit_claimed_tagged");
        MlogProgramBuilder.Label ownedConfirmed = label("managed_unit_owned_confirmed");
        MlogProgramBuilder.Label claimed = label("managed_unit_claimed");
        MlogProgramBuilder.Label body = label("managed_unit_body");
        MlogProgramBuilder.Label next = label("managed_unit_next");
        MlogProgramBuilder.Label end = label("managed_unit_end");
        String sentinel = "__mpl_managed_sentinel" + iterationId;
        String owner = "__mpl_managed_owner" + iterationId;
        String retained = "__mpl_managed_count" + iterationId;
        String limit = Integer.toString(iteration.managedLimit());

        emitManagedOwnerToken(owner, iterationId);
        output.set(retained, "0");

        // Phase A: preserve qualifying units already owned by this traversal
        // before filling the remaining capacity. A stale excess tag is released
        // so lowering remains correct after reducing take(n) and recompiling.
        output.unitBind("@" + iteration.mlogType());
        emitJump(reconcileEnd, JumpCondition.STRICT_EQUAL, "@unit", "null");
        output.set(sentinel, "@unit");
        emitLabel(reconcileScan);
        String currentFlag = temporary();
        output.sensor(currentFlag, "@unit", "@flag");
        emitJump(reconcileTagged, JumpCondition.STRICT_EQUAL, currentFlag, owner);
        emitJump(reconcileNext, JumpCondition.ALWAYS, "0", "0");

        emitLabel(reconcileTagged);
        String currentController = temporary();
        output.sensor(currentController, "@unit", "@controller");
        emitJump(reconcileOwned, JumpCondition.STRICT_EQUAL, currentController, "@this");
        emitJump(reconcileNext, JumpCondition.ALWAYS, "0", "0");

        emitLabel(reconcileOwned);
        emitFilterRejectionJump(iteration.filters(), reconcileReject);
        output.operation(Operation.ADD, retained, retained, "1");
        emitJump(reconcileNext, JumpCondition.LESS_THAN_EQ, retained, limit);

        emitLabel(reconcileExcess);
        output.unitControl(UnitControlCommand.FLAG, "0", "0", "0", "0", "0");
        output.operation(Operation.SUB, retained, retained, "1");
        emitJump(reconcileNext, JumpCondition.ALWAYS, "0", "0");

        emitLabel(reconcileReject);
        output.unitControl(UnitControlCommand.FLAG, "0", "0", "0", "0", "0");
        emitJump(reconcileNext, JumpCondition.ALWAYS, "0", "0");

        emitLabel(reconcileNext);
        emitUnitScanAdvance(sentinel, iteration.mlogType(), reconcileScan, reconcileEnd);
        emitLabel(reconcileEnd);

        // Phase B: only current owner-tagged units execute the MPL loop body.
        // Unflagged, qualifying candidates fill an empty slot; any foreign
        // non-zero flag is left untouched.
        output.unitBind("@" + iteration.mlogType());
        emitJump(end, JumpCondition.STRICT_EQUAL, "@unit", "null");
        output.set(sentinel, "@unit");
        emitLabel(scan);
        String candidateFlag = temporary();
        output.sensor(candidateFlag, "@unit", "@flag");
        emitJump(owned, JumpCondition.STRICT_EQUAL, candidateFlag, owner);
        emitJump(unclaimed, JumpCondition.STRICT_EQUAL, candidateFlag, "0");
        emitJump(next, JumpCondition.ALWAYS, "0", "0");
        emitLabel(unclaimed);
        emitJump(next, JumpCondition.GREATER_THAN_EQ, retained, limit);
        String candidateControl = temporary();
        output.sensor(candidateControl, "@unit", "@controlled");
        emitJump(next, JumpCondition.NOT_EQUAL, candidateControl, "0");
        emitFilterRejectionJump(iteration.filters(), next);
        output.unitControl(UnitControlCommand.FLAG, owner, "0", "0", "0", "0");
        String claimedFlag = temporary();
        output.sensor(claimedFlag, "@unit", "@flag");
        emitJump(claimedTagged, JumpCondition.STRICT_EQUAL, claimedFlag, owner);
        emitJump(next, JumpCondition.ALWAYS, "0", "0");
        emitLabel(claimedTagged);
        String claimedController = temporary();
        output.sensor(claimedController, "@unit", "@controller");
        emitJump(claimed, JumpCondition.STRICT_EQUAL, claimedController, "@this");
        emitJump(next, JumpCondition.ALWAYS, "0", "0");
        emitLabel(claimed);
        output.operation(Operation.ADD, retained, retained, "1");
        emitJump(body, JumpCondition.ALWAYS, "0", "0");

        emitLabel(owned);
        String ownedController = temporary();
        output.sensor(ownedController, "@unit", "@controller");
        emitJump(ownedConfirmed, JumpCondition.STRICT_EQUAL, ownedController, "@this");
        emitJump(next, JumpCondition.ALWAYS, "0", "0");
        emitLabel(ownedConfirmed);
        emitFilterRejectionJump(iteration.filters(), next);
        emitLabel(body);
        for (HirStatement statement : iteration.body()) emitStatement(statement);

        emitLabel(next);
        emitUnitScanAdvance(sentinel, iteration.mlogType(), scan, end);
        emitLabel(end);
    }

    /** Builds a stable, numeric owner token without exposing target internals to MPL. */
    private void emitManagedOwnerToken(String owner, int iterationId) {
        // @thisx/@thisy can be half-tile coordinates for some processor sizes.
        // Doubling them produces a unique integer cell coordinate. @mapw makes
        // the two-dimensional coordinate injective within the current map; 4096
        // leaves room for distinct managed traversal sites in one program.
        String twiceX = temporary();
        String twiceY = temporary();
        String stride = temporary();
        String row = temporary();
        String cell = temporary();
        output.operation(Operation.MUL, twiceX, "@thisx", "2");
        output.operation(Operation.MUL, twiceY, "@thisy", "2");
        output.operation(Operation.MUL, stride, "@mapw", "2");
        output.operation(Operation.MUL, row, twiceY, stride);
        output.operation(Operation.ADD, cell, twiceX, row);
        output.operation(Operation.MUL, owner, cell, "4096");
        output.operation(Operation.ADD, owner, owner, "4000000000");
        output.operation(Operation.ADD, owner, owner, Integer.toString(iterationId));
    }

    private void emitFilterRejectionJump(List<HirExpression> filters, MlogProgramBuilder.Label reject) {
        for (HirExpression filter : filters) {
            String accepted = emitExpression(filter);
            emitJump(reject, JumpCondition.EQUAL, accepted, "0");
        }
    }

    /** Advances a v146 {@code ubind} carousel while safely detecting wraparound/removal. */
    private void emitUnitScanAdvance(String sentinel, String mlogType, MlogProgramBuilder.Label scan,
                                     MlogProgramBuilder.Label end) {
        output.unitBind(sentinel);
        emitJump(end, JumpCondition.STRICT_EQUAL, "@unit", "null");
        String sentinelDead = temporary();
        output.sensor(sentinelDead, "@unit", "@dead");
        emitJump(end, JumpCondition.EQUAL, sentinelDead, "1");
        output.unitBind("@" + mlogType);
        emitJump(end, JumpCondition.STRICT_EQUAL, "@unit", "null");
        emitJump(end, JumpCondition.STRICT_EQUAL, "@unit", sentinel);
        emitJump(scan, JumpCondition.ALWAYS, "0", "0");
    }

    private void emitUnitControl(HirUnitControl control) {
        if (!"move".equals(control.command()) || control.arguments().size() != 2) {
            throw new IllegalArgumentException("unsupported Unit control command: " + control.command());
        }
        String x = emitExpression(control.arguments().get(0));
        String y = emitExpression(control.arguments().get(1));
        // v146 serializes all five ucontrol parameter slots even though move consumes x/y.
        output.unitControl(UnitControlCommand.MOVE, x, y, "0", "0", "0");
    }

    private void emitBuildingControl(HirBuildingControl control) {
        List<String> arguments = control.arguments().stream().map(this::emitExpression).toList();
        output.buildingControl(control.target().gameAlias(), control.action(), arguments);
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
        if (member.target() instanceof HirHardwareLink hardware) {
            String result = temporary();
            output.sensor(result, hardware.gameAlias(), "@" + member.member());
            return result;
        }
        if (!(member.target() instanceof HirVariable variable) || variable.type() != com.arc.mpl.hir.ValueType.UNIT) {
            throw new IllegalArgumentException("unsupported member access target: " + member.target());
        }

        String result = temporary();
        String sensor = "alive".equals(member.member()) ? "dead" : member.member();
        output.sensor(result, "@unit", "@" + sensor);
        if ("alive".equals(member.member())) {
            String alive = temporary();
            output.operation(Operation.EQUAL, alive, result, "0");
            return alive;
        }
        return result;
    }

    private String emitIntrinsicCall(HirIntrinsicCall call) {
        if ("Clock".equals(call.namespace())) {
            if (!call.arguments().isEmpty()) {
                throw new IllegalArgumentException("Clock intrinsic does not accept arguments: " + call.name());
            }
            return switch (call.name()) {
                case "timeMs" -> "@time";
                case "time" -> emitScaledTime("1000.0");
                case "timeMinutes" -> emitScaledTime("60000.0");
                case "timeHours" -> emitScaledTime("3600000.0");
                case "tick" -> "@tick";
                default -> throw new IllegalArgumentException("unsupported Clock intrinsic: " + call.name());
            };
        }
        if ("Math".equals(call.namespace()) && ("sin".equals(call.name()) || "cos".equals(call.name()))
            && call.arguments().size() == 1) {
            String result = temporary();
            Operation operation = "sin".equals(call.name()) ? Operation.SIN : Operation.COS;
            output.operation(operation, result, emitExpression(call.arguments().get(0)), "0");
            return result;
        }
        throw new IllegalArgumentException("unsupported intrinsic: " + call.namespace() + "." + call.name());
    }

    /** Converts the target's millisecond clock to a derived floating-point unit. */
    private String emitScaledTime(String millisecondsPerUnit) {
        String result = temporary();
        output.operation(Operation.DIV, result, "@time", millisecondsPerUnit);
        return result;
    }

    private String emitUnary(HirUnary unary) {
        String operand = emitExpression(unary.operand());
        if ("+".equals(unary.operator())) return operand;
        String temporary = temporary();
        Operation operation = switch (unary.operator()) {
            case "-" -> Operation.SUB;
            case "!" -> Operation.EQUAL;
            default -> throw new IllegalArgumentException("unsupported unary operator " + unary.operator());
        };
        output.operation(operation, temporary, "0", operand);
        return temporary;
    }

    private String emitBinary(HirBinary binary) {
        String left = emitExpression(binary.left());
        String right = emitExpression(binary.right());
        String temporary = temporary();
        output.operation(operation(binary.operator()), temporary, left, right);
        return temporary;
    }

    private String emitAssignment(HirAssignment assignment) {
        String target = variable(assignment.target());
        String value = emitExpression(assignment.value());
        if ("=".equals(assignment.operator())) {
            output.set(target, value);
        } else {
            output.operation(operation(assignment.operator().substring(0, 1)), target, target, value);
        }
        return target;
    }

    private Operation operation(String operator) {
        return switch (operator) {
            case "+" -> Operation.ADD;
            case "-" -> Operation.SUB;
            case "*" -> Operation.MUL;
            case "/" -> Operation.DIV;
            case "%" -> Operation.MOD;
            case "==" -> Operation.EQUAL;
            case "!=" -> Operation.NOT_EQUAL;
            case "<" -> Operation.LESS_THAN;
            case "<=" -> Operation.LESS_THAN_EQ;
            case ">" -> Operation.GREATER_THAN;
            case ">=" -> Operation.GREATER_THAN_EQ;
            case "&&" -> Operation.LAND;
            case "||" -> Operation.OR;
            default -> throw new IllegalArgumentException("unsupported binary operator " + operator);
        };
    }

    private String variable(String name) {
        return "mpl_" + name;
    }

    private String temporary() {
        return "__mpl_tmp" + temporaryIndex++;
    }

    private MlogProgramBuilder.Label label(String role) {
        return output.newLabel(role);
    }

    private void emitLabel(MlogProgramBuilder.Label label) {
        output.label(label);
    }

    private void emitJump(MlogProgramBuilder.Label target, JumpCondition condition, String value, String compare) {
        output.jump(target, condition, value, compare);
    }

    private String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r").replace("\"", "\\\"") + "\"";
    }
}
