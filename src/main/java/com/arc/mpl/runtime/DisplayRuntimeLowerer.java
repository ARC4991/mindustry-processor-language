package com.arc.mpl.runtime;

import com.arc.mpl.hir.HirAggregateIteration;
import com.arc.mpl.hir.HirArrayLiteral;
import com.arc.mpl.hir.HirAssignment;
import com.arc.mpl.hir.HirBinary;
import com.arc.mpl.hir.HirBlock;
import com.arc.mpl.hir.HirBuildingIteration;
import com.arc.mpl.hir.HirCollectionContains;
import com.arc.mpl.hir.HirCollectionLiteral;
import com.arc.mpl.hir.HirDoWhile;
import com.arc.mpl.hir.HirDraw;
import com.arc.mpl.hir.HirDrawFlush;
import com.arc.mpl.hir.HirExpression;
import com.arc.mpl.hir.HirFor;
import com.arc.mpl.hir.HirFunction;
import com.arc.mpl.hir.HirFunctionCall;
import com.arc.mpl.hir.HirIf;
import com.arc.mpl.hir.HirIndexAccess;
import com.arc.mpl.hir.HirIntrinsicCall;
import com.arc.mpl.hir.HirMemberAccess;
import com.arc.mpl.hir.HirProgram;
import com.arc.mpl.hir.HirStatement;
import com.arc.mpl.hir.HirTupleLiteral;
import com.arc.mpl.hir.HirUnary;
import com.arc.mpl.hir.HirUnitIteration;
import com.arc.mpl.hir.HirUnitQuery;
import com.arc.mpl.hir.HirUnitQueryGet;
import com.arc.mpl.hir.HirUnitQuerySize;
import com.arc.mpl.hir.HirWhile;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Inserts compiler-private Display buffer commits into structured HIR.
 *
 * <p>MPL deliberately has no {@code flush()} method. This pass owns the
 * target graphics buffer and emits {@link HirDrawFlush} only for generated
 * MIL and mlog. It keeps adjacent drawing commands batched, commits before a
 * Display switch or control-flow boundary, and splits a batch at the profile
 * limit. Every structured body ends with a commit, so an unbounded loop can
 * never accumulate draw commands across iterations.</p>
 */
public final class DisplayRuntimeLowerer {
    private final int maxBufferCommands;

    public DisplayRuntimeLowerer(int maxBufferCommands) {
        if (maxBufferCommands <= 0) throw new IllegalArgumentException("maxBufferCommands 必须为正数");
        this.maxBufferCommands = maxBufferCommands;
    }

    public HirProgram lower(HirProgram program) {
        Objects.requireNonNull(program, "program");
        List<HirFunction> functions = program.functions().stream()
            .map(function -> new HirFunction(function.name(), function.parameters(), function.returnType(),
                lowerStatements(function.body())))
            .toList();
        return new HirProgram(functions, lowerStatements(program.statements()));
    }

    private List<HirStatement> lowerStatements(List<HirStatement> statements) {
        BufferState state = new BufferState();
        List<HirStatement> lowered = new ArrayList<>();
        for (HirStatement statement : statements) lowerStatement(statement, state, lowered);
        flush(state, lowered);
        return List.copyOf(lowered);
    }

    private void lowerStatement(HirStatement statement, BufferState state, List<HirStatement> lowered) {
        if (statement instanceof HirDraw draw) {
            // A user function can draw while evaluating an argument. Commit the
            // previous batch before entering it, because the target buffer is global.
            if (draw.arguments().stream().anyMatch(this::containsFunctionCall)) flush(state, lowered);
            if (state.target != null && !state.target.equals(draw.displayName())) flush(state, lowered);
            lowered.add(draw);
            state.target = draw.displayName();
            state.commands++;
            if (state.commands >= maxBufferCommands) flush(state, lowered);
            return;
        }
        if (statement instanceof HirDrawFlush flush) {
            if (state.target != null && !state.target.equals(flush.displayName())) flush(state, lowered);
            state.target = null;
            state.commands = 0;
            lowered.add(flush);
            return;
        }

        // A control-flow node or ordinary statement may execute code that calls
        // a function. Treat it as a buffer boundary; this is conservative and
        // makes the inserted MIL commits reflect the actual target semantics.
        flush(state, lowered);
        lowered.add(lowerStructuredStatement(statement));
    }

