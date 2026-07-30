package com.arc.mpl.memory;

import com.arc.mpl.hir.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Computes whole-program UTF-16 capacity bounds for compiler-owned String buffers.
 *
 * <p>The semantic front end deliberately uses the target maximum for an unknown
 * function parameter or result. Once the complete HIR call graph is available,
 * this pass propagates the actual argument bounds through the non-recursive
 * function graph. It never widens the language limit and falls back to that
 * limit for an uncalled function, mutable storage, or an opaque String source.</p>
 */
final class StringCapacityAnalyzer {
    Analysis analyze(HirProgram program, int maximumCapacity) {
        if (maximumCapacity < 0) throw new IllegalArgumentException("String 最大容量不能为负数");
        Map<String, HirFunction> functions = new LinkedHashMap<>();
        for (HirFunction function : program.functions()) functions.put(function.name(), function);

        Pass discovery = new Pass(functions, Map.of(), Map.of(), Map.of(), maximumCapacity);
        discovery.run(program);

        Map<ParameterKey, Integer> parameters = new LinkedHashMap<>();
        Map<String, Integer> results = new LinkedHashMap<>();
        Map<PhysicalMemoryLayout.StorageKey, Integer> variables = new LinkedHashMap<>();
        for (HirFunction function : program.functions()) {
            boolean called = discovery.calledFunctions.contains(function.name());
            for (HirFunctionParameter parameter : function.parameters()) {
                if (parameter.type() == ValueType.STRING) {
                    parameters.put(new ParameterKey(function.name(), parameter.name()), called ? 0 : maximumCapacity);
                }
            }
            if (function.returnType() == ValueType.STRING) results.put(function.name(), 0);
        }

        int maximumRounds = Math.max(1, maximumCapacity + parameters.size() + results.size() + 2);
        Pass stable = null;
        for (int round = 0; round < maximumRounds; round++) {
            Pass pass = new Pass(functions, parameters, results, variables, maximumCapacity);
            pass.run(program);
            boolean changed = merge(parameters, pass.incomingParameters)
                | merge(results, pass.returnCapacities)
                | merge(variables, pass.variableCapacities);
            stable = pass;
            if (!changed) break;
            if (round == maximumRounds - 1) {
                throw new IllegalStateException("String 容量分析未能在非递归调用图上收敛");
            }
        }

        // The pass that observed convergence used the final maps because no
        // merge changed them. Running once more keeps this invariant explicit
        // if the merge implementation is extended later.
        stable = new Pass(functions, parameters, results, variables, maximumCapacity);
        stable.run(program);
        return new Analysis(variables, results, stable.concatenationCapacities,
            stable.snapshotCapacities, stable.callResultCapacities, stable.aggregateCapacities,
            maximumCapacity);
    }

    private static <K> boolean merge(Map<K, Integer> target, Map<K, Integer> source) {
        boolean changed = false;
        for (Map.Entry<K, Integer> entry : source.entrySet()) {
            int previous = target.getOrDefault(entry.getKey(), 0);
            if (entry.getValue() > previous) {
                target.put(entry.getKey(), entry.getValue());
                changed = true;
            }
        }
        return changed;
    }

    record Analysis(Map<PhysicalMemoryLayout.StorageKey, Integer> variables,
                    Map<String, Integer> functionResults,
                    Map<Integer, Integer> concatenations,
                    Map<Integer, Integer> snapshots,
                    Map<Integer, Integer> callResults,
                    Map<StringRuntimeLayout.AggregateElementKey, Integer> aggregateElements,
                    int maximumCapacity) {
        Analysis {
            variables = immutable(variables);
            functionResults = immutable(functionResults);
            concatenations = immutable(concatenations);
            snapshots = immutable(snapshots);
            callResults = immutable(callResults);
            aggregateElements = immutable(aggregateElements);
        }

        int variable(String function, String name, int fallback) {
            return variables.getOrDefault(new PhysicalMemoryLayout.StorageKey(function, name), fallback);
        }

        int functionResult(String function) {
            return functionResults.getOrDefault(function, maximumCapacity);
        }

        int concatenation(HirStringConcat value) {
            return concatenations.getOrDefault(value.allocationId(), value.maxCodeUnits());
        }

        int snapshot(HirStringSnapshot value) {
            return snapshots.getOrDefault(value.allocationId(), value.maxCodeUnits());
        }

        int callResult(HirFunctionCall value) {
            return callResults.getOrDefault(value.stringResultAllocationId(), maximumCapacity);
        }

        int aggregateElement(String function, String variable, int element) {
            return aggregateElements.getOrDefault(
                new StringRuntimeLayout.AggregateElementKey(function, variable, element), maximumCapacity);
        }

        private static <K, V> Map<K, V> immutable(Map<K, V> source) {
            return java.util.Collections.unmodifiableMap(new LinkedHashMap<>(source));
        }
    }

