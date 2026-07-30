package com.arc.mpl.optimization;

import com.arc.mpl.hir.HirAggregateIteration;
import com.arc.mpl.hir.HirArrayLiteral;
import com.arc.mpl.hir.HirAssignment;
import com.arc.mpl.hir.HirBinary;
import com.arc.mpl.hir.HirBlock;
import com.arc.mpl.hir.HirBuildingControl;
import com.arc.mpl.hir.HirBuildingIteration;
import com.arc.mpl.hir.HirBuildingQuery;
import com.arc.mpl.hir.HirBuildingQueryGet;
import com.arc.mpl.hir.HirBuildingQuerySize;
import com.arc.mpl.hir.HirCollectionContains;
import com.arc.mpl.hir.HirCollectionLiteral;
import com.arc.mpl.hir.HirCollectionSet;
import com.arc.mpl.hir.HirConstant;
import com.arc.mpl.hir.HirDoWhile;
import com.arc.mpl.hir.HirDraw;
import com.arc.mpl.hir.HirDrawFlush;
import com.arc.mpl.hir.HirDynamicCollectionSet;
import com.arc.mpl.hir.HirDynamicIndexAccess;
import com.arc.mpl.hir.HirExpression;
import com.arc.mpl.hir.HirExpressionStatement;
import com.arc.mpl.hir.HirFor;
import com.arc.mpl.hir.HirFunction;
import com.arc.mpl.hir.HirFunctionCall;
import com.arc.mpl.hir.HirHardwareLink;
import com.arc.mpl.hir.HirIf;
import com.arc.mpl.hir.HirIndexAccess;
import com.arc.mpl.hir.HirIntrinsicCall;
import com.arc.mpl.hir.HirMemberAccess;
import com.arc.mpl.hir.HirNewObject;
import com.arc.mpl.hir.HirObjectFieldAssignment;
import com.arc.mpl.hir.HirObjectFieldRead;
import com.arc.mpl.hir.HirObjectRelease;
import com.arc.mpl.hir.HirPrintStatement;
import com.arc.mpl.hir.HirProgram;
import com.arc.mpl.hir.HirReturn;
import com.arc.mpl.hir.HirStatement;
import com.arc.mpl.hir.HirStringComparison;
import com.arc.mpl.hir.HirStringConcat;
import com.arc.mpl.hir.HirStringLength;
import com.arc.mpl.hir.HirStringSnapshot;
import com.arc.mpl.hir.HirText;
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
import com.arc.mpl.hir.MplType;
import com.arc.mpl.hir.ValueType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Conservatively classifies HIR functions before a function can be moved to a Worker shard.
 * Local scalar computation is pure; anything that can observe or mutate the game/runtime is retained
 * on Main until a stronger ownership proof exists.
 */
public final class HirEffectAnalyzer {
    public Analysis analyze(HirProgram program) {
        Objects.requireNonNull(program, "program");
        Map<String, HirFunction> functions = new LinkedHashMap<>();
        for (HirFunction function : program.functions()) {
            if (functions.put(function.name(), function) != null) {
                throw new IllegalArgumentException("重复 HIR 函数：" + function.name());
            }
        }
        Set<String> recursive = recursiveFunctions(functions);
        Map<String, FunctionEffect> summaries = new LinkedHashMap<>();
        for (HirFunction function : program.functions()) {
            summaries.put(function.name(), recursive.contains(function.name())
                ? FunctionEffect.recursive(function) : FunctionEffect.empty(function));
        }
        boolean changed;
        do {
            changed = false;
            for (HirFunction function : program.functions()) {
                FunctionEffect next = inspect(function, functions, summaries, recursive.contains(function.name()));
                if (!next.equals(summaries.get(function.name()))) {
                    summaries.put(function.name(), next);
                    changed = true;
                }
            }
        } while (changed);
        return new Analysis(summaries);
    }

