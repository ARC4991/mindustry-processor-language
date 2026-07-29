package com.arc.mpl.memory;

import com.arc.mpl.hir.HirArrayLiteral;
import com.arc.mpl.hir.HirAggregateIteration;
import com.arc.mpl.hir.HirBlock;
import com.arc.mpl.hir.HirBuildingControl;
import com.arc.mpl.hir.HirBuildingIteration;
import com.arc.mpl.hir.HirCollectionContains;
import com.arc.mpl.hir.HirCollectionLiteral;
import com.arc.mpl.hir.HirCollectionSet;
import com.arc.mpl.hir.HirDoWhile;
import com.arc.mpl.hir.HirDraw;
import com.arc.mpl.hir.HirDynamicCollectionSet;
import com.arc.mpl.hir.HirDynamicIndexAccess;
import com.arc.mpl.hir.HirExpression;
import com.arc.mpl.hir.HirExpressionStatement;
import com.arc.mpl.hir.HirFor;
import com.arc.mpl.hir.HirFunction;
import com.arc.mpl.hir.HirFunctionCall;
import com.arc.mpl.hir.HirIf;
import com.arc.mpl.hir.HirIndexAccess;
import com.arc.mpl.hir.HirIntrinsicCall;
import com.arc.mpl.hir.HirMemberAccess;
import com.arc.mpl.hir.HirPrintStatement;
import com.arc.mpl.hir.HirProgram;
import com.arc.mpl.hir.HirReturn;
import com.arc.mpl.hir.HirStatement;
import com.arc.mpl.hir.HirTupleLiteral;
import com.arc.mpl.hir.HirUnary;
import com.arc.mpl.hir.HirUnitControl;
import com.arc.mpl.hir.HirUnitIteration;
import com.arc.mpl.hir.HirUnitQuery;
import com.arc.mpl.hir.HirUnitQueryGet;
import com.arc.mpl.hir.HirUnitQuerySize;
import com.arc.mpl.hir.HirVariable;
import com.arc.mpl.hir.HirVariableDeclaration;
import com.arc.mpl.hir.HirWhile;
import com.arc.mpl.profile.TargetProfile;
import com.arc.mpl.project.RuntimePreferences;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Computes deterministic physical storage for arrays that require runtime indexing. */
public final class PhysicalMemoryPlanner {
    public PhysicalMemoryLayout plan(HirProgram program, TargetProfile profile, RuntimePreferences preferences) {
        Objects.requireNonNull(program, "program");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(preferences, "preferences");

        Map<PhysicalMemoryLayout.StorageKey, Integer> declarations = new LinkedHashMap<>();
        Set<PhysicalMemoryLayout.StorageKey> required = new LinkedHashSet<>();
        collect(null, program.statements(), declarations, required);
        for (HirFunction function : program.functions()) collect(function.name(), function.body(), declarations, required);

        List<Map.Entry<PhysicalMemoryLayout.StorageKey, Integer>> allocations = required.stream()
            .map(key -> Map.entry(key, requireDeclaration(key, declarations)))
            .toList();
        int slots = allocations.stream().mapToInt(Map.Entry::getValue).sum();
        if (slots == 0) return PhysicalMemoryLayout.empty();

        List<RuntimePreferences.MemoryKind> segmentKinds = chooseSegments(slots, profile, preferences);
        List<Integer> used = new ArrayList<>(java.util.Collections.nCopies(segmentKinds.size(), 0));
        Map<PhysicalMemoryLayout.StorageKey, PhysicalMemoryLayout.Allocation> placed = new LinkedHashMap<>();
        int segment = 0;
        for (Map.Entry<PhysicalMemoryLayout.StorageKey, Integer> entry : allocations) {
            int remaining = entry.getValue();
            int logicalStart = 0;
            List<PhysicalMemoryLayout.Slice> slices = new ArrayList<>();
            while (remaining > 0) {
                while (used.get(segment) == capacity(segmentKinds.get(segment), profile)) segment++;
                int capacity = capacity(segmentKinds.get(segment), profile);
                int offset = used.get(segment);
                int length = Math.min(remaining, capacity - offset);
                slices.add(new PhysicalMemoryLayout.Slice(segment, offset, logicalStart, length));
                used.set(segment, offset + length);
                logicalStart += length;
                remaining -= length;
                if (used.get(segment) == capacity && remaining > 0) segment++;
            }
            placed.put(entry.getKey(), new PhysicalMemoryLayout.Allocation(entry.getKey(), entry.getValue(), slices));
        }
        List<PhysicalMemoryLayout.Segment> segments = new ArrayList<>();
        for (int index = 0; index < segmentKinds.size(); index++) {
            RuntimePreferences.MemoryKind kind = segmentKinds.get(index);
            segments.add(new PhysicalMemoryLayout.Segment("__mpl_mem" + index, kind, capacity(kind, profile), used.get(index)));
        }
        return new PhysicalMemoryLayout(segments, placed, slots);
    }