    private record ParameterKey(String function, String parameter) { }

    private static final class Environment {
        private final Map<String, Integer> strings;
        private final Map<String, PhysicalMemoryLayout.StorageKey> keys;

        private Environment() {
            this.strings = new LinkedHashMap<>();
            this.keys = new LinkedHashMap<>();
        }
        private Environment(Map<String, Integer> strings,
                            Map<String, PhysicalMemoryLayout.StorageKey> keys) {
            this.strings = new LinkedHashMap<>(strings);
            this.keys = new LinkedHashMap<>(keys);
        }
        private Environment copy() { return new Environment(strings, keys); }
        private int get(String name, int fallback) { return strings.getOrDefault(name, fallback); }
        private void put(String name, PhysicalMemoryLayout.StorageKey key, int capacity) {
            strings.put(name, capacity);
            keys.put(name, key);
        }
        private PhysicalMemoryLayout.StorageKey key(String name) { return keys.get(name); }
        private void merge(Environment other) {
            other.strings.forEach((name, capacity) -> strings.merge(name, capacity, Math::max));
        }
    }

    private static final class Pass {
        private final Map<String, HirFunction> functions;
        private final Map<ParameterKey, Integer> parameters;
        private final Map<String, Integer> results;
        private final Map<PhysicalMemoryLayout.StorageKey, Integer> knownVariables;
        private final int maximumCapacity;
        private final Map<ParameterKey, Integer> incomingParameters = new LinkedHashMap<>();
        private final Map<String, Integer> returnCapacities = new LinkedHashMap<>();
        private final Map<PhysicalMemoryLayout.StorageKey, Integer> variableCapacities = new LinkedHashMap<>();
        private final Map<Integer, Integer> concatenationCapacities = new LinkedHashMap<>();
        private final Map<Integer, Integer> snapshotCapacities = new LinkedHashMap<>();
        private final Map<Integer, Integer> callResultCapacities = new LinkedHashMap<>();
        private final Map<StringRuntimeLayout.AggregateElementKey, Integer> aggregateCapacities = new LinkedHashMap<>();
        private final Set<String> calledFunctions = new LinkedHashSet<>();

        private Pass(Map<String, HirFunction> functions, Map<ParameterKey, Integer> parameters,
                     Map<String, Integer> results,
                     Map<PhysicalMemoryLayout.StorageKey, Integer> knownVariables,
                     int maximumCapacity) {
            this.functions = functions;
            this.parameters = parameters;
            this.results = results;
            this.knownVariables = knownVariables;
            this.maximumCapacity = maximumCapacity;
        }

        private void run(HirProgram program) {
            Environment globals = new Environment();
            statements(null, program.statements(), globals);
            Map<String, Integer> globalStrings = new LinkedHashMap<>(globals.strings);
            Map<String, PhysicalMemoryLayout.StorageKey> globalKeys = new LinkedHashMap<>(globals.keys);
            globalKeys.forEach((name, key) -> globalStrings.put(name,
                Math.max(globalStrings.getOrDefault(name, 0), knownVariables.getOrDefault(key, 0))));
            for (HirFunction function : program.functions()) {
                Environment environment = new Environment(globalStrings, globalKeys);
                for (HirFunctionParameter parameter : function.parameters()) {
                    if (parameter.type() != ValueType.STRING) continue;
                    int capacity = parameters.getOrDefault(
                        new ParameterKey(function.name(), parameter.name()), maximumCapacity);
                    PhysicalMemoryLayout.StorageKey key =
                        new PhysicalMemoryLayout.StorageKey(function.name(), parameter.name());
                    environment.put(parameter.name(), key, capacity);
                    variableCapacities.put(key, capacity);
                }
                statements(function.name(), function.body(), environment);
            }
        }

