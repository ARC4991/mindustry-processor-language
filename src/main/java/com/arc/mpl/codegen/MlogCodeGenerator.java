package com.arc.mpl.codegen;

import com.arc.mpl.hir.HirAssignment;
import com.arc.mpl.hir.HirAggregateIteration;
import com.arc.mpl.hir.HirArrayLiteral;
import com.arc.mpl.hir.HirBinary;
import com.arc.mpl.hir.HirConstant;
import com.arc.mpl.hir.HirExpression;
import com.arc.mpl.hir.HirExpressionStatement;
import com.arc.mpl.hir.HirFor;
import com.arc.mpl.hir.HirFunction;
import com.arc.mpl.hir.HirFunctionCall;
import com.arc.mpl.hir.HirBlock;
import com.arc.mpl.hir.HirBuildingControl;
import com.arc.mpl.hir.HirBuildingIteration;
import com.arc.mpl.hir.HirBreak;
import com.arc.mpl.hir.HirCollectionContains;
import com.arc.mpl.hir.HirCollectionLiteral;
import com.arc.mpl.hir.HirCollectionSet;
import com.arc.mpl.hir.HirContinue;
import com.arc.mpl.hir.HirDoWhile;
import com.arc.mpl.hir.HirDraw;
import com.arc.mpl.hir.HirDrawFlush;
import com.arc.mpl.hir.HirHardwareLink;
import com.arc.mpl.hir.HirIntrinsicCall;
import com.arc.mpl.hir.HirIndexAccess;
import com.arc.mpl.hir.HirIf;
import com.arc.mpl.hir.HirMemberAccess;
import com.arc.mpl.hir.HirProgram;
import com.arc.mpl.hir.HirReturn;
import com.arc.mpl.hir.HirPrintStatement;
import com.arc.mpl.hir.HirStatement;
import com.arc.mpl.hir.HirUnary;
import com.arc.mpl.hir.HirText;
import com.arc.mpl.hir.HirTupleLiteral;
import com.arc.mpl.hir.HirUnitControl;
import com.arc.mpl.hir.HirUnitIteration;
import com.arc.mpl.hir.HirVariable;
import com.arc.mpl.hir.HirVariableDeclaration;
import com.arc.mpl.hir.HirWhile;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.arc.mpl.codegen.MlogProgramBuilder.JumpCondition;
import com.arc.mpl.codegen.MlogProgramBuilder.Operation;
import com.arc.mpl.codegen.MlogProgramBuilder.UnitControlCommand;