    private FunctionEffect inspect(HirFunction function, Map<String, HirFunction> functions,
                                   Map<String, FunctionEffect> summaries, boolean recursive) {
        EnumSet<EffectKind> effects = EnumSet.noneOf(EffectKind.class);
        List<String> reasons = new ArrayList<>();
        Set<String> locals = new HashSet<>();
        function.parameters().forEach(parameter -> locals.add(parameter.name()));
        collectDeclaredNames(function.body(), locals);
        inspectStatements(function.body(), locals, functions, summaries, effects, reasons);
        if (recursive) add(effects, reasons, EffectKind.UNKNOWN_CALL, "递归调用不能使用静态 Worker ABI");
        if (!scalar(function.returnType()) || function.parameters().stream().anyMatch(parameter -> !scalar(parameter.type()))) {
            add(effects, reasons, EffectKind.NON_SCALAR_ABI, "参数和返回值必须全部是 Int、Float 或 Bool");
        }
        return new FunctionEffect(function.name(), effects, reasons, scalar(function.returnType()),
            function.parameters().stream().allMatch(parameter -> scalar(parameter.type())));
    }

    private void inspectStatements(List<HirStatement> statements, Set<String> locals,
                                   Map<String, HirFunction> functions, Map<String, FunctionEffect> summaries,
                                   EnumSet<EffectKind> effects, List<String> reasons) {
        for (HirStatement statement : statements) inspectStatement(statement, locals, functions, summaries, effects, reasons);
    }

    private void inspectStatement(HirStatement statement, Set<String> locals,
                                  Map<String, HirFunction> functions, Map<String, FunctionEffect> summaries,
                                  EnumSet<EffectKind> effects, List<String> reasons) {
        if (statement instanceof HirVariableDeclaration declaration) {
            inspectExpression(declaration.initializer(), locals, functions, summaries, effects, reasons);
            if (!scalar(declaration.type())) add(effects, reasons, EffectKind.NON_SCALAR_ABI,
                "局部聚合值不能跨 shard 传输");
            if (declaration.ownsPooledObject()) add(effects, reasons, EffectKind.ALLOCATION, "对象池分配属于 Main runtime");
            return;
        }
        if (statement instanceof HirExpressionStatement expression) {
            inspectExpression(expression.expression(), locals, functions, summaries, effects, reasons); return;
        }
        if (statement instanceof HirPrintStatement) {
            add(effects, reasons, EffectKind.HARDWARE_IO, "print 写入外部 Message"); return;
        }
        if (statement instanceof HirDraw || statement instanceof HirDrawFlush) {
            add(effects, reasons, EffectKind.HARDWARE_IO, "draw 写入外部 Display"); return;
        }
        if (statement instanceof HirBlock block) {
            inspectStatements(block.statements(), locals, functions, summaries, effects, reasons); return;
        }
        if (statement instanceof HirIf branch) {
            inspectExpression(branch.condition(), locals, functions, summaries, effects, reasons);
            inspectStatements(branch.thenBody(), locals, functions, summaries, effects, reasons);
            branch.elseBody().ifPresent(body -> inspectStatements(body, locals, functions, summaries, effects, reasons)); return;
        }
        if (statement instanceof HirWhile loop) {
            inspectExpression(loop.condition(), locals, functions, summaries, effects, reasons);
            inspectStatements(loop.body(), locals, functions, summaries, effects, reasons); return;
        }
        if (statement instanceof HirDoWhile loop) {
            inspectStatements(loop.body(), locals, functions, summaries, effects, reasons);
            inspectExpression(loop.condition(), locals, functions, summaries, effects, reasons); return;
        }
        if (statement instanceof HirFor loop) {
            loop.declarationInitializer().ifPresent(value -> inspectStatement(value, locals, functions, summaries, effects, reasons));
            loop.expressionInitializer().ifPresent(value -> inspectExpression(value, locals, functions, summaries, effects, reasons));
            inspectExpression(loop.condition(), locals, functions, summaries, effects, reasons);
            loop.update().ifPresent(value -> inspectExpression(value, locals, functions, summaries, effects, reasons));
            inspectStatements(loop.body(), locals, functions, summaries, effects, reasons); return;
        }
        if (statement instanceof HirUnitIteration || statement instanceof HirUnitControl) {
            add(effects, reasons, EffectKind.UNIT_CONTROL, "Unit runtime 只能由 owner shard 执行");
            return;
        }
        if (statement instanceof HirBuildingIteration || statement instanceof HirBuildingControl) {
            add(effects, reasons, EffectKind.HARDWARE_IO, "Building 访问属于外部硬件 owner");
            return;
        }
        if (statement instanceof HirCollectionSet update) {
            inspectExpression(update.value(), locals, functions, summaries, effects, reasons);
            add(effects, reasons, EffectKind.PHYSICAL_MEMORY, "集合写入需要物理 Memory"); return;
        }
        if (statement instanceof HirDynamicCollectionSet update) {
            inspectExpression(update.index(), locals, functions, summaries, effects, reasons);
            inspectExpression(update.value(), locals, functions, summaries, effects, reasons);
            add(effects, reasons, EffectKind.PHYSICAL_MEMORY, "动态集合写入需要物理 Memory"); return;
        }
        if (statement instanceof HirAggregateIteration iteration) {
            add(effects, reasons, EffectKind.PHYSICAL_MEMORY, "聚合遍历依赖物理存储");
            inspectExpression(iteration.source(), locals, functions, summaries, effects, reasons);
            inspectStatements(iteration.body(), locals, functions, summaries, effects, reasons); return;
        }
        if (statement instanceof HirReturn returned) {
            returned.value().ifPresent(value -> inspectExpression(value, locals, functions, summaries, effects, reasons));
            if (!returned.cleanup().isEmpty()) add(effects, reasons, EffectKind.ALLOCATION, "返回清理包含对象生命周期管理");
            return;
        }
        if (statement instanceof HirObjectRelease) add(effects, reasons, EffectKind.ALLOCATION, "对象释放属于 Main runtime");
    }