    private void collect(String function, List<HirStatement> statements,
                         Map<PhysicalMemoryLayout.StorageKey, Integer> declarations,
                         Set<PhysicalMemoryLayout.StorageKey> required) {
        for (HirStatement statement : statements) {
            if (statement instanceof HirVariableDeclaration declaration && declaration.initializer() instanceof HirArrayLiteral array) {
                declarations.put(new PhysicalMemoryLayout.StorageKey(function, declaration.name()), array.elements().size());
            }
            if (statement instanceof HirVariableDeclaration declaration) {
                collectExpression(function, declaration.initializer(), declarations, required);
            } else if (statement instanceof HirDynamicCollectionSet update) {
                required.add(resolve(function, update.target(), declarations));
                collectExpression(function, update.index(), declarations, required);
                collectExpression(function, update.value(), declarations, required);
            } else if (statement instanceof HirCollectionSet update) {
                collectExpression(function, update.value(), declarations, required);
            } else if (statement instanceof HirExpressionStatement expression) {
                collectExpression(function, expression.expression(), declarations, required);
            } else if (statement instanceof HirPrintStatement print) {
                collectExpressions(function, print.arguments(), declarations, required);
            } else if (statement instanceof HirBlock block) {
                collect(function, block.statements(), declarations, required);
            } else if (statement instanceof HirIf branch) {
                collectExpression(function, branch.condition(), declarations, required);
                collect(function, branch.thenBody(), declarations, required);
                branch.elseBody().ifPresent(body -> collect(function, body, declarations, required));
            } else if (statement instanceof HirWhile loop) {
                collectExpression(function, loop.condition(), declarations, required);
                collect(function, loop.body(), declarations, required);
            } else if (statement instanceof HirDoWhile loop) {
                collect(function, loop.body(), declarations, required);
                collectExpression(function, loop.condition(), declarations, required);
            } else if (statement instanceof HirFor loop) {
                loop.declarationInitializer().ifPresent(declaration -> {
                    if (declaration.initializer() instanceof HirArrayLiteral array) {
                        declarations.put(new PhysicalMemoryLayout.StorageKey(function, declaration.name()), array.elements().size());
                    }
                    collectExpression(function, declaration.initializer(), declarations, required);
                });
                loop.expressionInitializer().ifPresent(value -> collectExpression(function, value, declarations, required));
                collectExpression(function, loop.condition(), declarations, required);
                loop.update().ifPresent(value -> collectExpression(function, value, declarations, required));
                collect(function, loop.body(), declarations, required);
            } else if (statement instanceof HirUnitIteration loop) {
                collectExpressions(function, loop.filters(), declarations, required);
                collect(function, loop.body(), declarations, required);
            } else if (statement instanceof HirBuildingIteration loop) {
                collectExpressions(function, loop.filters(), declarations, required);
                collect(function, loop.body(), declarations, required);
            } else if (statement instanceof HirAggregateIteration loop) {
                collectExpression(function, loop.source(), declarations, required);
                collect(function, loop.body(), declarations, required);
            } else if (statement instanceof HirUnitControl control) {
                collectExpressions(function, control.arguments(), declarations, required);
            } else if (statement instanceof HirBuildingControl control) {
                collectExpression(function, control.target(), declarations, required);
                collectExpressions(function, control.arguments(), declarations, required);
            } else if (statement instanceof HirDraw draw) {
                collectExpressions(function, draw.arguments(), declarations, required);
            } else if (statement instanceof HirReturn returned) {
                returned.value().ifPresent(value -> collectExpression(function, value, declarations, required));
            }
        }
    }