    private HirStatement lowerStructuredStatement(HirStatement statement) {
        if (statement instanceof HirBlock block) return new HirBlock(lowerStatements(block.statements()));
        if (statement instanceof HirIf branch) {
            Optional<List<HirStatement>> otherwise = branch.elseBody().map(this::lowerStatements);
            return new HirIf(branch.condition(), lowerStatements(branch.thenBody()), otherwise);
        }
        if (statement instanceof HirWhile loop) return new HirWhile(loop.condition(), lowerStatements(loop.body()));
        if (statement instanceof HirDoWhile loop) return new HirDoWhile(lowerStatements(loop.body()), loop.condition());
        if (statement instanceof HirFor loop) {
            return new HirFor(loop.declarationInitializer(), loop.expressionInitializer(), loop.condition(), loop.update(),
                lowerStatements(loop.body()));
        }
        if (statement instanceof HirUnitIteration iteration) {
            return new HirUnitIteration(iteration.bindingName(), iteration.unitType(), iteration.mlogType(),
                iteration.filters(), iteration.managedLimit(), iteration.managedId(), lowerStatements(iteration.body()));
        }
        if (statement instanceof HirAggregateIteration iteration) {
            return new HirAggregateIteration(iteration.bindingName(), iteration.source(), iteration.elementType(), iteration.size(),
                lowerStatements(iteration.body()));
        }
        if (statement instanceof HirBuildingIteration iteration) {
            return new HirBuildingIteration(iteration.bindingName(), iteration.buildingType(), iteration.buildings(),
                iteration.filters(), lowerStatements(iteration.body()));
        }
        return statement;
    }

    private void flush(BufferState state, List<HirStatement> lowered) {
        if (state.target != null) lowered.add(new HirDrawFlush(state.target));
        state.target = null;
        state.commands = 0;
    }

    private boolean containsFunctionCall(HirExpression expression) {
        if (expression instanceof HirFunctionCall) return true;
        if (expression instanceof HirAssignment assignment) return containsFunctionCall(assignment.value());
        if (expression instanceof HirBinary binary) {
            return containsFunctionCall(binary.left()) || containsFunctionCall(binary.right());
        }
        if (expression instanceof HirUnary unary) return containsFunctionCall(unary.operand());
        if (expression instanceof HirMemberAccess member) return containsFunctionCall(member.target());
        if (expression instanceof HirIndexAccess access) {
            return containsFunctionCall(access.target()) || containsFunctionCall(access.index());
        }
        if (expression instanceof HirIntrinsicCall call) return call.arguments().stream().anyMatch(this::containsFunctionCall);
        if (expression instanceof HirCollectionContains contains) {
            return containsFunctionCall(contains.target()) || containsFunctionCall(contains.candidate());
        }
        if (expression instanceof HirUnitQuery query) return query.filters().stream().anyMatch(this::containsFunctionCall);
        if (expression instanceof HirUnitQuerySize size) {
            return size.query().filters().stream().anyMatch(this::containsFunctionCall);
        }
        if (expression instanceof HirUnitQueryGet get) {
            return containsFunctionCall(get.index())
                || get.query().filters().stream().anyMatch(this::containsFunctionCall);
        }
        if (expression instanceof HirArrayLiteral array) return array.elements().stream().anyMatch(this::containsFunctionCall);
        if (expression instanceof HirTupleLiteral tuple) return tuple.elements().stream().anyMatch(this::containsFunctionCall);
        if (expression instanceof HirCollectionLiteral collection) {
            return collection.elements().stream().anyMatch(this::containsFunctionCall);
        }
        return false;
    }

    private static final class BufferState {
        private String target;
        private int commands;
    }
}