    private void inspectExpression(HirExpression expression, Set<String> locals,
                                   Map<String, HirFunction> functions, Map<String, FunctionEffect> summaries,
                                   EnumSet<EffectKind> effects, List<String> reasons) {
        if (expression instanceof HirConstant || expression instanceof HirText) return;
        if (expression instanceof HirVariable variable) {
            if (!locals.contains(variable.name())) add(effects, reasons, EffectKind.READS_STATE,
                "函数捕获了外部变量：" + variable.name());
            return;
        }
        if (expression instanceof HirHardwareLink) {
            add(effects, reasons, EffectKind.HARDWARE_IO, "函数直接捕获了硬件链接"); return;
        }
        if (expression instanceof HirUnary unary) {
            inspectExpression(unary.operand(), locals, functions, summaries, effects, reasons); return;
        }
        if (expression instanceof HirBinary binary) {
            inspectExpression(binary.left(), locals, functions, summaries, effects, reasons);
            inspectExpression(binary.right(), locals, functions, summaries, effects, reasons); return;
        }
        if (expression instanceof HirAssignment assignment) {
            inspectExpression(assignment.value(), locals, functions, summaries, effects, reasons);
            if (!locals.contains(assignment.target())) add(effects, reasons, EffectKind.WRITES_STATE,
                "函数写入了外部变量：" + assignment.target()); return;
        }
        if (expression instanceof HirIntrinsicCall call) {
            for (HirExpression argument : call.arguments()) inspectExpression(argument, locals, functions, summaries, effects, reasons);
            if (!"Math".equals(call.namespace()) && !"Int".equals(call.namespace())) {
                add(effects, reasons, EffectKind.READS_STATE, "非纯 intrinsic：" + call.namespace() + "." + call.name());
            }
            return;
        }
        if (expression instanceof HirFunctionCall call) {
            for (HirExpression argument : call.arguments()) inspectExpression(argument, locals, functions, summaries, effects, reasons);
            FunctionEffect summary = summaries.get(call.function());
            if (summary == null) add(effects, reasons, EffectKind.UNKNOWN_CALL, "未知函数：" + call.function());
            else {
                effects.addAll(summary.effects());
                reasons.addAll(summary.reasons());
            }
            return;
        }
        if (expression instanceof HirMemberAccess member) {
            inspectExpression(member.target(), locals, functions, summaries, effects, reasons);
            add(effects, reasons, EffectKind.READS_STATE, "成员访问可能读取游戏状态"); return;
        }
        if (expression instanceof HirNewObject allocation) {
            allocation.arguments().forEach(value -> inspectExpression(value, locals, functions, summaries, effects, reasons));
            add(effects, reasons, EffectKind.ALLOCATION, "new 需要对象 runtime"); return;
        }
        if (expression instanceof HirObjectFieldRead read) {
            inspectExpression(read.target(), locals, functions, summaries, effects, reasons);
            add(effects, reasons, EffectKind.READS_STATE, "对象字段访问不属于纯数值 helper"); return;
        }
        if (expression instanceof HirObjectFieldAssignment assignment) {
            inspectExpression(assignment.target(), locals, functions, summaries, effects, reasons);
            inspectExpression(assignment.value(), locals, functions, summaries, effects, reasons);
            add(effects, reasons, EffectKind.WRITES_STATE, "对象字段写入不属于纯数值 helper"); return;
        }
        if (expression instanceof HirArrayLiteral || expression instanceof HirTupleLiteral
            || expression instanceof HirCollectionLiteral) {
            add(effects, reasons, EffectKind.NON_SCALAR_ABI, "聚合表达式不能作为 Worker helper 值");
            if (expression instanceof HirArrayLiteral array) array.elements().forEach(value -> inspectExpression(value, locals, functions, summaries, effects, reasons));
            if (expression instanceof HirTupleLiteral tuple) tuple.elements().forEach(value -> inspectExpression(value, locals, functions, summaries, effects, reasons));
            if (expression instanceof HirCollectionLiteral collection) collection.elements().forEach(value -> inspectExpression(value, locals, functions, summaries, effects, reasons));
            return;
        }
        if (expression instanceof HirIndexAccess access) {
            inspectExpression(access.target(), locals, functions, summaries, effects, reasons);
            inspectExpression(access.index(), locals, functions, summaries, effects, reasons);
            add(effects, reasons, EffectKind.PHYSICAL_MEMORY, "聚合索引依赖物理存储"); return;
        }
        if (expression instanceof HirDynamicIndexAccess access) {
            inspectExpression(access.target(), locals, functions, summaries, effects, reasons);
            inspectExpression(access.index(), locals, functions, summaries, effects, reasons);
            add(effects, reasons, EffectKind.PHYSICAL_MEMORY, "动态索引依赖物理 Memory"); return;
        }
        if (expression instanceof HirCollectionContains contains) {
            inspectExpression(contains.target(), locals, functions, summaries, effects, reasons);
            inspectExpression(contains.candidate(), locals, functions, summaries, effects, reasons);
            add(effects, reasons, EffectKind.PHYSICAL_MEMORY, "集合查询依赖物理存储"); return;
        }
        if (expression instanceof HirUnitQuery || expression instanceof HirUnitQuerySize
            || expression instanceof HirUnitQueryGet) {
            add(effects, reasons, EffectKind.UNIT_CONTROL, "Unit 查询依赖 Unit runtime"); return;
        }
        if (expression instanceof HirBuildingQuery || expression instanceof HirBuildingQuerySize
            || expression instanceof HirBuildingQueryGet) {
            add(effects, reasons, EffectKind.HARDWARE_IO, "Building 查询依赖外部硬件"); return;
        }
        if (expression instanceof HirStringConcat || expression instanceof HirStringLength
            || expression instanceof HirStringComparison || expression instanceof HirStringSnapshot) {
            add(effects, reasons, EffectKind.PHYSICAL_MEMORY, "String runtime 依赖物理存储"); return;
        }
        add(effects, reasons, EffectKind.UNKNOWN_CALL, "未识别的 HIR 表达式：" + expression.getClass().getSimpleName());
    }