        private void statements(String function, List<HirStatement> statements, Environment environment) {
            for (HirStatement statement : statements) statement(function, statement, environment);
        }

        private void statement(String function, HirStatement statement, Environment environment) {
            if (statement instanceof HirVariableDeclaration declaration) {
                int initializer = expression(function, declaration.initializer(), environment);
                if (declaration.type() == ValueType.STRING) {
                    PhysicalMemoryLayout.StorageKey key =
                        new PhysicalMemoryLayout.StorageKey(function, declaration.name());
                    int capacity = bound(Math.max(initializer, knownVariables.getOrDefault(key, 0)));
                    environment.put(declaration.name(), key, capacity);
                    variableCapacities.merge(key, capacity, Math::max);
                }
                aggregateDeclaration(function, declaration, environment);
            } else if (statement instanceof HirExpressionStatement value) {
                expression(function, value.expression(), environment);
            } else if (statement instanceof HirPrintStatement print) {
                print.arguments().forEach(value -> expression(function, value, environment));
            } else if (statement instanceof HirDraw draw) {
                draw.arguments().forEach(value -> expression(function, value, environment));
            } else if (statement instanceof HirBlock block) {
                statements(function, block.statements(), environment.copy());
            } else if (statement instanceof HirIf branch) {
                expression(function, branch.condition(), environment);
                Environment thenEnvironment = environment.copy();
                statements(function, branch.thenBody(), thenEnvironment);
                environment.merge(thenEnvironment);
                branch.elseBody().ifPresent(body -> {
                    Environment elseEnvironment = environment.copy();
                    statements(function, body, elseEnvironment);
                    environment.merge(elseEnvironment);
                });
            } else if (statement instanceof HirWhile loop) {
                expression(function, loop.condition(), environment);
                Environment body = environment.copy();
                statements(function, loop.body(), body);
                environment.merge(body);
            } else if (statement instanceof HirDoWhile loop) {
                Environment body = environment.copy();
                statements(function, loop.body(), body);
                expression(function, loop.condition(), body);
                environment.merge(body);
            } else if (statement instanceof HirFor loop) {
                Environment body = environment.copy();
                loop.declarationInitializer().ifPresent(value -> statement(function, value, body));
                loop.expressionInitializer().ifPresent(value -> expression(function, value, body));
                expression(function, loop.condition(), body);
                Environment iteration = body.copy();
                statements(function, loop.body(), iteration);
                loop.update().ifPresent(value -> expression(function, value, iteration));
                environment.merge(iteration);
            } else if (statement instanceof HirAggregateIteration loop) {
                expression(function, loop.source(), environment);
                statements(function, loop.body(), environment.copy());
            } else if (statement instanceof HirUnitIteration loop) {
                loop.filters().forEach(value -> expression(function, value, environment));
                statements(function, loop.body(), environment.copy());
            } else if (statement instanceof HirBuildingIteration loop) {
                loop.filters().forEach(value -> expression(function, value, environment));
                statements(function, loop.body(), environment.copy());
            } else if (statement instanceof HirUnitControl control) {
                control.arguments().forEach(value -> expression(function, value, environment));
            } else if (statement instanceof HirBuildingControl control) {
                expression(function, control.target(), environment);
                control.arguments().forEach(value -> expression(function, value, environment));
            } else if (statement instanceof HirCollectionSet update) {
                int capacity = expression(function, update.value(), environment);
                if (update.value().type() == ValueType.STRING) {
                    StringRuntimeLayout.AggregateElementKey key = aggregateKey(function, update.target(), update.index());
                    aggregateCapacities.merge(key, capacity, Math::max);
                }
            } else if (statement instanceof HirDynamicCollectionSet update) {
                expression(function, update.index(), environment);
                expression(function, update.value(), environment);
            } else if (statement instanceof HirReturn returned) {
                returned.value().ifPresent(value -> {
                    int capacity = expression(function, value, environment);
                    if (value.type() == ValueType.STRING && function != null) {
                        returnCapacities.merge(function, capacity, Math::max);
                    }
                });
            }
        }

