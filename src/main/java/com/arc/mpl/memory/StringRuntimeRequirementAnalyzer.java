package com.arc.mpl.memory;

import com.arc.mpl.hir.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Collects bounded String storage without mixing semantic inference into the physical placer. */
final class StringRuntimeRequirementAnalyzer {
    Result analyze(HirProgram program, int defaultCapacity) {
        Map<String, HirClass> classes = new LinkedHashMap<>();
        for (HirClass type : program.classes()) classes.put(type.name(), type);
        StringCapacityAnalyzer.Analysis capacities =
            new StringCapacityAnalyzer().analyze(program, defaultCapacity);
        Collector collector = new Collector(defaultCapacity, classes, capacities);
        for (HirFunction function : program.functions()) {
            for (HirFunctionParameter parameter : function.parameters()) {
                if (parameter.type() == ValueType.STRING) {
                    collector.variable(function.name(), parameter.name(),
                        capacities.variable(function.name(), parameter.name(), defaultCapacity));
                }
            }
            if (function.returnType() == ValueType.STRING) {
                collector.functionResult(function.name(), capacities.functionResult(function.name()));
            }
            collector.statements(function.name(), function.body());
        }
        collector.statements(null, program.statements());
        return collector.result();
    }

    enum Kind { LITERAL, CONCATENATION, SNAPSHOT, CALL_RESULT, VARIABLE, FUNCTION_RESULT, OBJECT_FIELD, AGGREGATE_ELEMENT }

    record Requirement(Kind kind, int handle, int capacity, String literal, Integer concatenationId,
                       PhysicalMemoryLayout.StorageKey variable, String functionResult,
                       StringRuntimeLayout.ObjectFieldKey objectField,
                       StringRuntimeLayout.AggregateElementKey aggregateElement,
                       PhysicalMemoryLayout.StorageKey storage) {
        Requirement {
            if (capacity < 0) throw new IllegalArgumentException("String 容量不能为负数");
        }

        int physicalSlots() { return Math.max(1, capacity); }
        int fixedLength() { return literal == null ? -1 : literal.length(); }
    }

    record Result(List<Requirement> requirements) {
        Result { requirements = List.copyOf(requirements); }
        int slots() {
            if (requirements.isEmpty()) return 0;
            return Math.addExact(requirements.stream().mapToInt(Requirement::physicalSlots).sum(),
                Math.multiplyExact(requirements.size(), 2));
        }
    }

    private static final class Collector {
        private final int defaultCapacity;
        private final Map<String, HirClass> classes;
        private final StringCapacityAnalyzer.Analysis capacities;
        private final List<Requirement> requirements = new ArrayList<>();
        private final Map<String, Requirement> literals = new LinkedHashMap<>();
        private final Map<Integer, Requirement> concatenations = new LinkedHashMap<>();
        private final Map<Integer, Requirement> snapshots = new LinkedHashMap<>();
        private final Map<Integer, Requirement> callResults = new LinkedHashMap<>();
        private final Map<PhysicalMemoryLayout.StorageKey, Requirement> variables = new LinkedHashMap<>();
        private final Map<String, Requirement> functionResults = new LinkedHashMap<>();
        private final Map<StringRuntimeLayout.ObjectFieldKey, Requirement> objectFields = new LinkedHashMap<>();
        private final Map<StringRuntimeLayout.AggregateElementKey, Requirement> aggregateElements = new LinkedHashMap<>();

        private Collector(int defaultCapacity, Map<String, HirClass> classes,
                          StringCapacityAnalyzer.Analysis capacities) {
            this.defaultCapacity = defaultCapacity;
            this.classes = Map.copyOf(classes);
            this.capacities = capacities;
        }

        private Result result() { return new Result(requirements); }

        private void statements(String function, List<HirStatement> statements) {
            for (HirStatement statement : statements) statement(function, statement);
        }