    private void collectDeclaredNames(List<HirStatement> statements, Set<String> names) {
        for (HirStatement statement : statements) {
            if (statement instanceof HirVariableDeclaration declaration) names.add(declaration.name());
            else if (statement instanceof HirBlock block) collectDeclaredNames(block.statements(), names);
            else if (statement instanceof HirIf branch) {
                collectDeclaredNames(branch.thenBody(), names);
                branch.elseBody().ifPresent(body -> collectDeclaredNames(body, names));
            } else if (statement instanceof HirWhile loop) collectDeclaredNames(loop.body(), names);
            else if (statement instanceof HirDoWhile loop) collectDeclaredNames(loop.body(), names);
            else if (statement instanceof HirFor loop) {
                loop.declarationInitializer().ifPresent(value -> names.add(value.name()));
                collectDeclaredNames(loop.body(), names);
            } else if (statement instanceof HirUnitIteration iteration) {
                names.add(iteration.bindingName()); collectDeclaredNames(iteration.body(), names);
            } else if (statement instanceof HirBuildingIteration iteration) {
                names.add(iteration.bindingName()); collectDeclaredNames(iteration.body(), names);
            } else if (statement instanceof HirAggregateIteration iteration) {
                names.add(iteration.bindingName()); collectDeclaredNames(iteration.body(), names);
            }
        }
    }

