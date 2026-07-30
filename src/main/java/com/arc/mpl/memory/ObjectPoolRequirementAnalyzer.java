package com.arc.mpl.memory;

import com.arc.mpl.hir.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Computes conservative, deterministic class-pool capacities from unique-owner HIR declarations. */
final class ObjectPoolRequirementAnalyzer {
    List<Requirement> analyze(HirProgram program) {
        Map<String, Integer> owners = new LinkedHashMap<>();
        Set<String> pooledAllocations = new LinkedHashSet<>();
        collect(program.statements(), owners, pooledAllocations);
        for (HirFunction function : program.functions()) collect(function.body(), owners, pooledAllocations);

        Map<String, HirClass> classes = new LinkedHashMap<>();
        for (HirClass type : program.classes()) classes.put(type.name(), type);
        List<Requirement> result = new ArrayList<>();
        int handleBase = 0;
        for (HirClass type : program.classes()) {
            int ownerCount = owners.getOrDefault(type.name(), 0);
            if (ownerCount == 0 && !pooledAllocations.contains(type.name())) continue;
            int capacity = Math.max(1, ownerCount);
            List<FieldRequirement> fields = type.fields().stream()
                .map(field -> new FieldRequirement(field.name(), field.type(), width(field.type()),
                    PhysicalMemoryLayout.objectPoolFieldKey(type.name(), field.name())))
                .toList();
            result.add(new Requirement(type.name(), capacity, handleBase,
                PhysicalMemoryLayout.objectPoolOccupancyKey(type.name()), fields));
            handleBase = Math.addExact(handleBase, capacity);
        }
        for (String className : pooledAllocations) {
            if (!classes.containsKey(className)) throw new IllegalArgumentException("对象池引用了未知类：" + className);
        }
        return List.copyOf(result);
    }

    private int width(MplType type) {
        if (type == ValueType.INT || type == ValueType.FLOAT || type == ValueType.BOOL) return 1;
        if (type instanceof TupleType tuple && tuple.elementTypes().stream().allMatch(element ->
            element == ValueType.INT || element == ValueType.FLOAT || element == ValueType.BOOL)) {
            return tuple.elementTypes().size();
        }
        throw new IllegalArgumentException("对象池字段不能写入物理 Memory：" + type.displayName());
    }

    private void collect(List<HirStatement> statements, Map<String, Integer> owners, Set<String> pooledAllocations) {
        for (HirStatement statement : statements) collect(statement, owners, pooledAllocations);
    }

    private void collect(HirStatement statement, Map<String, Integer> owners, Set<String> pooledAllocations) {
        if (statement instanceof HirVariableDeclaration declaration) {
            if (declaration.ownsPooledObject() && declaration.type() instanceof ObjectType object) {
                owners.merge(object.className(), 1, Integer::sum);
            }
            collect(declaration.initializer(), owners, pooledAllocations);
        } else if (statement instanceof HirExpressionStatement expression) {
            collect(expression.expression(), owners, pooledAllocations);
        } else if (statement instanceof HirPrintStatement print) {
            print.arguments().forEach(value -> collect(value, owners, pooledAllocations));
        } else if (statement instanceof HirDraw draw) {
            draw.arguments().forEach(value -> collect(value, owners, pooledAllocations));
        } else if (statement instanceof HirBlock block) {
            collect(block.statements(), owners, pooledAllocations);
        } else if (statement instanceof HirIf branch) {
            collect(branch.condition(), owners, pooledAllocations);
            collect(branch.thenBody(), owners, pooledAllocations);
            branch.elseBody().ifPresent(body -> collect(body, owners, pooledAllocations));
        } else if (statement instanceof HirWhile loop) {
            collect(loop.condition(), owners, pooledAllocations);
            collect(loop.body(), owners, pooledAllocations);
        } else if (statement instanceof HirDoWhile loop) {
            collect(loop.body(), owners, pooledAllocations);
            collect(loop.condition(), owners, pooledAllocations);
        } else if (statement instanceof HirFor loop) {
            loop.declarationInitializer().ifPresent(value -> collect(value, owners, pooledAllocations));
            loop.expressionInitializer().ifPresent(value -> collect(value, owners, pooledAllocations));
            collect(loop.condition(), owners, pooledAllocations);
            loop.update().ifPresent(value -> collect(value, owners, pooledAllocations));
            collect(loop.body(), owners, pooledAllocations);
        } else if (statement instanceof HirUnitIteration loop) {
            loop.filters().forEach(value -> collect(value, owners, pooledAllocations));
            collect(loop.body(), owners, pooledAllocations);
        } else if (statement instanceof HirBuildingIteration loop) {
            loop.filters().forEach(value -> collect(value, owners, pooledAllocations));
            collect(loop.body(), owners, pooledAllocations);
        } else if (statement instanceof HirAggregateIteration loop) {
            collect(loop.source(), owners, pooledAllocations);
            collect(loop.body(), owners, pooledAllocations);
        } else if (statement instanceof HirUnitControl control) {
            control.arguments().forEach(value -> collect(value, owners, pooledAllocations));
        } else if (statement instanceof HirBuildingControl control) {
            collect(control.target(), owners, pooledAllocations);
            control.arguments().forEach(value -> collect(value, owners, pooledAllocations));
        } else if (statement instanceof HirCollectionSet update) {
            collect(update.value(), owners, pooledAllocations);
        } else if (statement instanceof HirDynamicCollectionSet update) {
            collect(update.index(), owners, pooledAllocations);
            collect(update.value(), owners, pooledAllocations);
        } else if (statement instanceof HirReturn returned) {
            returned.value().ifPresent(value -> collect(value, owners, pooledAllocations));
        }
    }