        private void statement(String function, HirStatement statement) {
            if (statement instanceof HirVariableDeclaration declaration) {
                if (declaration.type() == ValueType.STRING) {
                    variable(function, declaration.name(),
                        capacities.variable(function, declaration.name(), declaration.stringCapacity()));
                }
                aggregate(function, declaration);
                expression(function, declaration.initializer());
            } else if (statement instanceof HirExpressionStatement value) {
                expression(function, value.expression());
            } else if (statement instanceof HirPrintStatement print) {
                print.arguments().forEach(value -> printExpression(function, value));
            } else if (statement instanceof HirDraw draw) {
                draw.arguments().forEach(value -> expression(function, value));
            } else if (statement instanceof HirBlock block) {
                statements(function, block.statements());
            } else if (statement instanceof HirIf branch) {
                expression(function, branch.condition());
                statements(function, branch.thenBody());
                branch.elseBody().ifPresent(body -> statements(function, body));
            } else if (statement instanceof HirWhile loop) {
                expression(function, loop.condition());
                statements(function, loop.body());
            } else if (statement instanceof HirDoWhile loop) {
                statements(function, loop.body());
                expression(function, loop.condition());
            } else if (statement instanceof HirFor loop) {
                loop.declarationInitializer().ifPresent(value -> statement(function, value));
                loop.expressionInitializer().ifPresent(value -> expression(function, value));
                expression(function, loop.condition());
                loop.update().ifPresent(value -> expression(function, value));
                statements(function, loop.body());
            } else if (statement instanceof HirAggregateIteration loop) {
                expression(function, loop.source());
                statements(function, loop.body());
            } else if (statement instanceof HirUnitIteration loop) {
                loop.filters().forEach(value -> expression(function, value));
                statements(function, loop.body());
            } else if (statement instanceof HirBuildingIteration loop) {
                loop.filters().forEach(value -> expression(function, value));
                statements(function, loop.body());
            } else if (statement instanceof HirUnitControl control) {
                control.arguments().forEach(value -> expression(function, value));
            } else if (statement instanceof HirBuildingControl control) {
                expression(function, control.target());
                control.arguments().forEach(value -> expression(function, value));
            } else if (statement instanceof HirCollectionSet update) {
                expression(function, update.value());
            } else if (statement instanceof HirDynamicCollectionSet update) {
                expression(function, update.index());
                expression(function, update.value());
            } else if (statement instanceof HirReturn returned) {
                returned.value().ifPresent(value -> expression(function, value));
            }
        }

        private void printExpression(String function, HirExpression value) {
            if (value instanceof HirText) return;
            if (value instanceof HirStringConcat concat) {
                printExpression(function, concat.left());
                printExpression(function, concat.right());
                return;
            }
            if (value instanceof HirBinary binary && binary.type() == ValueType.STRING
                && "+".equals(binary.operator())) {
                printExpression(function, binary.left());
                printExpression(function, binary.right());
                return;
            }
            expression(function, value);
        }