    private Set<String> recursiveFunctions(Map<String, HirFunction> functions) {
        Map<String, Set<String>> graph = new LinkedHashMap<>();
        functions.forEach((name, function) -> {
            Set<String> calls = new LinkedHashSet<>();
            collectCalls(function.body(), calls);
            graph.put(name, calls.stream().filter(functions::containsKey).collect(java.util.stream.Collectors.toSet()));
        });
        Set<String> recursive = new HashSet<>();
        for (String function : functions.keySet()) markRecursive(function, function, graph, new ArrayDeque<>(), recursive);
        return recursive;
    }

    private void markRecursive(String start, String current, Map<String, Set<String>> graph,
                               ArrayDeque<String> path, Set<String> recursive) {
        if (path.contains(current)) { recursive.add(current); recursive.addAll(path); return; }
        path.addLast(current);
        for (String next : graph.getOrDefault(current, Set.of())) markRecursive(start, next, graph, path, recursive);
        path.removeLast();
    }

    private void collectCalls(List<HirStatement> statements, Set<String> calls) {
        for (HirStatement statement : statements) {
            if (statement instanceof HirVariableDeclaration declaration) collectCalls(declaration.initializer(), calls);
            else if (statement instanceof HirExpressionStatement expression) collectCalls(expression.expression(), calls);
            else if (statement instanceof HirBlock block) collectCalls(block.statements(), calls);
            else if (statement instanceof HirIf branch) {
                collectCalls(branch.condition(), calls); collectCalls(branch.thenBody(), calls);
                branch.elseBody().ifPresent(body -> collectCalls(body, calls));
            } else if (statement instanceof HirWhile loop) { collectCalls(loop.condition(), calls); collectCalls(loop.body(), calls); }
            else if (statement instanceof HirDoWhile loop) { collectCalls(loop.condition(), calls); collectCalls(loop.body(), calls); }
            else if (statement instanceof HirFor loop) {
                loop.declarationInitializer().ifPresent(value -> collectCalls(value.initializer(), calls));
                loop.expressionInitializer().ifPresent(value -> collectCalls(value, calls));
                collectCalls(loop.condition(), calls); loop.update().ifPresent(value -> collectCalls(value, calls)); collectCalls(loop.body(), calls);
            } else if (statement instanceof HirReturn returned) returned.value().ifPresent(value -> collectCalls(value, calls));
            else if (statement instanceof HirCollectionSet update) collectCalls(update.value(), calls);
            else if (statement instanceof HirDynamicCollectionSet update) { collectCalls(update.index(), calls); collectCalls(update.value(), calls); }
            else if (statement instanceof HirUnitControl control) control.arguments().forEach(value -> collectCalls(value, calls));
            else if (statement instanceof HirBuildingControl control) { collectCalls(control.target(), calls); control.arguments().forEach(value -> collectCalls(value, calls)); }
            else if (statement instanceof HirPrintStatement print) print.arguments().forEach(value -> collectCalls(value, calls));
            else if (statement instanceof HirDraw draw) draw.arguments().forEach(value -> collectCalls(value, calls));
            else if (statement instanceof HirUnitIteration iteration) { iteration.filters().forEach(value -> collectCalls(value, calls)); collectCalls(iteration.body(), calls); }
            else if (statement instanceof HirBuildingIteration iteration) { iteration.filters().forEach(value -> collectCalls(value, calls)); collectCalls(iteration.body(), calls); }
            else if (statement instanceof HirAggregateIteration iteration) { collectCalls(iteration.source(), calls); collectCalls(iteration.body(), calls); }
        }
    }