        private int expression(String function, HirExpression value, Environment environment) {
            if (value instanceof HirText text) return bound(text.value().length());
            if (value instanceof HirVariable variable) {
                return variable.type() == ValueType.STRING
                    ? environment.get(variable.name(), maximumCapacity) : 0;
            }
            if (value instanceof HirStringConcat concat) {
                int propagated = add(expression(function, concat.left(), environment),
                    expression(function, concat.right(), environment));
                int capacity = Math.min(bound(concat.maxCodeUnits()), propagated);
                concatenationCapacities.put(concat.allocationId(), capacity);
                return capacity;
            }
            if (value instanceof HirStringSnapshot snapshot) {
                int capacity = Math.min(bound(snapshot.maxCodeUnits()),
                    expression(function, snapshot.value(), environment));
                snapshotCapacities.put(snapshot.allocationId(), capacity);
                return capacity;
            }
            if (value instanceof HirStringLength length) {
                expression(function, length.value(), environment);
                return 0;
            }
            if (value instanceof HirStringComparison comparison) {
                expression(function, comparison.left(), environment);
                expression(function, comparison.right(), environment);
                return 0;
            }
            if (value instanceof HirBinary binary) {
                int left = expression(function, binary.left(), environment);
                int right = expression(function, binary.right(), environment);
                return binary.type() == ValueType.STRING && "+".equals(binary.operator()) ? add(left, right) : 0;
            }
            if (value instanceof HirUnary unary) {
                expression(function, unary.operand(), environment);
                return value.type() == ValueType.STRING ? maximumCapacity : 0;
            }
            if (value instanceof HirAssignment assignment) {
                int capacity = expression(function, assignment.value(), environment);
                if (assignment.type() == ValueType.STRING) {
                    PhysicalMemoryLayout.StorageKey key = environment.key(assignment.target());
                    if (key == null) key = new PhysicalMemoryLayout.StorageKey(function, assignment.target());
                    variableCapacities.merge(key, capacity, Math::max);
                    environment.put(assignment.target(), key,
                        Math.max(environment.get(assignment.target(), 0), capacity));
                }
                return capacity;
            }
            if (value instanceof HirMemberAccess member) {
                expression(function, member.target(), environment);
                return value.type() == ValueType.STRING ? maximumCapacity : 0;
            }
            if (value instanceof HirIntrinsicCall call) {
                call.arguments().forEach(argument -> expression(function, argument, environment));
                return value.type() == ValueType.STRING ? maximumCapacity : 0;
            }
            if (value instanceof HirFunctionCall call) {
                List<Integer> arguments = new ArrayList<>();
                for (HirExpression argument : call.arguments()) {
                    arguments.add(expression(function, argument, environment));
                }
                recordCall(call.function(), arguments);
                int capacity = call.type() == ValueType.STRING
                    ? results.getOrDefault(call.function(), maximumCapacity) : 0;
                if (call.type() == ValueType.STRING) {
                    callResultCapacities.put(call.stringResultAllocationId(), capacity);
                }
                return capacity;
            }
            if (value instanceof HirNewObject allocation) {
                List<Integer> arguments = new ArrayList<>();
                arguments.add(0); // hidden this handle
                for (HirExpression argument : allocation.arguments()) {
                    arguments.add(expression(function, argument, environment));
                }
                recordCall(allocation.constructorFunction(), arguments);
                return 0;
            }
            if (value instanceof HirObjectFieldRead read) {
                expression(function, read.target(), environment);
                return value.type() == ValueType.STRING ? maximumCapacity : 0;
            }
            if (value instanceof HirObjectFieldAssignment assignment) {
                expression(function, assignment.target(), environment);
                return expression(function, assignment.value(), environment);
            }
            if (value instanceof HirArrayLiteral array) {
                array.elements().forEach(element -> expression(function, element, environment));
                return 0;
            }
            if (value instanceof HirTupleLiteral tuple) {
                tuple.elements().forEach(element -> expression(function, element, environment));
                return 0;
            }
            if (value instanceof HirCollectionLiteral collection) {
                collection.elements().forEach(element -> expression(function, element, environment));
                return 0;
            }
            if (value instanceof HirIndexAccess access) {
                expression(function, access.target(), environment);
                expression(function, access.index(), environment);
                if (value.type() == ValueType.STRING && access.target() instanceof HirVariable variable
                    && access.index() instanceof HirConstant index) {
                    try {
                        int element = Integer.parseInt(index.mlogLiteral());
                        return aggregateCapacities.getOrDefault(
                            aggregateKey(function, variable.name(), element), maximumCapacity);
                    } catch (NumberFormatException ignored) {
                        return maximumCapacity;
                    }
                }
                return value.type() == ValueType.STRING ? maximumCapacity : 0;
            }
            if (value instanceof HirDynamicIndexAccess access) {
                expression(function, access.target(), environment);
                expression(function, access.index(), environment);
                return value.type() == ValueType.STRING ? maximumCapacity : 0;
            }
            if (value instanceof HirCollectionContains contains) {
                expression(function, contains.target(), environment);
                expression(function, contains.candidate(), environment);
                return 0;
            }
            if (value instanceof HirUnitQuery query) {
                query.filters().forEach(filter -> expression(function, filter, environment));
            } else if (value instanceof HirUnitQuerySize size) {
                expression(function, size.query(), environment);
            } else if (value instanceof HirUnitQueryGet get) {
                expression(function, get.query(), environment);
                expression(function, get.index(), environment);
            } else if (value instanceof HirBuildingQuery query) {
                query.filters().forEach(filter -> expression(function, filter, environment));
            } else if (value instanceof HirBuildingQuerySize size) {
                expression(function, size.query(), environment);
            } else if (value instanceof HirBuildingQueryGet get) {
                expression(function, get.query(), environment);
                expression(function, get.index(), environment);
            }
            return value.type() == ValueType.STRING ? maximumCapacity : 0;
        }

