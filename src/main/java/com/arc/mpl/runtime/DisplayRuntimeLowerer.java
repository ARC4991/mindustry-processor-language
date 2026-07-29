package com.arc.mpl.runtime;

import com.arc.mpl.hir.HirAggregateIteration;
import com.arc.mpl.hir.HirArrayLiteral;
import com.arc.mpl.hir.HirAssignment;
import com.arc.mpl.hir.HirBinary;
import com.arc.mpl.hir.HirBlock;
import com.arc.mpl.hir.HirBuildingIteration;
import com.arc.mpl.hir.HirBuildingQuery;
import com.arc.mpl.hir.HirBuildingQueryGet;
import com.arc.mpl.hir.HirBuildingQuerySize;
import com.arc.mpl.hir.HirCollectionContains;
import com.arc.mpl.hir.HirCollectionLiteral;
import com.arc.mpl.hir.HirConstant;
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
import com.arc.mpl.hir.HirNewObject;
import com.arc.mpl.hir.HirObjectFieldAssignment;
import com.arc.mpl.hir.HirObjectFieldRead;
import com.arc.mpl.hir.HirProgram;
import com.arc.mpl.hir.HirStatement;
import com.arc.mpl.hir.HirTupleLiteral;
import com.arc.mpl.hir.HirUnary;
import com.arc.mpl.hir.HirUnitIteration;
import com.arc.mpl.hir.HirUnitQuery;
import com.arc.mpl.hir.HirUnitQueryGet;
import com.arc.mpl.hir.HirUnitQuerySize;
import com.arc.mpl.hir.HirVariable;
import com.arc.mpl.hir.HirVariableDeclaration;
import com.arc.mpl.hir.HirWhile;
import com.arc.mpl.hir.ValueType;
import com.arc.mpl.project.HardwareContract;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

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
    private final Map<String, HardwareContract.Resource> resources;
    private int nextArgumentId;
    private final Set<String> usedNames = new LinkedHashSet<>();

    public DisplayRuntimeLowerer(int maxBufferCommands) {
        this(maxBufferCommands, Map.of());
    }

    public DisplayRuntimeLowerer(int maxBufferCommands, HardwareContract hardware) {
        this(maxBufferCommands, Objects.requireNonNull(hardware, "hardware").resources());
    }

    private DisplayRuntimeLowerer(int maxBufferCommands, Map<String, HardwareContract.Resource> resources) {
        if (maxBufferCommands <= 0) throw new IllegalArgumentException("maxBufferCommands 必须为正数");
        this.maxBufferCommands = maxBufferCommands;
        this.resources = Map.copyOf(resources);
    }

    public HirProgram lower(HirProgram program) {
        Objects.requireNonNull(program, "program");
        nextArgumentId = 0;
        usedNames.clear();
        program.functions().forEach(function -> {
            function.parameters().forEach(parameter -> usedNames.add(parameter.name()));
            collectNames(function.body());
        });
        collectNames(program.statements());
        List<HirFunction> functions = program.functions().stream()
            .map(function -> new HirFunction(function.name(), function.parameters(), function.returnType(),
                lowerStatements(function.body())))
            .toList();
        return new HirProgram(program.classes(), functions, lowerStatements(program.statements()));
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
            state.target = draw.displayName();
            state.draws.add(draw);
            if (state.draws.size() >= maxBufferCommands) flush(state, lowered);
            return;
        }
        if (statement instanceof HirDrawFlush requested) {
            flush(state, lowered);
            lowered.add(new HirDrawFlush(directAlias(requested.displayName())));
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
            return new HirBuildingIteration(iteration.bindingName(), iteration.buildingType(), iteration.mlogType(),
                iteration.buildings(), iteration.filters(), lowerStatements(iteration.body()));
        }
        return statement;
    }

    private void flush(BufferState state, List<HirStatement> lowered) {
        if (state.target == null) return;
        HardwareContract.Resource resource = resources.get(state.target);
        if (resource == null || resource.physicalLinks().size() == 1) {
            String alias = resource == null ? state.target : resource.physicalLinks().get(0).gameAlias();
            state.draws.forEach(draw -> lowered.add(new HirDraw(alias, draw.command(), draw.arguments())));
            lowered.add(new HirDrawFlush(alias));
        } else {
            List<HirDraw> stableDraws = materializeArguments(state.draws, lowered);
            HardwareContract.DisplayLayout layout = resource.display().orElseThrow(() ->
                new IllegalStateException("组合 Display 缺少逻辑布局：" + resource.mplName()));
            for (HardwareContract.DisplayTile tile : layout.tiles()) {
                for (HirDraw draw : stableDraws) lowered.add(translate(draw, tile));
                lowered.add(new HirDrawFlush(tile.gameAlias()));
            }
        }
        state.target = null;
        state.draws.clear();
    }

    private List<HirDraw> materializeArguments(List<HirDraw> draws, List<HirStatement> lowered) {
        List<HirDraw> result = new ArrayList<>();
        for (HirDraw draw : draws) {
            List<HirExpression> arguments = new ArrayList<>();
            for (HirExpression argument : draw.arguments()) {
                if (argument instanceof HirConstant || argument instanceof HirVariable) {
                    arguments.add(argument);
                    continue;
                }
                String name = freshArgumentName();
                lowered.add(new HirVariableDeclaration(name, argument.type(), false, argument));
                arguments.add(new HirVariable(name, argument.type()));
            }
            result.add(new HirDraw(draw.displayName(), draw.command(), arguments));
        }
        return List.copyOf(result);
    }

    private HirDraw translate(HirDraw draw, HardwareContract.DisplayTile tile) {
        List<HirExpression> arguments = new ArrayList<>(draw.arguments());
        switch (draw.command()) {
            case RECT, LINE_RECT -> {
                arguments.set(0, subtract(arguments.get(0), tile.x()));
                arguments.set(1, subtract(arguments.get(1), tile.y()));
            }
            case LINE -> {
                arguments.set(0, subtract(arguments.get(0), tile.x()));
                arguments.set(1, subtract(arguments.get(1), tile.y()));
                arguments.set(2, subtract(arguments.get(2), tile.x()));
                arguments.set(3, subtract(arguments.get(3), tile.y()));
            }
            case CLEAR, COLOR -> {
                // Color state and clear commands must be replayed for every physical Display.
            }
        }
        return new HirDraw(tile.gameAlias(), draw.command(), arguments);
    }

    private String freshArgumentName() {
        String candidate;
        do candidate = "__mpl_display_arg" + nextArgumentId++;
        while (!usedNames.add(candidate));
        return candidate;
    }

    private void collectNames(List<HirStatement> statements) {
        for (HirStatement statement : statements) {
            if (statement instanceof HirVariableDeclaration declaration) usedNames.add(declaration.name());
            else if (statement instanceof HirBlock block) collectNames(block.statements());
            else if (statement instanceof HirIf branch) {
                collectNames(branch.thenBody());
                branch.elseBody().ifPresent(this::collectNames);
            } else if (statement instanceof HirWhile loop) collectNames(loop.body());
            else if (statement instanceof HirDoWhile loop) collectNames(loop.body());
            else if (statement instanceof HirFor loop) {
                loop.declarationInitializer().ifPresent(declaration -> usedNames.add(declaration.name()));
                collectNames(loop.body());
            }
            else if (statement instanceof HirUnitIteration iteration) {
                usedNames.add(iteration.bindingName());
                collectNames(iteration.body());
            } else if (statement instanceof HirAggregateIteration iteration) {
                usedNames.add(iteration.bindingName());
                collectNames(iteration.body());
            } else if (statement instanceof HirBuildingIteration iteration) {
                usedNames.add(iteration.bindingName());
                collectNames(iteration.body());
            }
        }
    }

    private HirExpression subtract(HirExpression value, int offset) {
        if (offset == 0) return value;
        if (value instanceof HirConstant constant && constant.type() == ValueType.INT) {
            try {
                long translated = Long.parseLong(constant.mlogLiteral()) - offset;
                long saturated = Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, translated));
                return new HirConstant(Long.toString(saturated), ValueType.INT);
            } catch (NumberFormatException ignored) {
                // Non-decimal target literals are lowered through the regular arithmetic path.
            }
        }
        return new HirBinary(value, "-", new HirConstant(Integer.toString(offset), ValueType.INT), value.type());
    }

    private String directAlias(String displayName) {
        HardwareContract.Resource resource = resources.get(displayName);
        if (resource == null) return displayName;
        if (resource.physicalLinks().size() != 1) {
            throw new IllegalArgumentException("组合 Display 不能作为单个 drawFlush 目标：" + displayName);
        }
        return resource.physicalLinks().get(0).gameAlias();
    }

    private boolean containsFunctionCall(HirExpression expression) {
        if (expression instanceof HirFunctionCall) return true;
        if (expression instanceof HirNewObject) return true;
        if (expression instanceof HirObjectFieldRead read) return containsFunctionCall(read.target());
        if (expression instanceof HirObjectFieldAssignment assignment) {
            return containsFunctionCall(assignment.target()) || containsFunctionCall(assignment.value());
        }
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
        if (expression instanceof HirBuildingQuery query) {
            return query.filters().stream().anyMatch(this::containsFunctionCall);
        }
        if (expression instanceof HirBuildingQuerySize size) {
            return size.query().filters().stream().anyMatch(this::containsFunctionCall);
        }
        if (expression instanceof HirBuildingQueryGet get) {
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
        private final List<HirDraw> draws = new ArrayList<>();
    }
}