    private void collectCalls(HirExpression expression, Set<String> calls) {
        if (expression instanceof HirFunctionCall call) { calls.add(call.function()); call.arguments().forEach(value -> collectCalls(value, calls)); return; }
        if (expression instanceof HirUnary unary) { collectCalls(unary.operand(), calls); return; }
        if (expression instanceof HirBinary binary) { collectCalls(binary.left(), calls); collectCalls(binary.right(), calls); return; }
        if (expression instanceof HirAssignment assignment) { collectCalls(assignment.value(), calls); return; }
        if (expression instanceof HirIntrinsicCall call) { call.arguments().forEach(value -> collectCalls(value, calls)); return; }
        if (expression instanceof HirMemberAccess member) { collectCalls(member.target(), calls); return; }
        if (expression instanceof HirNewObject allocation) { allocation.arguments().forEach(value -> collectCalls(value, calls)); return; }
        if (expression instanceof HirObjectFieldRead read) { collectCalls(read.target(), calls); return; }
        if (expression instanceof HirObjectFieldAssignment assignment) { collectCalls(assignment.target(), calls); collectCalls(assignment.value(), calls); return; }
        if (expression instanceof HirArrayLiteral array) { array.elements().forEach(value -> collectCalls(value, calls)); return; }
        if (expression instanceof HirTupleLiteral tuple) { tuple.elements().forEach(value -> collectCalls(value, calls)); return; }
        if (expression instanceof HirCollectionLiteral collection) { collection.elements().forEach(value -> collectCalls(value, calls)); return; }
        if (expression instanceof HirIndexAccess access) { collectCalls(access.target(), calls); collectCalls(access.index(), calls); return; }
        if (expression instanceof HirDynamicIndexAccess access) { collectCalls(access.target(), calls); collectCalls(access.index(), calls); return; }
        if (expression instanceof HirCollectionContains contains) { collectCalls(contains.target(), calls); collectCalls(contains.candidate(), calls); return; }
        if (expression instanceof HirUnitQueryGet get) { collectCalls(get.query(), calls); collectCalls(get.index(), calls); return; }
        if (expression instanceof HirBuildingQueryGet get) { collectCalls(get.query(), calls); collectCalls(get.index(), calls); return; }
        if (expression instanceof HirUnitQuerySize size) { collectCalls(size.query(), calls); return; }
        if (expression instanceof HirBuildingQuerySize size) { collectCalls(size.query(), calls); return; }
        if (expression instanceof HirStringConcat concat) { collectCalls(concat.left(), calls); collectCalls(concat.right(), calls); return; }
        if (expression instanceof HirStringLength length) { collectCalls(length.value(), calls); return; }
        if (expression instanceof HirStringComparison comparison) { collectCalls(comparison.left(), calls); collectCalls(comparison.right(), calls); return; }
        if (expression instanceof HirStringSnapshot snapshot) collectCalls(snapshot.value(), calls);
    }

    private boolean scalar(MplType type) {
        return type == ValueType.INT || type == ValueType.FLOAT || type == ValueType.BOOL;
    }

    private void add(EnumSet<EffectKind> effects, List<String> reasons, EffectKind effect, String reason) {
        effects.add(effect);
        if (!reasons.contains(reason)) reasons.add(reason);
    }

    public enum EffectKind {
        READS_STATE,
        WRITES_STATE,
        HARDWARE_IO,
        UNIT_CONTROL,
        PHYSICAL_MEMORY,
        ALLOCATION,
        UNKNOWN_CALL,
        NON_SCALAR_ABI
    }

    public record FunctionEffect(String function, Set<EffectKind> effects, List<String> reasons,
                                 boolean scalarReturn, boolean scalarParameters) {
        public FunctionEffect {
            Objects.requireNonNull(function, "function");
            effects = Set.copyOf(Objects.requireNonNull(effects, "effects"));
            reasons = List.copyOf(Objects.requireNonNull(reasons, "reasons"));
        }

        static FunctionEffect empty(HirFunction function) {
            return new FunctionEffect(function.name(), Set.of(), List.of(), scalar(function.returnType()),
                function.parameters().stream().allMatch(parameter -> scalar(parameter.type())));
        }

        static FunctionEffect recursive(HirFunction function) {
            return new FunctionEffect(function.name(), Set.of(EffectKind.UNKNOWN_CALL),
                List.of("递归调用不能使用静态 Worker ABI"), scalar(function.returnType()),
                function.parameters().stream().allMatch(parameter -> scalar(parameter.type())));
        }

        public boolean pureNumeric() {
            return scalarReturn && scalarParameters && effects.isEmpty();
        }

        private static boolean scalar(MplType type) {
            return type == ValueType.INT || type == ValueType.FLOAT || type == ValueType.BOOL;
        }
    }

    public record Analysis(Map<String, FunctionEffect> functions) {
        public Analysis {
            functions = Map.copyOf(Objects.requireNonNull(functions, "functions"));
        }

        public FunctionEffect function(String name) {
            return functions.get(name);
        }

        public static Analysis empty() {
            return new Analysis(Map.of());
        }

        public List<FunctionEffect> pureNumericFunctions() {
            return functions.values().stream().filter(FunctionEffect::pureNumeric).toList();
        }
    }
}