/** Deterministic baseline mlog emission for the first arithmetic HIR subset. */
public final class MlogCodeGenerator {
    private final MlogLabelStyle labelStyle;
    private MlogProgramBuilder output;
    private int temporaryIndex;
    private int unitIterationIndex;
    private Deque<LoopTarget> loopTargets;
    private Map<String, MlogProgramBuilder.Label> functionEntries;
    private Map<String, HirFunction> functions;
    private Map<String, Integer> functionIndexes;
    private Set<String> globalVariables;
    private Map<String, HirHardwareLink> activeBuildingBindings;
    private String activeDrawTarget;
    private String currentFunction;

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
        loopTargets = new ArrayDeque<>();
        functionEntries = new HashMap<>();
        functions = program.functions().stream().collect(java.util.stream.Collectors.toMap(
            HirFunction::name, value -> value, (left, right) -> left, java.util.LinkedHashMap::new));
        functionIndexes = new HashMap<>();
        for (int index = 0; index < program.functions().size(); index++) {
            functionIndexes.put(program.functions().get(index).name(), index);
        }
        globalVariables = new HashSet<>();
        activeBuildingBindings = new HashMap<>();
        activeDrawTarget = null;
        for (HirStatement statement : program.statements()) {
            if (statement instanceof HirVariableDeclaration declaration) globalVariables.add(declaration.name());
        }
        for (HirFunction function : program.functions()) {
            functionEntries.put(function.name(), label("function_" + function.name()));
        }
        currentFunction = null;
        for (HirStatement statement : program.statements()) {
            emitStatement(statement);
        }
        flushPendingDraw();
        // MPL 程序默认只执行一次；持续逻辑必须由用户显式写 while。
        output.stop();
        for (HirFunction function : program.functions()) emitFunction(function);
        return output.render();
    }

    private void emitStatement(HirStatement statement) {
        if (statement instanceof HirVariableDeclaration declaration) {
            if (declaration.initializer() instanceof HirArrayLiteral array) {
                emitAggregateDeclaration(declaration.name(), array.elements());
                return;
            }
            if (declaration.initializer() instanceof HirTupleLiteral tuple) {
                emitAggregateDeclaration(declaration.name(), tuple.elements());
                return;
            }
            if (declaration.initializer() instanceof HirCollectionLiteral collection) {
                emitAggregateDeclaration(declaration.name(), collection.elements());
                return;
            }
            output.set(variable(declaration.name()), emitExpression(declaration.initializer()));
            return;
        }
        if (statement instanceof HirPrintStatement print) {
            for (HirExpression argument : print.arguments()) output.print(emitExpression(argument));
            output.printFlush(print.linkName());
            return;
        }
        if (statement instanceof HirDraw draw) {
            emitDraw(draw);
            return;
        }
        if (statement instanceof HirDrawFlush flush) {
            flushDrawBuffer(flush.displayName());
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
        if (statement instanceof HirDoWhile loop) {
            emitDoWhile(loop);
            return;
        }
        if (statement instanceof HirFor loop) {
            emitFor(loop);
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
        if (statement instanceof HirAggregateIteration iteration) {
            emitAggregateIteration(iteration);
            return;
        }
        if (statement instanceof HirBuildingIteration iteration) {
            emitBuildingIteration(iteration);
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
        if (statement instanceof HirCollectionSet update) {
            output.set(aggregateSlot(update.target(), update.index()), emitExpression(update.value()));
            return;
        }
        if (statement instanceof HirBreak) {
            emitLoopJump(true);
            return;
        }
        if (statement instanceof HirContinue) {
            emitLoopJump(false);
            return;
        }
        if (statement instanceof HirReturn returned) {
            emitReturn(returned);
            return;
        }
        emitExpression(((HirExpressionStatement) statement).expression());
    }

    private void emitFunction(HirFunction function) {
        currentFunction = function.name();
        emitLabel(functionEntries.get(function.name()));
        for (HirStatement statement : function.body()) emitStatement(statement);
        if (function.returnType() == com.arc.mpl.hir.ValueType.VOID) {
            output.set("@counter", functionReturnSlot(function.name()));
        }
        currentFunction = null;
    }

    private void emitReturn(HirReturn returned) {
        if (currentFunction == null) throw new IllegalStateException("return 缺少函数上下文");
        returned.value().ifPresent(value -> output.set(functionResultSlot(currentFunction), emitExpression(value)));
        output.set("@counter", functionReturnSlot(currentFunction));
    }

    private void emitWhile(HirWhile loop) {
        MlogProgramBuilder.Label start = label("while_start");
        MlogProgramBuilder.Label end = label("while_end");
        emitLabel(start);
        String condition = emitExpression(loop.condition());
        emitJump(end, JumpCondition.EQUAL, condition, "0");
        emitLoopBody(loop.body(), start, end);
        emitJump(start, JumpCondition.ALWAYS, "0", "0");
        emitLabel(end);
    }

    private void emitDoWhile(HirDoWhile loop) {
        MlogProgramBuilder.Label start = label("do_start");
        MlogProgramBuilder.Label condition = label("do_condition");
        MlogProgramBuilder.Label end = label("do_end");
        emitLabel(start);
        emitLoopBody(loop.body(), condition, end);
        emitLabel(condition);
        String accepted = emitExpression(loop.condition());
        emitJump(start, JumpCondition.NOT_EQUAL, accepted, "0");
        emitLabel(end);
    }

    private void emitFor(HirFor loop) {
        MlogProgramBuilder.Label condition = label("for_condition");
        MlogProgramBuilder.Label update = label("for_update");
        MlogProgramBuilder.Label end = label("for_end");
        loop.declarationInitializer().ifPresent(this::emitStatement);
        loop.expressionInitializer().ifPresent(this::emitExpression);
        emitLabel(condition);
        String accepted = emitExpression(loop.condition());
        emitJump(end, JumpCondition.EQUAL, accepted, "0");
        emitLoopBody(loop.body(), update, end);
        emitLabel(update);
        loop.update().ifPresent(this::emitExpression);
        emitJump(condition, JumpCondition.ALWAYS, "0", "0");
        emitLabel(end);
    }

    private void emitIf(HirIf branch) {
        MlogProgramBuilder.Label otherwise = branch.elseBody().isPresent() ? label("if_else") : null;
        MlogProgramBuilder.Label end = label("if_end");
        String condition = emitExpression(branch.condition());
        emitJump(otherwise == null ? end : otherwise, JumpCondition.EQUAL, condition, "0");
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
        emitLoopBody(iteration.body(), next, end);

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
        emitLoopBody(iteration.body(), next, end);

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
        output.buildingControl(resolveBuildingTarget(control.target()), control.action(), arguments);
    }

    private void emitDraw(HirDraw draw) {
        if (activeDrawTarget != null && !activeDrawTarget.equals(draw.displayName())) output.drawFlush(activeDrawTarget);
        activeDrawTarget = draw.displayName();
        List<String> values = draw.arguments().stream().map(this::emitExpression).toList();
        List<String> operands = switch (draw.command()) {
            case CLEAR -> List.of(values.get(0), values.get(1), values.get(2), "0", "0", "0");
            case COLOR -> List.of(values.get(0), values.get(1), values.get(2), values.get(3), "0", "0");
            case RECT, LINE_RECT, LINE -> List.of(values.get(0), values.get(1), values.get(2), values.get(3), "0", "0");
        };
        String command = switch (draw.command()) {
            case CLEAR -> "clear";
            case COLOR -> "color";
            case RECT -> "rect";
            case LINE_RECT -> "lineRect";
            case LINE -> "line";
        };
        output.draw(command, operands);
    }

    /** The target owns one graphics buffer, so runtime flushes it before switching Displays and at exit. */
    private void flushDrawBuffer(String requestedTarget) {
        if (activeDrawTarget != null) output.drawFlush(activeDrawTarget);
        else output.drawFlush(requestedTarget);
        activeDrawTarget = null;
    }

    private void flushPendingDraw() {
        if (activeDrawTarget != null) output.drawFlush(activeDrawTarget);
        activeDrawTarget = null;
    }

    private void emitBuildingIteration(HirBuildingIteration iteration) {
        MlogProgramBuilder.Label end = label("building_end");
        HirHardwareLink previous = activeBuildingBindings.get(iteration.bindingName());
        try {
            for (HirHardwareLink building : iteration.buildings()) {
                MlogProgramBuilder.Label next = label("building_next");
                activeBuildingBindings.put(iteration.bindingName(), building);
                emitFilterRejectionJump(iteration.filters(), next);
                emitLoopBody(iteration.body(), next, end);
                emitLabel(next);
            }
        } finally {
            if (previous == null) activeBuildingBindings.remove(iteration.bindingName());
            else activeBuildingBindings.put(iteration.bindingName(), previous);
        }
        emitLabel(end);
    }

    private String resolveBuildingTarget(HirExpression target) {
        if (target instanceof HirHardwareLink hardware) return hardware.gameAlias();
        if (target instanceof HirVariable variable && variable.type() == com.arc.mpl.hir.ValueType.BUILDING) {
            HirHardwareLink building = activeBuildingBindings.get(variable.name());
            if (building != null) return building.gameAlias();
        }
        throw new IllegalArgumentException("building control lacks an active linked building target: " + target);
    }

    private void emitLoopBody(List<HirStatement> body, MlogProgramBuilder.Label continueTarget,
                              MlogProgramBuilder.Label breakTarget) {
        loopTargets.push(new LoopTarget(continueTarget, breakTarget));
        try {
            for (HirStatement statement : body) emitStatement(statement);
        } finally {
            loopTargets.pop();
        }
    }

    private void emitLoopJump(boolean breaking) {
        LoopTarget target = loopTargets.peek();
        if (target == null) throw new IllegalStateException("循环跳转缺少目标");
        emitJump(breaking ? target.breakTarget() : target.continueTarget(), JumpCondition.ALWAYS, "0", "0");
    }

    private String emitExpression(HirExpression expression) {
        if (expression instanceof HirConstant constant) return constant.mlogLiteral();
        if (expression instanceof HirText text) return quote(text.value());
        if (expression instanceof HirArrayLiteral || expression instanceof HirTupleLiteral || expression instanceof HirCollectionLiteral) {
            throw new IllegalArgumentException("aggregate literal must be assigned before target lowering");
        }
        if (expression instanceof HirVariable variable) return variable(variable.name());
        if (expression instanceof HirIndexAccess access) return emitIndexAccess(access);
        if (expression instanceof HirCollectionContains contains) return emitCollectionContains(contains);
        if (expression instanceof HirUnary unary) return emitUnary(unary);
        if (expression instanceof HirBinary binary) return emitBinary(binary);
        if (expression instanceof HirMemberAccess member) return emitMemberAccess(member);
        if (expression instanceof HirIntrinsicCall call) return emitIntrinsicCall(call);
        if (expression instanceof HirFunctionCall call) return emitFunctionCall(call);
        return emitAssignment((HirAssignment) expression);
    }

    private void emitAggregateDeclaration(String name, List<HirExpression> elements) {
        for (int index = 0; index < elements.size(); index++) {
            output.set(aggregateSlot(name, index), emitExpression(elements.get(index)));
        }
    }

    private void emitAggregateIteration(HirAggregateIteration iteration) {
        MlogProgramBuilder.Label end = label("aggregate_end");
        for (int index = 0; index < iteration.size(); index++) {
            MlogProgramBuilder.Label next = label("aggregate_next");
            output.set(variable(iteration.bindingName()), aggregateSlot(iteration.source().name(), index));
            emitLoopBody(iteration.body(), next, end);
            emitLabel(next);
        }
        emitLabel(end);
    }

    private String emitIndexAccess(HirIndexAccess access) {
        if (!(access.target() instanceof HirVariable variable) || !(access.index() instanceof HirConstant index)) {
            throw new IllegalArgumentException("aggregate access must be statically resolved before target lowering");
        }
        int position;
        try {
            position = Math.toIntExact(Long.parseLong(index.mlogLiteral()));
        } catch (NumberFormatException | ArithmeticException exception) {
            throw new IllegalArgumentException("invalid aggregate index: " + index.mlogLiteral(), exception);
        }
        return aggregateSlot(variable.name(), position);
    }

    private String emitCollectionContains(HirCollectionContains contains) {
        if (!(contains.target() instanceof HirVariable variable)) {
            throw new IllegalArgumentException("collection contains target must be a variable");
        }
        String candidate = temporary();
        output.set(candidate, emitExpression(contains.candidate()));
        String result = temporary();
        output.set(result, "0");
        for (int index = 0; index < contains.size(); index++) {
            String equal = temporary();
            output.operation(Operation.EQUAL, equal, aggregateSlot(variable.name(), index), candidate);
            output.operation(Operation.OR, result, result, equal);
        }
        return result;
    }

    private String emitFunctionCall(HirFunctionCall call) {
        HirFunction function = functions.get(call.function());
        if (function == null) throw new IllegalArgumentException("unknown HIR function: " + call.function());
        List<String> savedArguments = new java.util.ArrayList<>();
        for (HirExpression argument : call.arguments()) {
            String saved = temporary();
            output.set(saved, emitExpression(argument));
            savedArguments.add(saved);
        }
        for (int index = 0; index < savedArguments.size() && index < function.parameters().size(); index++) {
            output.set(functionParameterSlot(function.name(), function.parameters().get(index).name()), savedArguments.get(index));
        }
        output.operation(Operation.ADD, functionReturnSlot(function.name()), "@counter", "1");
        output.setCounter(functionEntries.get(function.name()));
        if (call.type() == com.arc.mpl.hir.ValueType.VOID) return "0";
        String result = temporary();
        output.set(result, functionResultSlot(function.name()));
        return result;
    }

    private String emitMemberAccess(HirMemberAccess member) {
        if (member.target() instanceof HirHardwareLink hardware) {
            String result = temporary();
            output.sensor(result, hardware.gameAlias(), "@" + member.member());
            return result;
        }
        if (member.target() instanceof HirVariable variable && variable.type() == com.arc.mpl.hir.ValueType.BUILDING) {
            HirHardwareLink building = activeBuildingBindings.get(variable.name());
            if (building == null) throw new IllegalArgumentException("building field access lacks an active binding: " + variable.name());
            String result = temporary();
            output.sensor(result, building.gameAlias(), "@" + member.member());
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
        if ("&&".equals(binary.operator()) || "||".equals(binary.operator())) {
            return emitShortCircuitBinary(binary);
        }
        String left = emitExpression(binary.left());
        String right = emitExpression(binary.right());
        String temporary = temporary();
        output.operation(operation(binary.operator()), temporary, left, right);
        return temporary;
    }

    /** Emits MPL's lazy boolean operators without relying on mlog's eager op instructions. */
    private String emitShortCircuitBinary(HirBinary binary) {
        String left = emitExpression(binary.left());
        String result = temporary();
        MlogProgramBuilder.Label end = label("short_circuit_end");
        boolean conjunction = "&&".equals(binary.operator());
        output.set(result, conjunction ? "0" : "1");
        emitJump(end, conjunction ? JumpCondition.EQUAL : JumpCondition.NOT_EQUAL, left, "0");
        output.set(result, emitExpression(binary.right()));
        emitLabel(end);
        return result;
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
            default -> throw new IllegalArgumentException("unsupported binary operator " + operator);
        };
    }

    private String variable(String name) {
        if (currentFunction == null || globalVariables.contains(name)) return "mpl_" + name;
        HirFunction function = functions.get(currentFunction);
        if (function != null && function.parameters().stream().anyMatch(parameter -> parameter.name().equals(name))) {
            return functionParameterSlot(currentFunction, name);
        }
        return functionPrefix(currentFunction) + "_local_" + name;
    }

    private String aggregateSlot(String name, int index) {
        if (index < 0) throw new IllegalArgumentException("aggregate index must be non-negative");
        return variable(name) + "_e" + index;
    }

    private String functionParameterSlot(String function, String parameter) {
        HirFunction declaration = functions.get(function);
        if (declaration == null) throw new IllegalArgumentException("unknown HIR function: " + function);
        for (int index = 0; index < declaration.parameters().size(); index++) {
            if (declaration.parameters().get(index).name().equals(parameter)) {
                return functionPrefix(function) + "_arg" + index;
            }
        }
        throw new IllegalArgumentException("unknown HIR function parameter: " + function + "." + parameter);
    }

    private String functionReturnSlot(String function) { return functionPrefix(function) + "_return"; }
    private String functionResultSlot(String function) { return functionPrefix(function) + "_result"; }

    private String functionPrefix(String function) {
        Integer index = functionIndexes.get(function);
        if (index == null) throw new IllegalArgumentException("unknown HIR function: " + function);
        return "__mpl_fn" + index;
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

    private record LoopTarget(MlogProgramBuilder.Label continueTarget, MlogProgramBuilder.Label breakTarget) {
    }
}