        private void expression(String function, HirExpression value) {
            if (value instanceof HirText text) {
                literal(text.value());
            } else if (value instanceof HirStringConcat concat) {
                concatenation(function, concat);
                expression(function, concat.left());
                expression(function, concat.right());
            } else if (value instanceof HirStringLength length) {
                expression(function, length.value());
            } else if (value instanceof HirStringSnapshot snapshot) {
                snapshot(function, snapshot);
                expression(function, snapshot.value());
            } else if (value instanceof HirStringComparison comparison) {
                expression(function, comparison.left());
                expression(function, comparison.right());
            } else if (value instanceof HirUnary unary) {
                expression(function, unary.operand());
            } else if (value instanceof HirBinary binary) {
                expression(function, binary.left());
                expression(function, binary.right());
            } else if (value instanceof HirAssignment assignment) {
                expression(function, assignment.value());
            } else if (value instanceof HirMemberAccess member) {
                expression(function, member.target());
            } else if (value instanceof HirIntrinsicCall call) {
                call.arguments().forEach(argument -> expression(function, argument));
            } else if (value instanceof HirFunctionCall call) {
                if (call.type() == ValueType.STRING) callResult(function, call);
                call.arguments().forEach(argument -> expression(function, argument));
            } else if (value instanceof HirMethodCall call) {
                if (call.type() == ValueType.STRING) callResult(function, call.stringResultAllocationId());
                expression(function, call.receiver());
                call.arguments().forEach(argument -> expression(function, argument));
            } else if (value instanceof HirNewObject allocation) {
                objectFields(allocation);
                allocation.arguments().forEach(argument -> expression(function, argument));
            } else if (value instanceof HirObjectFieldRead read) {
                expression(function, read.target());
            } else if (value instanceof HirObjectFieldAssignment assignment) {
                expression(function, assignment.target());
                expression(function, assignment.value());
            } else if (value instanceof HirArrayLiteral array) {
                array.elements().forEach(element -> expression(function, element));
            } else if (value instanceof HirTupleLiteral tuple) {
                tuple.elements().forEach(element -> expression(function, element));
            } else if (value instanceof HirCollectionLiteral collection) {
                collection.elements().forEach(element -> expression(function, element));
            } else if (value instanceof HirIndexAccess access) {
                expression(function, access.target());
                expression(function, access.index());
            } else if (value instanceof HirDynamicIndexAccess access) {
                expression(function, access.target());
                expression(function, access.index());
            } else if (value instanceof HirCollectionContains contains) {
                expression(function, contains.target());
                expression(function, contains.candidate());
            } else if (value instanceof HirUnitQuery query) {
                query.filters().forEach(filter -> expression(function, filter));
            } else if (value instanceof HirUnitQuerySize size) {
                expression(function, size.query());
            } else if (value instanceof HirUnitQueryGet get) {
                expression(function, get.query());
                expression(function, get.index());
            } else if (value instanceof HirBuildingQuery query) {
                query.filters().forEach(filter -> expression(function, filter));
            } else if (value instanceof HirBuildingQuerySize size) {
                expression(function, size.query());
            } else if (value instanceof HirBuildingQueryGet get) {
                expression(function, get.query());
                expression(function, get.index());
            }
        }

        private void literal(String value) {
            if (literals.containsKey(value)) return;
            Requirement requirement = add(Kind.LITERAL, value.length(), value, null, null, null);
            literals.put(value, requirement);
        }

        private void concatenation(String function, HirStringConcat concat) {
            if (concatenations.containsKey(concat.allocationId())) return;
            PhysicalMemoryLayout.StorageKey storage = new PhysicalMemoryLayout.StorageKey(
                function, "@stringConcat:" + concat.allocationId());
            Requirement requirement = add(Kind.CONCATENATION, capacities.concatenation(concat), null,
                concat.allocationId(), null, null, null, null, storage);
            concatenations.put(concat.allocationId(), requirement);
        }

        private void variable(String function, String name, int capacity) {
            PhysicalMemoryLayout.StorageKey variable = new PhysicalMemoryLayout.StorageKey(function, name);
            if (variables.containsKey(variable)) return;
            PhysicalMemoryLayout.StorageKey storage = new PhysicalMemoryLayout.StorageKey(function, "@string:" + name);
            Requirement requirement = add(Kind.VARIABLE, capacity, null, null, variable, null,
                null, null, storage);
            variables.put(variable, requirement);
        }

        private void snapshot(String function, HirStringSnapshot snapshot) {
            if (snapshots.containsKey(snapshot.allocationId())) return;
            PhysicalMemoryLayout.StorageKey storage = new PhysicalMemoryLayout.StorageKey(
                function, "@stringSnapshot:" + snapshot.allocationId());
            Requirement requirement = add(Kind.SNAPSHOT, capacities.snapshot(snapshot), null,
                snapshot.allocationId(), null, null, null, null, storage);
            snapshots.put(snapshot.allocationId(), requirement);
        }

        private void callResult(String function, HirFunctionCall call) {
            callResult(function, call.stringResultAllocationId());
        }

        private void callResult(String function, int allocationId) {
            if (callResults.containsKey(allocationId)) return;
            PhysicalMemoryLayout.StorageKey storage = new PhysicalMemoryLayout.StorageKey(
                function, "@stringCallResult:" + allocationId);
            Requirement requirement = add(Kind.CALL_RESULT, capacities.callResult(allocationId), null, allocationId,
                null, null, null, null, storage);
            callResults.put(allocationId, requirement);
        }