    private void collectExpression(String function, HirExpression expression,
                                   Map<PhysicalMemoryLayout.StorageKey, Integer> declarations,
                                   Set<PhysicalMemoryLayout.StorageKey> required) {
        if (expression instanceof HirDynamicIndexAccess access && access.target() instanceof HirVariable variable) {
            required.add(resolve(function, variable.name(), declarations));
            collectExpression(function, access.index(), declarations, required);
            return;
        }
        if (expression instanceof HirDynamicIndexAccess access) {
            collectExpression(function, access.target(), declarations, required);
            collectExpression(function, access.index(), declarations, required);
            throw new IllegalArgumentException("动态数组访问必须以具名 Array 变量为目标");
        }
        if (expression instanceof HirUnary unary) collectExpression(function, unary.operand(), declarations, required);
        if (expression instanceof com.arc.mpl.hir.HirBinary binary) {
            collectExpression(function, binary.left(), declarations, required);
            collectExpression(function, binary.right(), declarations, required);
        }
        if (expression instanceof com.arc.mpl.hir.HirAssignment assignment) collectExpression(function, assignment.value(), declarations, required);
        if (expression instanceof HirMemberAccess member) collectExpression(function, member.target(), declarations, required);
        if (expression instanceof HirIntrinsicCall call) collectExpressions(function, call.arguments(), declarations, required);
        if (expression instanceof HirFunctionCall call) collectExpressions(function, call.arguments(), declarations, required);
        if (expression instanceof HirArrayLiteral array) collectExpressions(function, array.elements(), declarations, required);
        if (expression instanceof HirTupleLiteral tuple) collectExpressions(function, tuple.elements(), declarations, required);
        if (expression instanceof HirCollectionLiteral collection) collectExpressions(function, collection.elements(), declarations, required);
        if (expression instanceof HirIndexAccess access) {
            collectExpression(function, access.target(), declarations, required);
            collectExpression(function, access.index(), declarations, required);
        }
        if (expression instanceof HirCollectionContains contains) {
            collectExpression(function, contains.target(), declarations, required);
            collectExpression(function, contains.candidate(), declarations, required);
        }
        if (expression instanceof HirUnitQuery query) collectExpressions(function, query.filters(), declarations, required);
        if (expression instanceof HirUnitQuerySize size) collectExpressions(function, size.query().filters(), declarations, required);
        if (expression instanceof HirUnitQueryGet get) {
            collectExpressions(function, get.query().filters(), declarations, required);
            collectExpression(function, get.index(), declarations, required);
        }
    }

    private void collectExpressions(String function, List<HirExpression> expressions,
                                    Map<PhysicalMemoryLayout.StorageKey, Integer> declarations,
                                    Set<PhysicalMemoryLayout.StorageKey> required) {
        expressions.forEach(expression -> collectExpression(function, expression, declarations, required));
    }

    private PhysicalMemoryLayout.StorageKey resolve(String function, String variable,
                                                    Map<PhysicalMemoryLayout.StorageKey, Integer> declarations) {
        PhysicalMemoryLayout.StorageKey local = new PhysicalMemoryLayout.StorageKey(function, variable);
        if (declarations.containsKey(local)) return local;
        PhysicalMemoryLayout.StorageKey global = new PhysicalMemoryLayout.StorageKey(null, variable);
        return declarations.containsKey(global) ? global : local;
    }

    private int requireDeclaration(PhysicalMemoryLayout.StorageKey key,
                                   Map<PhysicalMemoryLayout.StorageKey, Integer> declarations) {
        Integer size = declarations.get(key);
        if (size == null || size < 1) throw new IllegalArgumentException("动态数组缺少固定容量声明：" + key.variable());
        return size;
    }

    private List<RuntimePreferences.MemoryKind> chooseSegments(int slots, TargetProfile profile,
                                                               RuntimePreferences preferences) {
        List<RuntimePreferences.MemoryKind> segments = new ArrayList<>();
        int remaining = slots;
        for (RuntimePreferences.MemoryKind kind : List.of(RuntimePreferences.MemoryKind.BANK, RuntimePreferences.MemoryKind.CELL)) {
            int allowed = preferences.memory().getOrDefault(kind, 0);
            int required = remaining == 0 ? 0 : ((remaining - 1) / capacity(kind, profile)) + 1;
            int selected = Math.min(allowed, required);
            for (int index = 0; index < selected; index++) segments.add(kind);
            long provided = (long) selected * capacity(kind, profile);
            remaining = (int) Math.max(0L, remaining - provided);
            if (remaining == 0) return List.copyOf(segments);
        }
        throw new IllegalArgumentException("运行时 Memory 约束无法满足 " + slots + " 个物理槽需求");
    }

    private int capacity(RuntimePreferences.MemoryKind kind, TargetProfile profile) {
        return kind == RuntimePreferences.MemoryKind.CELL ? profile.memoryCellCapacity() : profile.memoryBankCapacity();
    }
}