        private void aggregateDeclaration(String function, HirVariableDeclaration declaration, Environment environment) {
            List<HirExpression> elements;
            if (declaration.initializer() instanceof HirArrayLiteral array) elements = array.elements();
            else if (declaration.initializer() instanceof HirTupleLiteral tuple) elements = tuple.elements();
            else if (declaration.initializer() instanceof HirCollectionLiteral collection) elements = collection.elements();
            else return;
            for (int index = 0; index < elements.size(); index++) {
                HirExpression element = elements.get(index);
                if (element.type() != ValueType.STRING) continue;
                int capacity = expression(function, element, environment);
                aggregateCapacities.merge(
                    new StringRuntimeLayout.AggregateElementKey(function, declaration.name(), index),
                    capacity, Math::max);
            }
        }

        private void recordCall(String functionName, List<Integer> argumentCapacities) {
            calledFunctions.add(functionName);
            HirFunction function = functions.get(functionName);
            if (function == null) return;
            for (int index = 0; index < function.parameters().size() && index < argumentCapacities.size(); index++) {
                HirFunctionParameter parameter = function.parameters().get(index);
                if (parameter.type() == ValueType.STRING) {
                    incomingParameters.merge(new ParameterKey(functionName, parameter.name()),
                        bound(argumentCapacities.get(index)), Math::max);
                }
            }
        }

        private StringRuntimeLayout.AggregateElementKey aggregateKey(String function, String variable, int element) {
            StringRuntimeLayout.AggregateElementKey local =
                new StringRuntimeLayout.AggregateElementKey(function, variable, element);
            if (aggregateCapacities.containsKey(local)) return local;
            return new StringRuntimeLayout.AggregateElementKey(null, variable, element);
        }

        private int add(int left, int right) {
            long value = (long) left + right;
            return bound(value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value);
        }

        private int bound(int value) {
            return Math.min(maximumCapacity, Math.max(0, value));
        }
    }
}