        private void functionResult(String function, int capacity) {
            if (functionResults.containsKey(function)) return;
            PhysicalMemoryLayout.StorageKey storage = new PhysicalMemoryLayout.StorageKey(function, "@stringResult");
            Requirement requirement = add(Kind.FUNCTION_RESULT, capacity, null, null, null, function,
                null, null, storage);
            functionResults.put(function, requirement);
        }

        private Requirement add(Kind kind, int capacity, String literal, Integer concat,
                                PhysicalMemoryLayout.StorageKey variable, String result) {
            PhysicalMemoryLayout.StorageKey storage = new PhysicalMemoryLayout.StorageKey(
                "@string", kind.name().toLowerCase() + ":" + requirements.size());
            return add(kind, capacity, literal, concat, variable, result, null, null, storage);
        }

        private Requirement add(Kind kind, int capacity, String literal, Integer concat,
                                PhysicalMemoryLayout.StorageKey variable, String result,
                                StringRuntimeLayout.ObjectFieldKey objectField,
                                StringRuntimeLayout.AggregateElementKey aggregateElement,
                                PhysicalMemoryLayout.StorageKey storage) {
            int bounded = Math.min(defaultCapacity, Math.max(0, capacity));
            Requirement requirement = new Requirement(kind, requirements.size() + 1, bounded, literal,
                concat, variable, result, objectField, aggregateElement, storage);
            requirements.add(requirement);
            return requirement;
        }

        private void objectFields(HirNewObject allocation) {
            if (allocation.allocationKind() != HirNewObject.AllocationKind.FIXED) return;
            HirClass type = classes.get(allocation.className());
            if (type == null) return;
            for (HirClass.Field field : type.fields()) {
                if (field.type() == ValueType.STRING) {
                    objectField(allocation.allocationId(), field.name(), null);
                } else if (field.type() instanceof TupleType tuple) {
                    for (int index = 0; index < tuple.elementTypes().size(); index++) {
                        if (tuple.elementTypes().get(index) == ValueType.STRING) {
                            objectField(allocation.allocationId(), field.name(), index);
                        }
                    }
                }
            }
        }

        private void objectField(int allocationId, String field, Integer element) {
            StringRuntimeLayout.ObjectFieldKey key = new StringRuntimeLayout.ObjectFieldKey(allocationId, field, element);
            if (objectFields.containsKey(key)) return;
            PhysicalMemoryLayout.StorageKey storage = new PhysicalMemoryLayout.StorageKey(
                "@stringObject:" + allocationId, field + (element == null ? "" : ":" + element));
            Requirement requirement = add(Kind.OBJECT_FIELD, defaultCapacity, null, null, null, null,
                key, null, storage);
            objectFields.put(key, requirement);
        }

        private void aggregate(String function, HirVariableDeclaration declaration) {
            List<MplType> elements;
            if (declaration.type() instanceof TupleType tuple) {
                elements = tuple.elementTypes();
            } else if (declaration.type() instanceof CollectionType collection) {
                int size = declaration.initializer() instanceof HirArrayLiteral array ? array.elements().size()
                    : declaration.initializer() instanceof HirCollectionLiteral values ? values.elements().size() : 0;
                elements = java.util.Collections.nCopies(size, collection.elementType());
            } else {
                return;
            }
            for (int index = 0; index < elements.size(); index++) {
                if (elements.get(index) != ValueType.STRING) continue;
                StringRuntimeLayout.AggregateElementKey key =
                    new StringRuntimeLayout.AggregateElementKey(function, declaration.name(), index);
                if (aggregateElements.containsKey(key)) continue;
                PhysicalMemoryLayout.StorageKey storage = new PhysicalMemoryLayout.StorageKey(
                    function, "@stringAggregate:" + declaration.name() + ":" + index);
                Requirement requirement = add(Kind.AGGREGATE_ELEMENT,
                    capacities.aggregateElement(function, declaration.name(), index), null, null, null, null,
                    null, key, storage);
                aggregateElements.put(key, requirement);
            }
        }
    }
}