    private void collect(HirExpression expression, Map<String, Integer> owners, Set<String> pooledAllocations) {
        if (expression instanceof HirNewObject allocation) {
            if (allocation.allocationKind() == HirNewObject.AllocationKind.POOLED) {
                pooledAllocations.add(allocation.className());
            }
            allocation.arguments().forEach(value -> collect(value, owners, pooledAllocations));
        } else if (expression instanceof HirFunctionCall call) {
            call.arguments().forEach(value -> collect(value, owners, pooledAllocations));
        } else if (expression instanceof HirAssignment assignment) {
            collect(assignment.value(), owners, pooledAllocations);
        } else if (expression instanceof HirBinary binary) {
            collect(binary.left(), owners, pooledAllocations);
            collect(binary.right(), owners, pooledAllocations);
        } else if (expression instanceof HirUnary unary) {
            collect(unary.operand(), owners, pooledAllocations);
        } else if (expression instanceof HirStringConcat concat) {
            collect(concat.left(), owners, pooledAllocations);
            collect(concat.right(), owners, pooledAllocations);
        } else if (expression instanceof HirStringLength length) {
            collect(length.value(), owners, pooledAllocations);
        } else if (expression instanceof HirStringComparison comparison) {
            collect(comparison.left(), owners, pooledAllocations);
            collect(comparison.right(), owners, pooledAllocations);
        } else if (expression instanceof HirStringSnapshot snapshot) {
            collect(snapshot.value(), owners, pooledAllocations);
        } else if (expression instanceof HirIntrinsicCall call) {
            call.arguments().forEach(value -> collect(value, owners, pooledAllocations));
        } else if (expression instanceof HirMemberAccess member) {
            collect(member.target(), owners, pooledAllocations);
        } else if (expression instanceof HirArrayLiteral array) {
            array.elements().forEach(value -> collect(value, owners, pooledAllocations));
        } else if (expression instanceof HirTupleLiteral tuple) {
            tuple.elements().forEach(value -> collect(value, owners, pooledAllocations));
        } else if (expression instanceof HirCollectionLiteral collection) {
            collection.elements().forEach(value -> collect(value, owners, pooledAllocations));
        } else if (expression instanceof HirIndexAccess access) {
            collect(access.target(), owners, pooledAllocations);
            collect(access.index(), owners, pooledAllocations);
        } else if (expression instanceof HirDynamicIndexAccess access) {
            collect(access.target(), owners, pooledAllocations);
            collect(access.index(), owners, pooledAllocations);
        } else if (expression instanceof HirCollectionContains contains) {
            collect(contains.target(), owners, pooledAllocations);
            collect(contains.candidate(), owners, pooledAllocations);
        } else if (expression instanceof HirObjectFieldRead read) {
            collect(read.target(), owners, pooledAllocations);
        } else if (expression instanceof HirObjectFieldAssignment assignment) {
            collect(assignment.target(), owners, pooledAllocations);
            collect(assignment.value(), owners, pooledAllocations);
        } else if (expression instanceof HirUnitQuery query) {
            query.filters().forEach(value -> collect(value, owners, pooledAllocations));
        } else if (expression instanceof HirUnitQuerySize size) {
            size.query().filters().forEach(value -> collect(value, owners, pooledAllocations));
        } else if (expression instanceof HirUnitQueryGet get) {
            get.query().filters().forEach(value -> collect(value, owners, pooledAllocations));
            collect(get.index(), owners, pooledAllocations);
        } else if (expression instanceof HirBuildingQuery query) {
            query.filters().forEach(value -> collect(value, owners, pooledAllocations));
        } else if (expression instanceof HirBuildingQuerySize size) {
            size.query().filters().forEach(value -> collect(value, owners, pooledAllocations));
        } else if (expression instanceof HirBuildingQueryGet get) {
            get.query().filters().forEach(value -> collect(value, owners, pooledAllocations));
            collect(get.index(), owners, pooledAllocations);
        }
    }

    record Requirement(String className, int capacity, int handleBase,
                       PhysicalMemoryLayout.StorageKey occupancy, List<FieldRequirement> fields) {
        int slots() {
            int slots = capacity;
            for (FieldRequirement field : fields) slots = Math.addExact(slots, field.slots(capacity));
            return slots;
        }
    }

    record FieldRequirement(String name, MplType type, int width, PhysicalMemoryLayout.StorageKey key) {
        int slots(int capacity) {
            return Math.multiplyExact(capacity, width);
        }
    }
}
