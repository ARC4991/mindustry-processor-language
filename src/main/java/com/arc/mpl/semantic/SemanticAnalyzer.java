package com.arc.mpl.semantic;

import com.arc.mpl.ast.AssignmentExpression;
import com.arc.mpl.ast.ArrayLiteral;
import com.arc.mpl.ast.BinaryExpression;
import com.arc.mpl.ast.BlockStatement;
import com.arc.mpl.ast.BooleanLiteral;
import com.arc.mpl.ast.BreakStatement;
import com.arc.mpl.ast.CallExpression;
import com.arc.mpl.ast.ContinueStatement;
import com.arc.mpl.ast.DoWhileStatement;
import com.arc.mpl.ast.Expression;
import com.arc.mpl.ast.ExpressionStatement;
import com.arc.mpl.ast.FloatLiteral;
import com.arc.mpl.ast.ForEachStatement;
import com.arc.mpl.ast.ForStatement;
import com.arc.mpl.ast.FunctionDeclaration;
import com.arc.mpl.ast.FunctionParameter;
import com.arc.mpl.ast.Identifier;
import com.arc.mpl.ast.IndexExpression;
import com.arc.mpl.ast.IfStatement;
import com.arc.mpl.ast.IntegerLiteral;
import com.arc.mpl.ast.LambdaExpression;
import com.arc.mpl.ast.MemberAccessExpression;
import com.arc.mpl.ast.MethodCallExpression;
import com.arc.mpl.ast.MilDrawStatement;
import com.arc.mpl.ast.NullLiteral;
import com.arc.mpl.ast.Program;
import com.arc.mpl.ast.ReturnStatement;
import com.arc.mpl.ast.Statement;
import com.arc.mpl.ast.StringLiteral;
import com.arc.mpl.ast.TupleLiteral;
import com.arc.mpl.ast.UnaryExpression;
import com.arc.mpl.ast.VariableDeclaration;
import com.arc.mpl.ast.WhileStatement;
import com.arc.mpl.diagnostic.Diagnostic;
import com.arc.mpl.diagnostic.Diagnostic.SourceSpan;
import com.arc.mpl.diagnostic.Severity;
import com.arc.mpl.hir.HirAssignment;
import com.arc.mpl.hir.HirAggregateIteration;
import com.arc.mpl.hir.HirBinary;
import com.arc.mpl.hir.HirBlock;
import com.arc.mpl.hir.HirBuildingControl;
import com.arc.mpl.hir.HirBuildingIteration;
import com.arc.mpl.hir.HirBuildingQuery;
import com.arc.mpl.hir.HirBuildingQueryGet;
import com.arc.mpl.hir.HirBuildingQuerySize;
import com.arc.mpl.hir.HirBreak;
import com.arc.mpl.hir.HirCollectionContains;
import com.arc.mpl.hir.HirCollectionLiteral;
import com.arc.mpl.hir.HirCollectionSet;
import com.arc.mpl.hir.HirConstant;
import com.arc.mpl.hir.HirContinue;
import com.arc.mpl.hir.HirDoWhile;
import com.arc.mpl.hir.HirDraw;
import com.arc.mpl.hir.HirDynamicCollectionSet;
import com.arc.mpl.hir.HirDynamicIndexAccess;
import com.arc.mpl.hir.HirExpression;
import com.arc.mpl.hir.HirExpressionStatement;
import com.arc.mpl.hir.HirFor;
import com.arc.mpl.hir.HirFunction;
import com.arc.mpl.hir.HirFunctionCall;
import com.arc.mpl.hir.HirFunctionParameter;
import com.arc.mpl.hir.HirHardwareLink;
import com.arc.mpl.hir.HirIntrinsicCall;
import com.arc.mpl.hir.HirIf;
import com.arc.mpl.hir.HirArrayLiteral;
import com.arc.mpl.hir.HirIndexAccess;
import com.arc.mpl.hir.HirMemberAccess;
import com.arc.mpl.hir.HirPrintStatement;
import com.arc.mpl.hir.HirProgram;
import com.arc.mpl.hir.HirReturn;
import com.arc.mpl.hir.HirStatement;
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
import com.arc.mpl.hir.BuildingType;
import com.arc.mpl.hir.CollectionType;
import com.arc.mpl.hir.LinkedBuildingSetType;
import com.arc.mpl.hir.TupleType;
import com.arc.mpl.hir.UnitSetType;
import com.arc.mpl.hir.UnitType;
import com.arc.mpl.hir.ValueType;
import com.arc.mpl.profile.KnownProfiles;
import com.arc.mpl.profile.TargetProfile;
import com.arc.mpl.project.HardwareContract;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Name resolution and strict type checks for the currently implemented MPL subset. */
public final class SemanticAnalyzer {
    private final TargetProfile profile;
    private final Deque<Map<String, Symbol>> scopes = new ArrayDeque<>();
    private final List<Diagnostic> diagnostics = new ArrayList<>();
    private final Map<String, FunctionSignature> functions = new LinkedHashMap<>();
    private final Map<String, Set<String>> callGraph = new HashMap<>();
    private final Map<String, Set<String>> directGlobalDependencies = new HashMap<>();
    private final List<TopLevelCall> topLevelCalls = new ArrayList<>();
    private final Set<String> initializedGlobals = new HashSet<>();
    private final Deque<ArrayBoundsProof> arrayBoundsProofs = new ArrayDeque<>();
    private Path file;
    private Map<String, String> messages = Map.of();
    private Map<String, HardwareContract.LinkDeclaration> hardwareLinks = Map.of();
    private Map<String, HardwareContract.Resource> hardwareResources = Map.of();
    private int unitIterationDepth;
    private int nextManagedQueryId;
    private int loopDepth;
    private String activeUnitBinding;
    private String activeBuildingBinding;
    private String activeBuildingType;
    private String currentFunction;
    private MplType currentReturnType;
    private boolean analyzingTopLevel;

    /** Uses the v146 baseline when semantic analysis is invoked outside a compiler request. */
    public SemanticAnalyzer() {
        this(KnownProfiles.find("v146").orElseThrow());
    }

    public SemanticAnalyzer(TargetProfile profile) {
        this.profile = java.util.Objects.requireNonNull(profile, "profile");
    }

    public SemanticResult analyze(Program program, Path sourceFile) {
        return analyze(program, sourceFile, Map.of());
    }

    public SemanticResult analyze(Program program, Path sourceFile, Map<String, String> messages) {
        List<HardwareContract.LinkDeclaration> links = messages.entrySet().stream()
            .map(entry -> new HardwareContract.LinkDeclaration(entry.getKey(), "Message", entry.getValue())).toList();
        return analyze(program, sourceFile, new HardwareContract(links, messages));
    }

    /** Analyzes source with the full typed hardware contract, never treating links as numeric variables. */
    public SemanticResult analyze(Program program, Path sourceFile, HardwareContract hardware) {
        scopes.clear();
        scopes.push(new HashMap<>());
        diagnostics.clear();
        file = sourceFile;
        this.messages = Map.copyOf(hardware.messages());
        Map<String, HardwareContract.LinkDeclaration> links = new LinkedHashMap<>();
        for (HardwareContract.LinkDeclaration link : hardware.links()) {
            if (links.put(link.mplName(), link) != null) {
                throw new IllegalArgumentException("重复的硬件常量：" + link.mplName());
            }
        }
        hardwareLinks = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(links));
        hardwareResources = Map.copyOf(hardware.resources());
        functions.clear();
        callGraph.clear();
        directGlobalDependencies.clear();
        topLevelCalls.clear();
        initializedGlobals.clear();
        arrayBoundsProofs.clear();
        unitIterationDepth = 0;
        nextManagedQueryId = 0;
        loopDepth = 0;
        activeUnitBinding = null;
        activeBuildingBinding = null;
        activeBuildingType = null;
        currentFunction = null;
        currentReturnType = null;
        analyzingTopLevel = true;

        if (!program.imports().isEmpty() || !program.exports().isEmpty()) {
            SourceSpan span = !program.imports().isEmpty()
                ? program.imports().get(0).span() : program.exports().get(0).span();
            error("MPL1400", "模块声明必须先经过 ProjectProgramLoader 链接", span);
            return new SemanticResult(Optional.empty(), diagnostics);
        }

        for (FunctionDeclaration function : program.functions()) registerFunction(function);

        List<HirStatement> statements = new ArrayList<>();
        try {
            for (Statement statement : program.statements()) {
                statements.add(analyzeStatement(statement));
            }
        } finally {
            analyzingTopLevel = false;
        }
        List<HirFunction> analyzedFunctions = program.functions().stream().map(this::analyzeFunction).toList();
        rejectRecursiveFunctions();
        validateTopLevelCalls();
        return new SemanticResult(diagnostics.isEmpty()
            ? Optional.of(new HirProgram(analyzedFunctions, statements)) : Optional.empty(), diagnostics);
    }

    private HirStatement analyzeStatement(Statement statement) {
        if (statement instanceof MilDrawStatement draw) {
            return analyzeMilDraw(draw);
        }
        if (statement instanceof VariableDeclaration declaration) {
            return analyzeDeclaration(declaration);
        }
        if (statement instanceof WhileStatement loop) {
            return analyzeWhile(loop);
        }
        if (statement instanceof DoWhileStatement loop) {
            return analyzeDoWhile(loop);
        }
        if (statement instanceof IfStatement branch) {
            return analyzeIf(branch);
        }
        if (statement instanceof ForStatement loop) {
            return analyzeFor(loop);
        }
        if (statement instanceof ForEachStatement loop) {
            return analyzeForEach(loop);
        }
        if (statement instanceof BlockStatement block) {
            return new HirBlock(analyzeBlock(block));
        }
        if (statement instanceof BreakStatement jump) {
            if (loopDepth == 0) error("MPL3401", "break 只能出现在循环内", jump.span());
            return new HirBreak();
        }
        if (statement instanceof ContinueStatement jump) {
            if (loopDepth == 0) error("MPL3402", "continue 只能出现在循环内", jump.span());
            return new HirContinue();
        }
        if (statement instanceof ReturnStatement returned) {
            return analyzeReturn(returned);
        }
        ExpressionStatement expressionStatement = (ExpressionStatement) statement;
        return analyzeExpressionStatement(expressionStatement.expression());
    }

    private void registerFunction(FunctionDeclaration function) {
        List<MplType> parameters = function.parameters().stream()
            .map(parameter -> parseType(parameter.typeName(), parameter.span())).toList();
        if (parameters.contains(ValueType.VOID)) {
            error("MPL3503", "函数参数不能使用 Void 类型", function.span());
        }
        MplType returnType = function.returnType().map(value -> parseType(value, function.span())).orElse(ValueType.VOID);
        if (parameters.stream().anyMatch(type -> isAggregate(type) || type instanceof UnitSetType || type instanceof UnitType)
            || isAggregate(returnType) || returnType instanceof UnitSetType || returnType instanceof UnitType) {
            error("MPL3602", "第一版函数 ABI 尚不支持聚合、Set<Unit<T>> 或 UnitRef 参数及返回值", function.span());
        }
        FunctionSignature signature = new FunctionSignature(function, parameters, returnType);
        if (hardwareLinks.containsKey(function.name()) || functions.putIfAbsent(function.name(), signature) != null) {
            error("MPL3501", "函数已声明：" + function.name(), function.span());
        }
        callGraph.putIfAbsent(function.name(), new HashSet<>());
        directGlobalDependencies.putIfAbsent(function.name(), new HashSet<>());
    }

    private HirFunction analyzeFunction(FunctionDeclaration function) {
        FunctionSignature signature = functions.get(function.name());
        if (signature == null || signature.declaration() != function) {
            return new HirFunction(function.name(), List.of(), ValueType.ERROR, List.of());
        }
        String previousFunction = currentFunction;
        MplType previousReturnType = currentReturnType;
        currentFunction = function.name();
        currentReturnType = signature.returnType();
        scopes.push(new HashMap<>());
        try {
            List<HirFunctionParameter> parameters = new ArrayList<>();
            for (int index = 0; index < function.parameters().size(); index++) {
                FunctionParameter parameter = function.parameters().get(index);
                MplType type = signature.parameterTypes().get(index);
                declare(parameter.name(), new Symbol(type, false, null, null, null, null, false, parameter.span()), parameter.span());
                parameters.add(new HirFunctionParameter(parameter.name(), type));
            }
            List<HirStatement> body = analyzeBlock(function.body());
            if (signature.returnType() != ValueType.VOID && !guaranteesReturn(body)) {
                error("MPL3504", "函数 " + function.name() + " 并非所有路径都返回 "
                    + display(signature.returnType()), function.span());
            }
            return new HirFunction(function.name(), parameters, signature.returnType(), body);
        } finally {
            scopes.pop();
            currentFunction = previousFunction;
            currentReturnType = previousReturnType;
        }
    }

    private HirStatement analyzeReturn(ReturnStatement returned) {
        if (currentFunction == null) {
            error("MPL3502", "return 只能出现在函数内", returned.span());
            return new HirReturn(Optional.empty());
        }
        Optional<HirExpression> value = returned.value().map(this::analyzeExpression);
        if (currentReturnType == ValueType.VOID && value.isPresent()) {
            error("MPL3503", "无返回值函数不能 return 表达式", returned.span());
        } else if (currentReturnType != ValueType.VOID && value.isEmpty()) {
            error("MPL3503", "函数 " + currentFunction + " 必须返回 " + display(currentReturnType), returned.span());
        } else if (value.isPresent() && !currentReturnType.canAssignFrom(value.orElseThrow().type())) {
            error("MPL3503", "函数 " + currentFunction + " 不能返回 " + display(value.orElseThrow().type()), returned.span());
        }
        return new HirReturn(value);
    }

    private boolean guaranteesReturn(List<HirStatement> statements) {
        for (HirStatement statement : statements) {
            if (statement instanceof HirReturn) return true;
            if (statement instanceof HirBlock block && guaranteesReturn(block.statements())) return true;
            if (statement instanceof HirIf branch && branch.elseBody().isPresent()
                && guaranteesReturn(branch.thenBody()) && guaranteesReturn(branch.elseBody().orElseThrow())) return true;
        }
        return false;
    }

    private void rejectRecursiveFunctions() {
        Map<String, Integer> states = new HashMap<>();
        for (String function : functions.keySet()) visitFunction(function, states, new ArrayDeque<>());
    }

    private void visitFunction(String function, Map<String, Integer> states, Deque<String> path) {
        if (states.getOrDefault(function, 0) == 2) return;
        if (states.getOrDefault(function, 0) == 1) {
            FunctionSignature signature = functions.get(function);
            error("MPL3505", "函数调用图存在递归环：" + String.join(" -> ", path) + " -> " + function,
                signature.declaration().span());
            return;
        }
        states.put(function, 1);
        path.addLast(function);
        for (String target : callGraph.getOrDefault(function, java.util.Set.of())) visitFunction(target, states, path);
        path.removeLast();
        states.put(function, 2);
    }

    private List<HirStatement> analyzeBlock(BlockStatement block) {
        scopes.push(new HashMap<>());
        try {
            List<HirStatement> statements = new ArrayList<>();
            for (Statement statement : block.statements()) {
                statements.add(analyzeStatement(statement));
            }
            return List.copyOf(statements);
        } finally {
            scopes.pop();
        }
    }

    private HirStatement analyzeWhile(WhileStatement loop) {
        HirExpression condition = analyzeExpression(loop.condition());
        requireBool(condition.type(), loop.condition().span(), "while 条件");
        return new HirWhile(condition, analyzeLoopBlock(loop.body()));
    }

    private HirStatement analyzeDoWhile(DoWhileStatement loop) {
        List<HirStatement> body = analyzeLoopBlock(loop.body());
        HirExpression condition = analyzeExpression(loop.condition());
        requireBool(condition.type(), loop.condition().span(), "do-while 条件");
        return new HirDoWhile(body, condition);
    }

    private HirStatement analyzeIf(IfStatement branch) {
        HirExpression condition = analyzeExpression(branch.condition());
        requireBool(condition.type(), branch.condition().span(), "if 条件");
        List<HirStatement> consequence = analyzeWithNarrowing(
            nonNullNarrowing(branch.condition(), true), () -> analyzeBlock(branch.thenBlock()));
        Optional<List<HirStatement>> alternative = branch.elseBranch().map(value -> analyzeWithNarrowing(
            nonNullNarrowing(branch.condition(), false), () -> analyzeAlternative(value)));
        return new HirIf(condition, consequence, alternative);
    }

    private Map<String, Symbol> nonNullNarrowing(Expression condition, boolean conditionResult) {
        if (!(condition instanceof BinaryExpression binary)
            || (!"==".equals(binary.operator()) && !"!=".equals(binary.operator()))) {
            return Map.of();
        }
        Identifier identifier;
        if (binary.left() instanceof Identifier left && binary.right() instanceof NullLiteral) {
            identifier = left;
        } else if (binary.right() instanceof Identifier right && binary.left() instanceof NullLiteral) {
            identifier = right;
        } else {
            return Map.of();
        }
        boolean nonNullBranch = "!=".equals(binary.operator()) == conditionResult;
        if (!nonNullBranch) return Map.of();
        Symbol symbol = lookup(identifier.name());
        if (symbol == null || symbol.mutable()) return Map.of();
        if (symbol.type() instanceof UnitType unit && unit.nullable()) {
            return Map.of(identifier.name(), symbol.withType(unit.nonNullable()));
        }
        if (symbol.type() instanceof BuildingType building && building.nullable()) {
            return Map.of(identifier.name(), symbol.withType(building.nonNullable()));
        }
        return Map.of();
    }

    private <T> T analyzeWithNarrowing(Map<String, Symbol> narrowing, java.util.function.Supplier<T> analysis) {
        if (narrowing.isEmpty()) return analysis.get();
        scopes.push(new HashMap<>(narrowing));
        try {
            return analysis.get();
        } finally {
            scopes.pop();
        }
    }

    private List<HirStatement> analyzeAlternative(Statement alternative) {
        if (alternative instanceof BlockStatement block) return analyzeBlock(block);
        return List.of(analyzeStatement(alternative));
    }

    private List<HirStatement> analyzeLoopBlock(BlockStatement block) {
        loopDepth++;
        try {
            return analyzeBlock(block);
        } finally {
            loopDepth--;
        }
    }

    private HirStatement analyzeFor(ForStatement loop) {
        scopes.push(new HashMap<>());
        try {
            Optional<HirVariableDeclaration> declarationInitializer = loop.declarationInitializer()
                .map(value -> (HirVariableDeclaration) analyzeDeclaration(value));
            Optional<HirExpression> expressionInitializer = loop.expressionInitializer().map(this::analyzeExpression);
            Optional<ArrayBoundsProof> boundsProof = countingArrayBoundsProof(loop);
            HirExpression condition = loop.condition().map(this::analyzeExpression)
                .orElseGet(() -> new HirConstant("1", ValueType.BOOL));
            loop.condition().ifPresent(value -> requireBool(condition.type(), value.span(), "for 条件"));
            Optional<HirExpression> update = loop.update().map(this::analyzeExpression);
            boundsProof.ifPresent(arrayBoundsProofs::push);
            List<HirStatement> body;
            try {
                body = analyzeLoopBlock(loop.body());
            } finally {
                boundsProof.ifPresent(ignored -> arrayBoundsProofs.pop());
            }
            return new HirFor(declarationInitializer, expressionInitializer, condition, update, body);
        } finally {
            scopes.pop();
        }
    }

    /** Recognizes the first statically safe dynamic-index form: {@code for (var i = 0; i < array.size; i += 1)}. */
    private Optional<ArrayBoundsProof> countingArrayBoundsProof(ForStatement loop) {
        if (loop.declarationInitializer().isEmpty() || loop.condition().isEmpty() || loop.update().isEmpty()) {
            return Optional.empty();
        }
        VariableDeclaration declaration = loop.declarationInitializer().orElseThrow();
        if (!declaration.mutable() || !(declaration.initializer() instanceof IntegerLiteral start) || start.value() != 0) {
            return Optional.empty();
        }
        Symbol indexSymbol = lookup(declaration.name());
        if (indexSymbol == null || indexSymbol.type() != ValueType.INT) return Optional.empty();
        if (!(loop.condition().orElseThrow() instanceof BinaryExpression condition)
            || !"<".equals(condition.operator())
            || !(condition.left() instanceof Identifier index)
            || !declaration.name().equals(index.name())
            || !(condition.right() instanceof MemberAccessExpression size)
            || !"size".equals(size.member())
            || !(size.target() instanceof Identifier array)) {
            return Optional.empty();
        }
        Symbol arraySymbol = lookup(array.name());
        if (arraySymbol == null || !(arraySymbol.type() instanceof CollectionType collection)
            || collection.kind() != CollectionType.Kind.ARRAY || arraySymbol.staticAggregateSize() == null) {
            return Optional.empty();
        }
        if (!(loop.update().orElseThrow() instanceof AssignmentExpression update)
            || !declaration.name().equals(update.target().name())
            || !"+=".equals(update.operator())
            || !(update.value() instanceof IntegerLiteral step) || step.value() != 1) {
            return Optional.empty();
        }
        return Optional.of(new ArrayBoundsProof(declaration.name(), array.name()));
    }

    private HirStatement analyzeForEach(ForEachStatement loop) {
        HirUnitQuery savedQuery = resolveSavedUnitQuery(loop.iterable(), loop.name());
        if (savedQuery != null) return analyzeUnitForEach(loop, savedQuery);
        Optional<UnitQuery> query = parseUnitQuery(loop.iterable());
        if (query.isPresent()) {
            return analyzeUnitForEach(loop, query.orElseThrow());
        }
        HirBuildingQuery savedBuildingQuery = resolveSavedBuildingQuery(loop.iterable(), loop.name());
        if (savedBuildingQuery != null) return analyzeBuildingForEach(loop, savedBuildingQuery);
        Optional<BuildingQuery> buildingQuery = parseBuildingQuery(loop.iterable());
        if (buildingQuery.isPresent()) return analyzeBuildingForEach(loop, buildingQuery.orElseThrow());
        return analyzeAggregateForEach(loop);
    }

    private HirStatement analyzeUnitForEach(ForEachStatement loop, UnitQuery query) {
        if (currentFunction != null) error("MPL3508", "第一版函数不支持 Set<Unit<T>> 遍历", loop.span());
        if (unitIterationDepth > 0) {
            error("MPL3306", "第一版不支持嵌套 Unit 遍历", loop.span());
        }

        String previousBinding = activeUnitBinding;
        activeUnitBinding = loop.name();
        unitIterationDepth++;
        loopDepth++;
        scopes.push(new HashMap<>());
        try {
            declare(loop.name(), new Symbol(ValueType.UNIT, false, null, null, null, null, false, loop.span()), loop.span());
            List<HirExpression> filters = new ArrayList<>();
            for (Expression filter : query.filters()) {
                filters.add(analyzeUnitFilter(filter, loop.name()));
            }
            List<HirStatement> body = analyzeBlock(loop.body());
            TargetProfile.UnitType type = query.type();
            return new HirUnitIteration(
                loop.name(),
                query.typeName(),
                type.mlogName(),
                filters,
                query.managedLimit(),
                query.managedId(),
                body);
        } finally {
            scopes.pop();
            unitIterationDepth--;
            loopDepth--;
            activeUnitBinding = previousBinding;
        }
    }

    private HirStatement analyzeUnitForEach(ForEachStatement loop, HirUnitQuery query) {
        if (currentFunction != null) error("MPL3508", "第一版函数不支持 Set<Unit<T>> 遍历", loop.span());
        if (unitIterationDepth > 0) error("MPL3306", "第一版不支持嵌套 Unit 遍历", loop.span());

        String previousBinding = activeUnitBinding;
        activeUnitBinding = loop.name();
        unitIterationDepth++;
        loopDepth++;
        scopes.push(new HashMap<>());
        try {
            declare(loop.name(), new Symbol(ValueType.UNIT, false, null, null, null, null, false, loop.span()), loop.span());
            List<HirExpression> filters = query.filters().stream()
                .map(filter -> renameUnitBinding(filter, query.bindingName(), loop.name())).toList();
            return new HirUnitIteration(loop.name(), query.unitType(), query.mlogType(), filters,
                query.managedLimit(), query.managedId(), analyzeBlock(loop.body()));
        } finally {
            scopes.pop();
            unitIterationDepth--;
            loopDepth--;
            activeUnitBinding = previousBinding;
        }
    }

    private HirUnitQuery declaredUnitQuery(Expression expression) {
        if (!(expression instanceof Identifier identifier)) return null;
        Symbol symbol = lookup(identifier.name());
        return symbol == null ? null : symbol.unitQuery();
    }

    private HirUnitQuery resolveSavedUnitQuery(Expression expression, String bindingName) {
        List<CallExpression> modifiers = new ArrayList<>();
        Expression current = expression;
        while (current instanceof CallExpression call && call.callee() instanceof MemberAccessExpression member
            && ("where".equals(member.member()) || "take".equals(member.member()))) {
            modifiers.add(call);
            current = member.target();
        }
        HirUnitQuery base = declaredUnitQuery(current);
        if (base == null) return null;

        List<HirExpression> filters = new ArrayList<>(base.filters().stream()
            .map(filter -> renameUnitBinding(filter, base.bindingName(), bindingName)).toList());
        int managedLimit = base.managedLimit();
        int managedId = base.managedId();
        String previousBinding = activeUnitBinding;
        activeUnitBinding = bindingName;
        try {
            for (int index = modifiers.size() - 1; index >= 0; index--) {
                CallExpression modifier = modifiers.get(index);
                MemberAccessExpression member = (MemberAccessExpression) modifier.callee();
                if ("where".equals(member.member())) {
                    if (managedLimit != 0) {
                        error("MPL3307", "Set<Unit<T>> 的 where(...) 必须位于 take(n) 之前", modifier.span());
                        continue;
                    }
                    if (modifier.arguments().size() != 1) {
                        error("MPL3302", "Set<Unit<T>> 的 where(...) 需要恰好一个过滤 lambda", modifier.span());
                        continue;
                    }
                    filters.add(analyzeUnitFilter(modifier.arguments().get(0), bindingName));
                    continue;
                }
                if (managedLimit != 0) {
                    error("MPL3307", "一个 Set<Unit<T>> 查询只能调用一次 take(n)", modifier.span());
                    continue;
                }
                if (modifier.arguments().size() != 1 || !(modifier.arguments().get(0) instanceof IntegerLiteral literal)
                    || literal.value() <= 0 || literal.value() > Integer.MAX_VALUE) {
                    error("MPL3307", "Set<Unit<T>> 的 take(n) 只接受 1 到 2147483647 的 Int 字面量", modifier.span());
                    continue;
                }
                managedLimit = (int) literal.value();
                managedId = nextManagedQueryId++;
            }
        } finally {
            activeUnitBinding = previousBinding;
        }
        return new HirUnitQuery(bindingName, base.unitType(), base.mlogType(), filters, managedLimit, managedId);
    }

    private HirStatement analyzeBuildingForEach(ForEachStatement loop, BuildingQuery query) {
        String previousBinding = activeBuildingBinding;
        String previousType = activeBuildingType;
        activeBuildingBinding = loop.name();
        activeBuildingType = query.typeName();
        loopDepth++;
        scopes.push(new HashMap<>());
        try {
            declare(loop.name(), new Symbol(ValueType.BUILDING, false, null, null, null, null, false, loop.span()), loop.span());
            List<HirHardwareLink> buildings = linkedBuildings(query.typeName());
            List<HirExpression> filters = query.filters().stream()
                .map(filter -> analyzeBuildingFilter(filter, loop.name(), query.typeName())).toList();
            String mlogType = profile.buildingType(query.typeName()).orElseThrow().mlogName();
            return new HirBuildingIteration(loop.name(), query.typeName(), mlogType, buildings, filters,
                analyzeBlock(loop.body()));
        } finally {
            scopes.pop();
            loopDepth--;
            activeBuildingBinding = previousBinding;
            activeBuildingType = previousType;
        }
    }

    private HirStatement analyzeBuildingForEach(ForEachStatement loop, HirBuildingQuery query) {
        String previousBinding = activeBuildingBinding;
        String previousType = activeBuildingType;
        activeBuildingBinding = loop.name();
        activeBuildingType = query.buildingType();
        loopDepth++;
        scopes.push(new HashMap<>());
        try {
            declare(loop.name(), new Symbol(ValueType.BUILDING, false, null, null, null, null, false, loop.span()), loop.span());
            List<HirExpression> filters = query.filters().stream()
                .map(filter -> renameBuildingBinding(filter, query.bindingName(), loop.name())).toList();
            return new HirBuildingIteration(loop.name(), query.buildingType(), query.mlogType(), query.buildings(), filters,
                analyzeBlock(loop.body()));
        } finally {
            scopes.pop();
            loopDepth--;
            activeBuildingBinding = previousBinding;
            activeBuildingType = previousType;
        }
    }

    private List<HirHardwareLink> linkedBuildings(String buildingType) {
        return hardwareLinks.values().stream()
            .filter(link -> buildingType.equals(link.mplType()))
            .map(link -> new HirHardwareLink(link.mplName(), link.gameAlias(), link.mplType()))
            .toList();
    }

    private HirStatement analyzeAggregateForEach(ForEachStatement loop) {
        HirExpression iterable = analyzeExpression(loop.iterable());
        if (!(iterable instanceof HirVariable source) || !isAggregate(iterable.type())) {
            error("MPL3601", "for 遍历目标必须是已声明的元组、数组、List、Set 或 Unit 查询", loop.iterable().span());
            return new HirBlock(analyzeBlock(loop.body()));
        }
        Integer size = aggregateSize(loop.iterable(), iterable.type());
        MplType elementType = aggregateIterationElementType(iterable.type(), loop.iterable().span());
        if (size == null || elementType == ValueType.ERROR) {
            return new HirBlock(analyzeBlock(loop.body()));
        }

        loopDepth++;
        scopes.push(new HashMap<>());
        try {
            declare(loop.name(), new Symbol(elementType, false, null, null, null, null, false, loop.span()), loop.span());
            return new HirAggregateIteration(loop.name(), source, elementType, size, analyzeBlock(loop.body()));
        } finally {
            scopes.pop();
            loopDepth--;
        }
    }

    private Optional<UnitQuery> parseUnitQuery(Expression iterable) {
        List<Expression> filters = new ArrayList<>();
        List<CallExpression> modifiers = new ArrayList<>();
        Expression current = iterable;

        while (current instanceof CallExpression call
            && call.callee() instanceof MemberAccessExpression member) {
            if (!"where".equals(member.member()) && !"take".equals(member.member())) {
                break;
            }
            modifiers.add(call);
            current = member.target();
        }

        if (!(current instanceof CallExpression call)
            || !(call.callee() instanceof MemberAccessExpression member)
            || !(member.target() instanceof Identifier namespace)
            || !"Unit".equals(namespace.name())) {
            return Optional.empty();
        }
        if (!member.member().startsWith("getAll") || member.member().length() == "getAll".length()) {
            error("MPL3302", "Unit 查询必须形如 Unit.getAllDagger()", member.span());
            return Optional.empty();
        }

        String typeName = member.member().substring("getAll".length());
        Optional<TargetProfile.UnitType> type = profile.unitType(typeName);
        if (type.isEmpty() || !type.orElseThrow().logicControllable()) {
            error("MPL3302", "当前 target 不支持 Unit.getAll" + typeName + "()", member.span());
            return Optional.empty();
        }
        if (call.arguments().size() > 1) {
            error("MPL3302", "Unit.getAll类型(...) 最多接受一个过滤 lambda", call.span());
            return Optional.empty();
        }
        if (call.arguments().size() == 1) filters.add(0, call.arguments().get(0));

        int managedLimit = 0;
        for (int index = modifiers.size() - 1; index >= 0; index--) {
            CallExpression modifier = modifiers.get(index);
            MemberAccessExpression modifierMember = (MemberAccessExpression) modifier.callee();
            if ("where".equals(modifierMember.member())) {
                if (managedLimit != 0) {
                error("MPL3307", "Set<Unit<T>>.take(n) 必须放在所有 .where(...) 之后", modifier.span());
                    return Optional.empty();
                }
                if (modifier.arguments().size() != 1) {
                    error("MPL3302", "Set<Unit<T>>.where(...) 需要恰好一个过滤 lambda", modifier.span());
                    return Optional.empty();
                }
                filters.add(modifier.arguments().get(0));
                continue;
            }

            if (managedLimit != 0) {
                error("MPL3307", "一个 Set<Unit<T>> 查询只能调用一次 .take(n)", modifier.span());
                return Optional.empty();
            }
            if (modifier.arguments().size() != 1 || !(modifier.arguments().get(0) instanceof IntegerLiteral literal)) {
                error("MPL3307", "Set<Unit<T>>.take(n) 只接受正 Int 字面量", modifier.span());
                return Optional.empty();
            }
            if (literal.value() <= 0 || literal.value() > Integer.MAX_VALUE) {
                error("MPL3307", "Set<Unit<T>>.take(n) 的 n 必须位于 1 到 2147483647", literal.span());
                return Optional.empty();
            }
            managedLimit = (int) literal.value();
        }

        int managedId = managedLimit > 0 ? nextManagedQueryId++ : -1;
        return Optional.of(new UnitQuery(typeName, type.orElseThrow(), List.copyOf(filters), managedLimit, managedId));
    }

    private Optional<BuildingQuery> parseBuildingQuery(Expression iterable) {
        List<Expression> filters = new ArrayList<>();
        List<CallExpression> modifiers = new ArrayList<>();
        Expression current = iterable;

        while (current instanceof CallExpression call
            && call.callee() instanceof MemberAccessExpression member
            && "where".equals(member.member())) {
            modifiers.add(call);
            current = member.target();
        }
        if (!(current instanceof CallExpression queryCall)
            || !(queryCall.callee() instanceof MemberAccessExpression member)
            || !(member.target() instanceof Identifier namespace)
            || !"Building".equals(namespace.name())) {
            return Optional.empty();
        }
        if (!member.member().startsWith("getAll") || member.member().length() == "getAll".length()) {
            error("MPL3201", "Building 查询必须形如 Building.getAllDuo()", member.span());
            return Optional.empty();
        }
        if (queryCall.arguments().size() > 1) {
            error("MPL3201", "Building.getAll类型(...) 最多接受一个过滤 lambda", queryCall.span());
            return Optional.empty();
        }
        String typeName = member.member().substring("getAll".length());
        if (profile.buildingType(typeName).isEmpty()) {
            error("MPL3201", "当前 target 不支持 Building.getAll" + typeName + "()", member.span());
            return Optional.empty();
        }
        if (queryCall.arguments().size() == 1) filters.add(queryCall.arguments().get(0));
        for (int index = modifiers.size() - 1; index >= 0; index--) {
            CallExpression modifier = modifiers.get(index);
            if (modifier.arguments().size() != 1) {
                error("MPL3201", "Building 查询的 .where(...) 需要恰好一个过滤 lambda", modifier.span());
                return Optional.empty();
            }
            filters.add(modifier.arguments().get(0));
        }
        return Optional.of(new BuildingQuery(typeName, List.copyOf(filters)));
    }

    private HirBuildingQuery declaredBuildingQuery(Expression expression) {
        if (!(expression instanceof Identifier identifier)) return null;
        Symbol symbol = lookup(identifier.name());
        return symbol == null ? null : symbol.buildingQuery();
    }

    private HirBuildingQuery resolveSavedBuildingQuery(Expression expression, String bindingName) {
        List<CallExpression> modifiers = new ArrayList<>();
        Expression current = expression;
        while (current instanceof CallExpression call && call.callee() instanceof MemberAccessExpression member
            && "where".equals(member.member())) {
            modifiers.add(call);
            current = member.target();
        }
        HirBuildingQuery base = declaredBuildingQuery(current);
        if (base == null) return null;

        List<HirExpression> filters = new ArrayList<>(base.filters().stream()
            .map(filter -> renameBuildingBinding(filter, base.bindingName(), bindingName)).toList());
        for (int index = modifiers.size() - 1; index >= 0; index--) {
            CallExpression modifier = modifiers.get(index);
            if (modifier.arguments().size() != 1) {
                error("MPL3201", "LinkedBuildingSet<T>.where(...) 需要恰好一个过滤 lambda", modifier.span());
                continue;
            }
            filters.add(analyzeBuildingFilter(modifier.arguments().get(0), bindingName, base.buildingType()));
        }
        return new HirBuildingQuery(bindingName, base.buildingType(), base.mlogType(), base.buildings(), filters);
    }

    private HirBuildingQuery analyzeBuildingQuery(BuildingQuery query, String bindingName) {
        String previousBinding = activeBuildingBinding;
        String previousType = activeBuildingType;
        activeBuildingBinding = bindingName;
        activeBuildingType = query.typeName();
        try {
            List<HirExpression> filters = query.filters().stream()
                .map(filter -> analyzeBuildingFilter(filter, bindingName, query.typeName())).toList();
            String mlogType = profile.buildingType(query.typeName()).orElseThrow().mlogName();
            return new HirBuildingQuery(bindingName, query.typeName(), mlogType, linkedBuildings(query.typeName()), filters);
        } finally {
            activeBuildingBinding = previousBinding;
            activeBuildingType = previousType;
        }
    }

    private HirBuildingQuery resolveBuildingQuery(Expression expression, String bindingName) {
        HirBuildingQuery saved = resolveSavedBuildingQuery(expression, bindingName);
        if (saved != null) return saved;
        return parseBuildingQuery(expression).map(query -> analyzeBuildingQuery(query, bindingName)).orElse(null);
    }

    private HirExpression analyzeBuildingQueryGet(HirBuildingQuery query, Expression sourceIndex, SourceSpan span) {
        HirExpression index = analyzeExpression(sourceIndex);
        if (index.type() != ValueType.INT && index.type() != ValueType.ERROR) {
            error("MPL3210", "LinkedBuildingSet<T>.get(index) 的 index 必须是 Int", sourceIndex.span());
        }
        return new HirBuildingQueryGet(query, index);
    }

    /** Validates a side-effect-free predicate over one statically expanded linked building. */
    private HirExpression analyzeBuildingFilter(Expression source, String bindingName, String buildingType) {
        String parameter = "_";
        Expression predicate = source;
        if (source instanceof LambdaExpression lambda) {
            parameter = lambda.parameter();
            predicate = lambda.body();
        }
        String previousBinding = activeBuildingBinding;
        String previousType = activeBuildingType;
        activeBuildingBinding = parameter;
        activeBuildingType = buildingType;
        scopes.push(new HashMap<>());
        try {
            // Query parameters shadow an enclosing iteration binding just like Unit filter parameters.
            scopes.peek().put(parameter,
                new Symbol(ValueType.BUILDING, false, null, null, null, null, false, source.span()));
            HirExpression result = analyzeExpression(predicate);
            if (result.type() != ValueType.BOOL) {
                error("MPL3201", "Building 查询的 .where(...) 过滤条件必须是 Bool", predicate.span());
            }
            if (!(source instanceof LambdaExpression) && !referencesBuildingBinding(result, parameter)) {
                error("MPL3201", "Building 查询的过滤条件必须是 lambda，或使用引用 _ 的简写", source.span());
            }
            if (!isPureBuildingFilter(result, parameter)) {
                error("MPL3201", "Building 查询的 .where(...) 只能读取当前建筑字段与 val 标量", source.span());
            }
            return renameBuildingBinding(result, parameter, bindingName);
        } finally {
            scopes.pop();
            activeBuildingBinding = previousBinding;
            activeBuildingType = previousType;
        }
    }

    private boolean referencesBuildingBinding(HirExpression expression, String bindingName) {
        if (expression instanceof HirVariable variable) {
            return variable.type() == ValueType.BUILDING && bindingName.equals(variable.name());
        }
        if (expression instanceof HirMemberAccess member) {
            return referencesBuildingBinding(member.target(), bindingName);
        }
        if (expression instanceof HirIntrinsicCall call) {
            return call.arguments().stream().anyMatch(argument -> referencesBuildingBinding(argument, bindingName));
        }
        if (expression instanceof HirUnary unary) return referencesBuildingBinding(unary.operand(), bindingName);
        if (expression instanceof HirBinary binary) {
            return referencesBuildingBinding(binary.left(), bindingName)
                || referencesBuildingBinding(binary.right(), bindingName);
        }
        return false;
    }

    private boolean isPureBuildingFilter(HirExpression expression, String bindingName) {
        if (expression instanceof HirConstant || expression instanceof HirText) return true;
        if (expression instanceof HirVariable variable) {
            if (variable.type() == ValueType.BUILDING) return bindingName.equals(variable.name());
            return isImmutableFilterScalar(variable.name());
        }
        if (expression instanceof HirMemberAccess member) return isPureBuildingFilter(member.target(), bindingName);
        if (expression instanceof HirIntrinsicCall call) {
            return "Math".equals(call.namespace())
                && call.arguments().stream().allMatch(argument -> isPureBuildingFilter(argument, bindingName));
        }
        if (expression instanceof HirUnary unary) return isPureBuildingFilter(unary.operand(), bindingName);
        if (expression instanceof HirBinary binary) {
            return isPureBuildingFilter(binary.left(), bindingName) && isPureBuildingFilter(binary.right(), bindingName);
        }
        return false;
    }

    /** Rebinds the lambda parameter to the iteration variable used by target lowering. */
    private HirExpression renameBuildingBinding(HirExpression expression, String sourceName, String targetName) {
        if (expression instanceof HirVariable variable) {
            return variable.type() == ValueType.BUILDING && sourceName.equals(variable.name())
                ? new HirVariable(targetName, ValueType.BUILDING) : variable;
        }
        if (expression instanceof HirMemberAccess member) {
            return new HirMemberAccess(renameBuildingBinding(member.target(), sourceName, targetName), member.member(), member.type());
        }
        if (expression instanceof HirUnary unary) {
            return new HirUnary(unary.operator(), renameBuildingBinding(unary.operand(), sourceName, targetName), unary.type());
        }
        if (expression instanceof HirBinary binary) {
            return new HirBinary(renameBuildingBinding(binary.left(), sourceName, targetName), binary.operator(),
                renameBuildingBinding(binary.right(), sourceName, targetName), binary.type());
        }
        if (expression instanceof HirIntrinsicCall call) {
            return new HirIntrinsicCall(call.namespace(), call.name(), call.arguments().stream()
                .map(argument -> renameBuildingBinding(argument, sourceName, targetName)).toList(), call.type());
        }
        return expression;
    }

    private HirExpression analyzeUnitFilter(Expression source, String bindingName) {
        String parameter = "_";
        Expression predicate = source;
        if (source instanceof LambdaExpression lambda) {
            parameter = lambda.parameter();
            predicate = lambda.body();
        }

        scopes.push(new HashMap<>());
        try {
            // Lambda parameters deliberately shadow the enclosing loop binding.
            scopes.peek().put(parameter, new Symbol(ValueType.UNIT, false, null, null, null, null, false, source.span()));
            HirExpression result = analyzeExpression(predicate);
            if (result.type() != ValueType.BOOL) {
                error("MPL3303", "Set<Unit<T>>.where(...) 的过滤条件必须是 Bool", predicate.span());
            }
            if (!isPureUnitFilter(result, parameter)) {
                error("MPL3303", "Set<Unit<T>>.where(...) 只能读取当前单位属性与 val 标量", predicate.span());
            }
            return renameUnitBinding(result, parameter, bindingName);
        } finally {
            scopes.pop();
        }
    }

    private HirUnitQuery analyzeUnitQuery(UnitQuery query, String bindingName) {
        String previousBinding = activeUnitBinding;
        activeUnitBinding = bindingName;
        try {
            List<HirExpression> filters = query.filters().stream()
                .map(filter -> analyzeUnitFilter(filter, bindingName)).toList();
            return new HirUnitQuery(bindingName, query.typeName(), query.type().mlogName(), filters,
                query.managedLimit(), query.managedId());
        } finally {
            activeUnitBinding = previousBinding;
        }
    }

    private HirUnitQuery resolveUnitQuery(Expression expression, String bindingName) {
        HirUnitQuery saved = resolveSavedUnitQuery(expression, bindingName);
        if (saved != null) return saved;
        return parseUnitQuery(expression).map(query -> analyzeUnitQuery(query, bindingName)).orElse(null);
    }

    private HirExpression analyzeUnitQueryGet(HirUnitQuery query, Expression sourceIndex, SourceSpan span) {
        HirExpression index = analyzeExpression(sourceIndex);
        if (index.type() != ValueType.INT && index.type() != ValueType.ERROR) {
            error("MPL3309", "Set<Unit<T>>.get(index) 的 index 必须是 Int", sourceIndex.span());
        }
        if (currentFunction != null) {
            error("MPL3508", "第一版函数不能扫描 Set<Unit<T>>.get(index)", span);
        }
        if (unitIterationDepth > 0) {
            error("MPL3306", "Set<Unit<T>>.get(index) 不能嵌套在 Unit 遍历中", span);
        }
        return new HirUnitQueryGet(query, index);
    }

    private HirExpression renameUnitBinding(HirExpression expression, String sourceName, String targetName) {
        if (expression instanceof HirVariable variable && variable.type() == ValueType.UNIT
            && variable.name().equals(sourceName)) {
            return new HirVariable(targetName, ValueType.UNIT);
        }
        if (expression instanceof HirMemberAccess member) {
            return new HirMemberAccess(renameUnitBinding(member.target(), sourceName, targetName), member.member(), member.type());
        }
        if (expression instanceof HirUnary unary) {
            return new HirUnary(unary.operator(), renameUnitBinding(unary.operand(), sourceName, targetName), unary.type());
        }
        if (expression instanceof HirBinary binary) {
            return new HirBinary(renameUnitBinding(binary.left(), sourceName, targetName), binary.operator(),
                renameUnitBinding(binary.right(), sourceName, targetName), binary.type());
        }
        if (expression instanceof HirIntrinsicCall call) {
            return new HirIntrinsicCall(call.namespace(), call.name(), call.arguments().stream()
                .map(argument -> renameUnitBinding(argument, sourceName, targetName)).toList(), call.type());
        }
        return expression;
    }

    private boolean isPureUnitFilter(HirExpression expression, String bindingName) {
        if (expression instanceof HirConstant || expression instanceof HirText) return true;
        if (expression instanceof HirVariable variable) {
            if (variable.type() == ValueType.UNIT) return bindingName.equals(variable.name());
            return isImmutableFilterScalar(variable.name());
        }
        if (expression instanceof HirMemberAccess member) {
            return isPureUnitFilter(member.target(), bindingName);
        }
        if (expression instanceof HirIntrinsicCall call) {
            return "Math".equals(call.namespace())
                && call.arguments().stream().allMatch(argument -> isPureUnitFilter(argument, bindingName));
        }
        if (expression instanceof HirUnary unary) return isPureUnitFilter(unary.operand(), bindingName);
        if (expression instanceof HirBinary binary) {
            return isPureUnitFilter(binary.left(), bindingName) && isPureUnitFilter(binary.right(), bindingName);
        }
        return false;
    }

    private boolean isImmutableFilterScalar(String variableName) {
        Symbol symbol = lookup(variableName);
        if (symbol == null || symbol.mutable()) return false;
        return symbol.type() == ValueType.INT || symbol.type() == ValueType.FLOAT
            || symbol.type() == ValueType.BOOL || symbol.type() == ValueType.STRING;
    }

    private HirStatement analyzeExpressionStatement(Expression expression) {
        if (expression instanceof CallExpression call) {
            HirStatement special = analyzeStatementCall(call);
            if (special != null) return special;
        }
        if (expression instanceof MethodCallExpression call) {
            return analyzeLegacyMethodCall(call);
        }
        HirExpression value = analyzeExpression(expression);
        if (value.type() == ValueType.BUILDING) {
            error("MPL3201", "硬件常量只能读取字段或调用控制方法，不能作为普通表达式", expression.span());
        }
        if (value.type() instanceof UnitSetType) {
            error("MPL3301", "Set<Unit<T>> 查询只能保存为 val、读取 size 或作为 for 遍历目标", expression.span());
        }
        if (value.type() instanceof LinkedBuildingSetType) {
            error("MPL3201", "LinkedBuildingSet<T> 查询只能保存为 val、读取 size/get 或作为 for 遍历目标",
                expression.span());
        }
        return new HirExpressionStatement(value);
    }

    private HirStatement analyzeStatementCall(CallExpression call) {
        if (!(call.callee() instanceof MemberAccessExpression member)
            || !(member.target() instanceof Identifier target)) {
            return null;
        }
        if (unavailablePackageHardware(target.name())) {
            error("MPL1414", "包代码不能访问未通过 with 注入的根项目硬件", call.span());
            return new HirExpressionStatement(new HirConstant("0", ValueType.ERROR));
        }
        HardwareContract.Resource hardware = hardwareResources.get(target.name());
        if (hardware != null) {
            if ("Display".equals(hardware.mplType())) {
                return analyzeDisplayCall(hardware, member.member(), call.arguments(), call.span());
            }
            HardwareContract.LinkDeclaration direct = hardware.physicalLinks().get(0);
            if ("Message".equals(hardware.mplType()) && "print".equals(member.member())) {
                return analyzePrintCall(direct.gameAlias(), call.arguments());
            }
            return analyzeBuildingControl(direct, member.member(), call.arguments(), call.span());
        }

        Symbol targetSymbol = lookup(target.name());
        if (targetSymbol != null && targetSymbol.type() instanceof CollectionType collection
            && "set".equals(member.member())) {
            return analyzeArraySet(target.name(), targetSymbol, collection, call.arguments(), call.span());
        }
        if (targetSymbol != null && targetSymbol.type() == ValueType.BUILDING
            && target.name().equals(activeBuildingBinding) && activeBuildingType != null) {
            return analyzeBuildingControl(new HirVariable(target.name(), ValueType.BUILDING), activeBuildingType,
                target.name(), member.member(), call.arguments(), call.span());
        }
        if (targetSymbol != null && targetSymbol.type() == ValueType.UNIT) {
            return analyzeUnitControl(target.name(), false, member.member(), call.arguments(), call.span());
        }
        if (targetSymbol != null && targetSymbol.type() instanceof UnitType unit) {
            if (unit.nullable()) {
                error("MPL3308", "可空 " + unit.displayName() + " 必须先通过 != null 检查", call.span());
                return new HirExpressionStatement(new HirConstant("0", ValueType.ERROR));
            }
            if (currentFunction != null || unitIterationDepth > 0) {
                error("MPL3306", "第一版不能在函数或 Unit 遍历体中重绑已保存的 UnitRef", call.span());
            }
            return analyzeUnitControl(target.name(), true, member.member(), call.arguments(), call.span());
        }
        if (targetSymbol != null && targetSymbol.type() instanceof BuildingType building) {
            if (building.nullable()) {
                error("MPL3211", "可空 " + building.displayName() + " 必须先通过 != null 检查", call.span());
                return new HirExpressionStatement(new HirConstant("0", ValueType.ERROR));
            }
            return analyzeBuildingControl(new HirVariable(target.name(), building), building.buildingType(), target.name(),
                member.member(), call.arguments(), call.span());
        }
        return null;
    }

    private HirStatement analyzeDisplayCall(HardwareContract.Resource display, String method,
                                            List<Expression> sourceArguments, SourceSpan span) {
        if ("flush".equals(method)) {
            error("MPL3203", "MPL 不提供 Display.flush()；绘制刷新由编译器 runtime 自动管理", span);
            return new HirExpressionStatement(new HirConstant("0", ValueType.ERROR));
        }
        HirDraw.Command command = switch (method) {
            case "clear" -> HirDraw.Command.CLEAR;
            case "fill", "stroke" -> HirDraw.Command.COLOR;
            case "fillRect" -> HirDraw.Command.RECT;
            case "strokeRect" -> HirDraw.Command.LINE_RECT;
            case "line" -> HirDraw.Command.LINE;
            default -> null;
        };
        if (command == null) {
            error("MPL3203", "Display 不支持绘制方法：" + method, span);
            return new HirExpressionStatement(new HirConstant("0", ValueType.ERROR));
        }
        if (command == HirDraw.Command.CLEAR || command == HirDraw.Command.COLOR) {
            if (sourceArguments.size() != 1) error("MPL3203", "Display." + method + "(...) 需要一个 Color 参数", span);
            List<HirExpression> color = sourceArguments.size() == 1
                ? analyzeColor(sourceArguments.get(0)) : List.<HirExpression>of(new HirConstant("0", ValueType.ERROR),
                    new HirConstant("0", ValueType.ERROR), new HirConstant("0", ValueType.ERROR),
                    new HirConstant("0", ValueType.ERROR));
            return new HirDraw(display.mplName(), command, color.subList(0, command.argumentCount()));
        }
        if (sourceArguments.size() != command.argumentCount()) {
            error("MPL3203", "Display." + method + "(...) 的参数数量不匹配", span);
        }
        List<HirExpression> arguments = new ArrayList<>();
        for (Expression source : sourceArguments) {
            HirExpression argument = analyzeExpression(source);
            requireNumeric(argument.type(), source.span(), "Display." + method + " 参数");
            arguments.add(argument);
        }
        while (arguments.size() < command.argumentCount()) arguments.add(new HirConstant("0", ValueType.ERROR));
        return new HirDraw(display.mplName(), command,
            arguments.subList(0, command.argumentCount()));
    }

    /** Type-checks the public MIL draw macro without exposing raw draw commands in MPL. */
    private HirStatement analyzeMilDraw(MilDrawStatement draw) {
        HardwareContract.LinkDeclaration display = hardwareLinks.get(draw.hardwareName());
        if (display == null || !"Display".equals(display.mplType())) {
            error("MIL3201", "@io.draw 的目标必须是硬件声明中的 Display", draw.span());
            return new HirExpressionStatement(new HirConstant("0", ValueType.ERROR));
        }
        HirDraw.Command command = switch (draw.command()) {
            case "clear" -> HirDraw.Command.CLEAR;
            case "color" -> HirDraw.Command.COLOR;
            case "rect" -> HirDraw.Command.RECT;
            case "lineRect" -> HirDraw.Command.LINE_RECT;
            case "line" -> HirDraw.Command.LINE;
            default -> null;
        };
        if (command == null) {
            error("MIL3201", "target 不支持绘制命令：" + draw.command(), draw.span());
            return new HirExpressionStatement(new HirConstant("0", ValueType.ERROR));
        }
        if (draw.arguments().size() != command.argumentCount()) {
            error("MIL3201", "绘制命令 " + draw.command() + " 需要 " + command.argumentCount()
                + " 个参数，实际为 " + draw.arguments().size(), draw.span());
        }
        List<HirExpression> arguments = new ArrayList<>();
        for (Expression source : draw.arguments()) {
            HirExpression argument = analyzeExpression(source);
            requireNumeric(argument.type(), source.span(), "@io.draw 参数");
            arguments.add(argument);
        }
        while (arguments.size() < command.argumentCount()) arguments.add(new HirConstant("0", ValueType.ERROR));
        return new HirDraw(display.gameAlias(), command, arguments.subList(0, command.argumentCount()));
    }

    private List<HirExpression> analyzeColor(Expression source) {
        if (source instanceof MemberAccessExpression member && member.target() instanceof Identifier namespace
            && "Color".equals(namespace.name())) {
            return switch (member.member()) {
                case "black" -> color(0, 0, 0, 255);
                case "white" -> color(255, 255, 255, 255);
                case "red" -> color(255, 0, 0, 255);
                case "green" -> color(0, 255, 0, 255);
                default -> invalidColor(source, "Color 不支持常量：" + member.member());
            };
        }
        if (source instanceof CallExpression call && call.callee() instanceof MemberAccessExpression member
            && member.target() instanceof Identifier namespace && "Color".equals(namespace.name()) && "rgb".equals(member.member())) {
            if (call.arguments().size() != 3 && call.arguments().size() != 4) {
                return invalidColor(source, "Color.rgb(...) 需要 3 或 4 个 Int 参数");
            }
            List<HirExpression> values = new ArrayList<>();
            for (Expression argument : call.arguments()) {
                if (!(argument instanceof IntegerLiteral literal) || literal.value() < 0 || literal.value() > 255) {
                    error("MPL3203", "当前 Color.rgb(...) 仅接受 0 到 255 的 Int 字面量", argument.span());
                }
                HirExpression value = analyzeExpression(argument);
                if (value.type() != ValueType.INT) error("MPL3203", "Color.rgb(...) 参数必须是 Int", argument.span());
                values.add(value);
            }
            if (values.size() == 3) values.add(new HirConstant("255", ValueType.INT));
            return List.copyOf(values);
        }
        return invalidColor(source, "Color 参数必须是 Color 常量或 Color.rgb(...)");
    }

    private List<HirExpression> color(int red, int green, int blue, int alpha) {
        return List.of(new HirConstant(Integer.toString(red), ValueType.INT), new HirConstant(Integer.toString(green), ValueType.INT),
            new HirConstant(Integer.toString(blue), ValueType.INT), new HirConstant(Integer.toString(alpha), ValueType.INT));
    }

    private List<HirExpression> invalidColor(Expression source, String message) {
        error("MPL3203", message, source.span());
        return color(0, 0, 0, 0);
    }

    private HirStatement analyzeBuildingControl(HardwareContract.LinkDeclaration link, String method,
                                                List<Expression> sourceArguments, SourceSpan span) {
        return analyzeBuildingControl(new HirHardwareLink(link.mplName(), link.gameAlias(), link.mplType()), link.mplType(),
            link.mplName(), method, sourceArguments, span);
    }

    private HirStatement analyzeBuildingControl(HirExpression target, String buildingType, String targetName, String method,
                                                List<Expression> sourceArguments, SourceSpan span) {
        TargetProfile.BuildingType building = profile.buildingType(buildingType).orElse(null);
        TargetProfile.BuildingAction action = building == null ? null : building.actions().get(method);
        if (action == null) {
            error("MPL3201", "建筑 " + targetName + "（" + buildingType + "）不支持控制方法：" + method, span);
            return new HirExpressionStatement(new HirConstant("0", ValueType.ERROR));
        }
        if (sourceArguments.size() != action.parameterTypes().size()) {
            error("MPL3201", "硬件控制 " + method + " 的参数数量不匹配", span);
        }
        List<HirExpression> arguments = new ArrayList<>();
        for (int index = 0; index < sourceArguments.size(); index++) {
            Expression sourceArgument = sourceArguments.get(index);
            HirExpression argument = analyzeExpression(sourceArgument);
            if (index < action.parameterTypes().size()
                && !action.parameterTypes().get(index).canAssignFrom(argument.type())) {
                error("MPL3201", "硬件控制 " + method + " 的参数类型不匹配", sourceArgument.span());
            }
            arguments.add(argument);
        }
        String control = action.target().substring("control ".length());
        return new HirBuildingControl(target, control, arguments);
    }

    private HirStatement analyzeLegacyMethodCall(MethodCallExpression call) {
        if (unavailablePackageHardware(call.target())) {
            error("MPL1414", "包代码不能访问未通过 with 注入的根项目硬件", call.span());
            return new HirExpressionStatement(new HirConstant("0", ValueType.ERROR));
        }
        String linkName = messages.get(call.target());
        if (linkName != null && "print".equals(call.method())) {
            return analyzePrintCall(linkName, call.arguments());
        }
        error("MPL3201", "当前阶段不支持该成员调用", call.span());
        return new HirExpressionStatement(new HirConstant("0", ValueType.ERROR));
    }

    private HirStatement analyzeArraySet(String target, Symbol symbol, CollectionType type,
                                         List<Expression> arguments, SourceSpan span) {
        if (type.kind() != CollectionType.Kind.ARRAY) {
            error("MPL3601", "只有 Array 支持 set(index, value)", span);
            return new HirExpressionStatement(new HirConstant("0", ValueType.ERROR));
        }
        if (!symbol.mutable()) error("MPL3104", "不能修改 val Array：" + target, span);
        if (arguments.size() != 2) {
            error("MPL3601", "Array.set(index, value) 需要两个参数", span);
            return new HirExpressionStatement(new HirConstant("0", ValueType.ERROR));
        }
        HirExpression value = analyzeExpression(arguments.get(1));
        if (!type.elementType().canAssignFrom(value.type())) {
            error("MPL3103", "不能将 " + display(value.type()) + " 写入 " + type.displayName(), arguments.get(1).span());
        }
        Expression sourceIndex = arguments.get(0);
        if (sourceIndex instanceof IntegerLiteral) {
            return staticAggregateIndex(sourceIndex, symbol.staticAggregateSize())
                .<HirStatement>map(valueAt -> new HirCollectionSet(target, valueAt, value))
                .orElseGet(() -> new HirExpressionStatement(new HirConstant("0", ValueType.ERROR)));
        }
        HirExpression index = analyzeExpression(sourceIndex);
        if (index.type() != ValueType.INT) {
            error("MPL3601", "Array.set(...) 下标必须是 Int", sourceIndex.span());
            return new HirExpressionStatement(new HirConstant("0", ValueType.ERROR));
        }
        if (!supportsDynamicArrayElement(type.elementType())) {
            error("MPL3601", "动态 Array 下标当前只支持 Int、Float 或 Bool 元素", span);
            return new HirExpressionStatement(new HirConstant("0", ValueType.ERROR));
        }
        if (!hasArrayBoundsProof(target, sourceIndex)) {
            error("MPL3601", "无法证明动态 Array 下标在范围内；请使用从 0 到 array.size 的标准计数 for 循环", sourceIndex.span());
            return new HirExpressionStatement(new HirConstant("0", ValueType.ERROR));
        }
        return new HirDynamicCollectionSet(target, index, value);
    }

    private HirStatement analyzePrintCall(String linkName, List<Expression> sourceArguments) {
        List<HirExpression> arguments = new ArrayList<>();
        for (Expression argument : sourceArguments) {
            HirExpression value = analyzePrintValue(argument);
            if (value.type() != ValueType.INT && value.type() != ValueType.FLOAT && value.type() != ValueType.BOOL
                && value.type() != ValueType.STRING
                && !(value instanceof HirText)) {
                error("MPL3202", "print 参数必须是数值、Bool 或字符串字面量", argument.span());
            }
            arguments.add(value);
        }
        int staticLength = arguments.stream().mapToInt(this::staticStringLength).sum();
        if (staticLength > profile.maxMessageUtf16CodeUnits()) {
            error("MPL3202", "print 的静态文本上界为 " + staticLength + " 个 UTF-16 代码单元，超过 target "
                + profile.id() + " 的 " + profile.maxMessageUtf16CodeUnits() + " 个上限", sourceArguments.get(0).span());
        }
        return new HirPrintStatement(linkName, arguments);
    }

    /**
     * A print call is the one String-concatenation context that does not need
     * a String value at runtime. The target emitter appends each leaf in order.
     */
    private HirExpression analyzePrintValue(Expression source) {
        if (source instanceof BinaryExpression binary && "+".equals(binary.operator())) {
            HirExpression left = analyzePrintValue(binary.left());
            HirExpression right = analyzePrintValue(binary.right());
            if (left.type() == ValueType.STRING && right.type() == ValueType.STRING) {
                if (left instanceof HirText leftText && right instanceof HirText rightText) {
                    return new HirText(leftText.value() + rightText.value());
                }
                return new HirBinary(left, "+", right, ValueType.STRING);
            }
            error("MPL3103", "print 中的 String 拼接两侧都必须是 String", binary.span());
            return new HirConstant("0", ValueType.ERROR);
        }
        return analyzeExpression(source);
    }

    private HirStatement analyzeUnitControl(String sourceBinding, boolean storedReference, String command,
                                            List<Expression> sourceArguments, SourceSpan span) {
        Optional<TargetProfile.UnitAction> action = profile.unitAction(command);
        if (action.isEmpty()) {
            error("MPL3305", "当前 target 的 Unit 不支持控制动作：" + command, span);
            return new HirExpressionStatement(new HirConstant("0", ValueType.ERROR));
        }
        if (sourceArguments.size() != action.orElseThrow().parameterTypes().size()) {
            error("MPL3305", "Unit." + command + "(...) 参数数量不匹配", span);
        }
        List<HirExpression> arguments = new ArrayList<>();
        for (int index = 0; index < sourceArguments.size(); index++) {
            Expression argument = sourceArguments.get(index);
            HirExpression value = analyzeExpression(argument);
            if (index < action.orElseThrow().parameterTypes().size()
                && !action.orElseThrow().parameterTypes().get(index).canAssignFrom(value.type())) {
                error("MPL3305", "Unit." + command + " 参数类型不匹配", argument.span());
            }
            arguments.add(value);
        }
        String binding = storedReference || activeUnitBinding == null ? sourceBinding : activeUnitBinding;
        return new HirUnitControl(binding, storedReference, command, arguments);
    }

    private HirStatement analyzeDeclaration(VariableDeclaration declaration) {
        MplType declaredType = declaration.declaredType().map(value -> parseType(value, declaration.span())).orElse(null);
        HirUnitQuery unitQuery = resolveSavedUnitQuery(declaration.initializer(), "_");
        if (unitQuery == null) {
            unitQuery = parseUnitQuery(declaration.initializer())
                .map(query -> analyzeUnitQuery(query, "_"))
                .orElse(null);
        }
        HirBuildingQuery buildingQuery = unitQuery == null
            ? resolveSavedBuildingQuery(declaration.initializer(), "_") : null;
        if (unitQuery == null && buildingQuery == null) {
            buildingQuery = parseBuildingQuery(declaration.initializer())
                .map(query -> analyzeBuildingQuery(query, "_"))
                .orElse(null);
        }
        HirExpression initializer = unitQuery != null ? unitQuery
            : buildingQuery != null ? buildingQuery
            : analyzeInitializer(declaration.initializer(), declaredType);
        if (initializer.type() == ValueType.BUILDING) {
            error("MPL3201", "硬件常量不能赋给普通变量；请直接读取字段或调用控制方法", declaration.initializer().span());
        }
        MplType type = declaredType == null ? initializer.type() : declaredType;
        if (declaredType == null && initializer.type() == ValueType.NULL) {
            error("MPL3103", "不能仅从 null 推导变量类型；请显式声明可空对象类型", declaration.span());
            type = ValueType.ERROR;
        }
        if (type == ValueType.VOID) error("MPL3103", "变量不能使用 Void 类型", declaration.span());
        if (!type.canAssignFrom(initializer.type())) {
            error("MPL3103", "不能将 " + display(initializer.type()) + " 赋给 " + display(type), declaration.initializer().span());
        }
        if (isAggregate(type) && !(initializer instanceof HirArrayLiteral)
            && !(initializer instanceof HirTupleLiteral) && !(initializer instanceof HirCollectionLiteral)) {
            error("MPL3601", "当前阶段不支持复制聚合值；请在声明处使用字面量或集合工厂", declaration.initializer().span());
        }
        if (type == ValueType.STRING && declaration.mutable()) {
            error("MPL3103", "当前阶段 String 仅支持 val 静态值；动态 String runtime 尚未启用", declaration.span());
        }
        if (type instanceof UnitSetType && declaration.mutable()) {
            error("MPL3301", "Set<Unit<T>> 是不可变的惰性查询描述符，只能使用 val 声明", declaration.span());
        }
        if (type instanceof UnitSetType && currentFunction != null) {
            error("MPL3508", "第一版函数不能声明 Set<Unit<T>> 查询", declaration.span());
        }
        if (type instanceof UnitSetType && unitQuery == null) {
            error("MPL3301", "Set<Unit<T>> 变量必须由 Unit.getAll类型(...) 查询初始化", declaration.initializer().span());
        }
        if (type instanceof LinkedBuildingSetType && declaration.mutable()) {
            error("MPL3201", "LinkedBuildingSet<T> 是不可变的惰性查询描述符，只能使用 val 声明", declaration.span());
        }
        if (type instanceof LinkedBuildingSetType && buildingQuery == null) {
            error("MPL3201", "LinkedBuildingSet<T> 变量必须由 Building.getAll类型(...) 查询初始化",
                declaration.initializer().span());
        }
        boolean global = currentFunction == null && scopes.size() == 1;
        if (declare(declaration.name(),
            new Symbol(type, declaration.mutable(), staticStringLength(initializer), staticAggregateSize(initializer), unitQuery,
                buildingQuery, global, declaration.span()),
            declaration.span()) && global) {
            // The initializer has already been analyzed, so calls inside it
            // deliberately do not observe this variable as initialized.
            initializedGlobals.add(declaration.name());
        }
        return new HirVariableDeclaration(declaration.name(), type, declaration.mutable(), initializer);
    }

    /** Resolves empty aggregate literals from an explicit declaration type. */
    private HirExpression analyzeInitializer(Expression initializer, MplType expectedType) {
        if (expectedType instanceof CollectionType collection && initializer instanceof ArrayLiteral array
            && array.elements().isEmpty() && collection.kind() == CollectionType.Kind.ARRAY) {
            return new HirArrayLiteral(List.of(), collection);
        }
        if (expectedType instanceof CollectionType collection && initializer instanceof CallExpression call
            && call.arguments().isEmpty() && collectionFactory(call.callee()).filter(collection.kind()::equals).isPresent()) {
            return new HirCollectionLiteral(List.of(), collection);
        }
        return analyzeExpression(initializer);
    }

    private HirExpression analyzeExpression(Expression expression) {
        if (expression instanceof IntegerLiteral integer) {
            return new HirConstant(Long.toString(integer.value()), ValueType.INT);
        }
        if (expression instanceof FloatLiteral decimal) {
            if (!Double.isFinite(decimal.value())) {
                error("MPL3103", "Float 字面量必须是有限值", decimal.span());
                return new HirConstant("0.0", ValueType.ERROR);
            }
            return new HirConstant(Double.toString(decimal.value()), ValueType.FLOAT);
        }
        if (expression instanceof StringLiteral text) return new HirText(text.value());
        if (expression instanceof ArrayLiteral array) return analyzeArrayLiteral(array);
        if (expression instanceof TupleLiteral tuple) return analyzeTupleLiteral(tuple);
        if (expression instanceof BooleanLiteral bool) {
            return new HirConstant(bool.value() ? "1" : "0", ValueType.BOOL);
        }
        if (expression instanceof NullLiteral) return new HirConstant("null", ValueType.NULL);
        if (expression instanceof Identifier identifier) {
            HardwareContract.LinkDeclaration hardware = hardwareLinks.get(identifier.name());
            if (hardware != null) return new HirHardwareLink(hardware.mplName(), hardware.gameAlias(), hardware.mplType());
            HardwareContract.Resource resource = hardwareResources.get(identifier.name());
            if (resource != null) return new HirVariable(resource.mplName(), ValueType.BUILDING);
            Symbol symbol = lookup(identifier.name());
            if (symbol == null) {
                error("MPL3102", "未声明的变量：" + identifier.name(), identifier.span());
                return new HirVariable(identifier.name(), ValueType.ERROR);
            }
            if (currentFunction != null && symbol.type() instanceof UnitType) {
                error("MPL3508", "第一版函数不能访问已保存的 UnitRef", identifier.span());
            }
            recordGlobalAccess(identifier.name(), symbol, identifier.span());
            return new HirVariable(identifier.name(), symbol.type());
        }
        if (expression instanceof IndexExpression access) {
            return analyzeIndexAccess(access);
        }
        if (expression instanceof MemberAccessExpression member) {
            return analyzeMemberAccess(member);
        }
        if (expression instanceof CallExpression call) {
            return analyzeCallExpression(call);
        }
        if (expression instanceof LambdaExpression lambda) {
            error("MPL3303", "lambda 只能作为 Unit.getAll类型(...) 或 .where(...) 的参数", lambda.span());
            return new HirConstant("0", ValueType.ERROR);
        }
        if (expression instanceof MethodCallExpression call) {
            error("MPL3201", "硬件调用只能作为独立语句", call.span());
            return new HirConstant("0", ValueType.ERROR);
        }
        if (expression instanceof UnaryExpression unary) {
            if ("-".equals(unary.operator()) && unary.operand() instanceof IntegerLiteral literal) {
                // Preserve a signed literal until the optimizer applies Int saturation.
                // Normalizing its positive half would make Int.min unrepresentable.
                return new HirConstant(Long.toString(-literal.value()), ValueType.INT);
            }
            HirExpression operand = analyzeExpression(unary.operand());
            ValueType type = switch (unary.operator()) {
                case "+", "-" -> requireNumeric(operand.type(), unary.span(), "一元运算符 " + unary.operator());
                case "!" -> requireBool(operand.type(), unary.span(), "一元运算符 !");
                default -> ValueType.ERROR;
            };
            return new HirUnary(unary.operator(), operand, type);
        }
        if (expression instanceof BinaryExpression binary) {
            HirExpression left = analyzeExpression(binary.left());
            HirExpression right = analyzeExpression(binary.right());
            if ("+".equals(binary.operator()) && left instanceof HirText leftText && right instanceof HirText rightText) {
                return new HirText(leftText.value() + rightText.value());
            }
            ValueType type = binaryType(binary.operator(), left.type(), right.type(), binary.span());
            return new HirBinary(left, binary.operator(), right, type);
        }
        AssignmentExpression assignment = (AssignmentExpression) expression;
        Symbol target = lookup(assignment.target().name());
        HirExpression value = analyzeExpression(assignment.value());
        if (target == null) {
            error("MPL3102", "未声明的变量：" + assignment.target().name(), assignment.target().span());
            return new HirAssignment(assignment.target().name(), assignment.operator(), value, ValueType.ERROR);
        }
        recordGlobalAccess(assignment.target().name(), target, assignment.target().span());
        if (!target.mutable()) {
            error("MPL3104", "不能给 val 重新赋值：" + assignment.target().name(), assignment.target().span());
        }
        if (arrayBoundsProofs.stream().anyMatch(proof -> proof.index().equals(assignment.target().name()))) {
            error("MPL3601", "不能在已证明边界的 for 循环体内修改下标变量：" + assignment.target().name(), assignment.span());
        }
        if ("=".equals(assignment.operator())) {
            if (isAggregate(target.type())) {
                error("MPL3601", "当前阶段不能整体重新赋值聚合值；可变 Array 请使用 set(index, value)", assignment.span());
            }
            if (!target.type().canAssignFrom(value.type())) {
                error("MPL3103", "不能将 " + display(value.type()) + " 赋给 " + display(target.type()), assignment.value().span());
            }
        } else {
            ValueType result = binaryType(assignment.operator().substring(0, 1), target.type(), value.type(), assignment.span());
            if (!target.type().canAssignFrom(result)) {
                error("MPL3103", "复合赋值结果不能赋给 " + display(target.type()), assignment.span());
            }
        }
        return new HirAssignment(assignment.target().name(), assignment.operator(), value, target.type());
    }

    private HirExpression analyzeMemberAccess(MemberAccessExpression member) {
        if (member.target() instanceof Identifier identifier && "Clock".equals(identifier.name())) {
            return clockIntrinsic(member.member(), List.of(), member.span());
        }
        if (member.target() instanceof Identifier identifier) {
            if (unavailablePackageHardware(identifier.name())) {
                error("MPL1414", "包代码不能访问未通过 with 注入的根项目硬件", member.span());
                return new HirConstant("0", ValueType.ERROR);
            }
            HardwareContract.Resource resource = hardwareResources.get(identifier.name());
            if (resource != null && "Display".equals(resource.mplType())
                && ("width".equals(member.member()) || "height".equals(member.member()))) {
                HardwareContract.DisplayLayout layout = resource.display().orElse(null);
                if (layout == null) {
                    error("MPL3203", "Display 编译期尺寸未知；请在 link(...) 中声明 width/height", member.span());
                    return new HirConstant("0", ValueType.ERROR);
                }
                int value = "width".equals(member.member()) ? layout.width() : layout.height();
                return new HirConstant(Integer.toString(value), ValueType.INT);
            }
            HardwareContract.LinkDeclaration hardware = hardwareLinks.get(identifier.name());
            if (hardware != null) {
                TargetProfile.BuildingType building = profile.buildingType(hardware.mplType()).orElse(null);
                ValueType type = building == null ? null : building.propertyTypes().get(member.member());
                if (type == null) {
                    error("MPL3201", "硬件 " + hardware.mplName() + "（" + hardware.mplType()
                        + "）不支持只读属性：" + member.member(), member.span());
                    return new HirConstant("0", ValueType.ERROR);
                }
                return new HirMemberAccess(new HirHardwareLink(hardware.mplName(), hardware.gameAlias(), hardware.mplType()),
                    member.member(), type);
            }
        }
        if ("size".equals(member.member())) {
            HirUnitQuery query = resolveSavedUnitQuery(member.target(), "_");
            if (query == null) {
                query = parseUnitQuery(member.target()).map(value -> analyzeUnitQuery(value, "_")).orElse(null);
            }
            if (query != null) {
                if (currentFunction != null) {
                    error("MPL3508", "第一版函数不能扫描 Set<Unit<T>>.size", member.span());
                    return new HirConstant("0", ValueType.ERROR);
                }
                if (unitIterationDepth > 0) {
                    error("MPL3306", "Set<Unit<T>>.size 不能嵌套在 Unit 遍历中", member.span());
                    return new HirConstant("0", ValueType.ERROR);
                }
                return new HirUnitQuerySize(query);
            }
            HirBuildingQuery buildingQuery = resolveBuildingQuery(member.target(), "_");
            if (buildingQuery != null) return new HirBuildingQuerySize(buildingQuery);
        }
        HirExpression target = analyzeExpression(member.target());
        if ("size".equals(member.member()) && isAggregate(target.type())) {
            Integer size = aggregateSize(member.target(), target.type());
            if (size == null) {
                error("MPL3601", "当前聚合值缺少可静态证明的长度", member.span());
                return new HirConstant("0", ValueType.ERROR);
            }
            return new HirConstant(Integer.toString(size), ValueType.INT);
        }
        if (target.type() == ValueType.UNIT) {
            if ("flag".equals(member.member())) {
                error("MPL3304", "Unit.flag 是编译器私有运行时属性，MPL 不允许访问", member.span());
                return new HirMemberAccess(target, member.member(), ValueType.ERROR);
            }
            Optional<ValueType> type = profile.unitPropertyType(member.member());
            if (type.isEmpty()) {
                error("MPL3304", "当前 target 的 Unit 不支持只读属性：" + member.member(), member.span());
                return new HirMemberAccess(target, member.member(), ValueType.ERROR);
            }
            return new HirMemberAccess(target, member.member(), type.orElseThrow());
        }
        if (target.type() instanceof UnitType unit) {
            if (unit.nullable()) {
                error("MPL3308", "可空 " + unit.displayName() + " 必须先通过 != null 检查", member.span());
                return new HirMemberAccess(target, member.member(), ValueType.ERROR);
            }
            if (currentFunction != null || unitIterationDepth > 0) {
                error("MPL3306", "第一版不能在函数或 Unit 遍历体中重绑已保存的 UnitRef", member.span());
            }
            if ("flag".equals(member.member())) {
                error("MPL3304", "Unit.flag 是编译器私有运行时属性，MPL 不允许访问", member.span());
                return new HirMemberAccess(target, member.member(), ValueType.ERROR);
            }
            Optional<ValueType> type = profile.unitPropertyType(member.member());
            if (type.isEmpty()) {
                error("MPL3304", "当前 target 的 Unit 不支持只读属性：" + member.member(), member.span());
                return new HirMemberAccess(target, member.member(), ValueType.ERROR);
            }
            return new HirMemberAccess(target, member.member(), type.orElseThrow());
        }
        if (target.type() instanceof BuildingType buildingReference) {
            if (buildingReference.nullable()) {
                error("MPL3211", "可空 " + buildingReference.displayName() + " 必须先通过 != null 检查", member.span());
                return new HirMemberAccess(target, member.member(), ValueType.ERROR);
            }
            TargetProfile.BuildingType building = profile.buildingType(buildingReference.buildingType()).orElseThrow();
            ValueType type = building.propertyTypes().get(member.member());
            if (type == null) {
                error("MPL3201", "建筑 " + buildingReference.buildingType() + " 不支持只读属性：" + member.member(),
                    member.span());
                return new HirMemberAccess(target, member.member(), ValueType.ERROR);
            }
            return new HirMemberAccess(target, member.member(), type);
        }
        if (target.type() == ValueType.BUILDING && target instanceof HirVariable variable
            && variable.name().equals(activeBuildingBinding) && activeBuildingType != null) {
            TargetProfile.BuildingType building = profile.buildingType(activeBuildingType).orElseThrow();
            ValueType type = building.propertyTypes().get(member.member());
            if (type == null) {
                error("MPL3201", "建筑 " + activeBuildingType + " 不支持只读属性：" + member.member(), member.span());
                return new HirMemberAccess(target, member.member(), ValueType.ERROR);
            }
            return new HirMemberAccess(target, member.member(), type);
        }
        error("MPL3201", "当前阶段不支持该成员访问", member.span());
        return new HirConstant("0", ValueType.ERROR);
    }

    private boolean unavailablePackageHardware(String name) {
        return name.startsWith("__package_hardware_unavailable_");
    }

    private HirExpression analyzeCallExpression(CallExpression call) {
        Optional<CollectionType.Kind> factory = collectionFactory(call.callee());
        if (factory.isPresent()) return analyzeCollectionFactory(factory.orElseThrow(), call.arguments(), call.span());
        if (call.callee() instanceof MemberAccessExpression member && "get".equals(member.member())
            && call.arguments().size() == 1) {
            HirUnitQuery query = resolveUnitQuery(member.target(), "_");
            if (query != null) return analyzeUnitQueryGet(query, call.arguments().get(0), call.span());
            HirBuildingQuery buildingQuery = resolveBuildingQuery(member.target(), "_");
            if (buildingQuery != null) return analyzeBuildingQueryGet(buildingQuery, call.arguments().get(0), call.span());
            return analyzeIndexAccess(member.target(), call.arguments().get(0), call.span());
        }
        if (call.callee() instanceof MemberAccessExpression member && "get".equals(member.member())) {
            HirUnitQuery query = resolveUnitQuery(member.target(), "_");
            if (query != null) {
                error("MPL3309", "Set<Unit<T>>.get(index) 需要恰好一个 Int 参数", call.span());
                return new HirConstant("null", ValueType.ERROR);
            }
            HirBuildingQuery buildingQuery = resolveBuildingQuery(member.target(), "_");
            if (buildingQuery != null) {
                error("MPL3210", "LinkedBuildingSet<T>.get(index) 需要恰好一个 Int 参数", call.span());
                return new HirConstant("null", ValueType.ERROR);
            }
        }
        if (call.callee() instanceof MemberAccessExpression member && "contains".equals(member.member())
            && call.arguments().size() == 1) {
            return analyzeCollectionContains(member.target(), call.arguments().get(0), call.span());
        }
        if (call.callee() instanceof Identifier functionName) {
            FunctionSignature signature = functions.get(functionName.name());
            if (signature == null) {
                error("MPL3501", "未声明的函数：" + functionName.name(), call.span());
                return new HirConstant("0", ValueType.ERROR);
            }
            if (call.arguments().size() != signature.parameterTypes().size()) {
                error("MPL3503", "函数 " + functionName.name() + " 的参数数量不匹配", call.span());
            }
            List<HirExpression> arguments = new ArrayList<>();
            for (int index = 0; index < call.arguments().size(); index++) {
                Expression source = call.arguments().get(index);
                HirExpression argument = analyzeExpression(source);
                if (index < signature.parameterTypes().size()
                    && !signature.parameterTypes().get(index).canAssignFrom(argument.type())) {
                    error("MPL3503", "函数 " + functionName.name() + " 的第 " + (index + 1) + " 个参数类型不匹配",
                        source.span());
                }
                arguments.add(argument);
            }
            if (currentFunction != null) callGraph.get(currentFunction).add(functionName.name());
            if (analyzingTopLevel) {
                topLevelCalls.add(new TopLevelCall(functionName.name(), Set.copyOf(initializedGlobals), call.span()));
            }
            return new HirFunctionCall(functionName.name(), arguments, signature.returnType());
        }
        if (call.callee() instanceof MemberAccessExpression member
            && member.target() instanceof Identifier namespace) {
            if ("Math".equals(namespace.name())) return mathIntrinsic(member.member(), call.arguments(), call.span());
            if ("Clock".equals(namespace.name())) return clockIntrinsic(member.member(), call.arguments(), call.span());
            if ("Int".equals(namespace.name())) return intIntrinsic(member.member(), call.arguments(), call.span());
        }
        if (call.callee() instanceof MemberAccessExpression member
            && member.target() instanceof Identifier namespace
            && "Unit".equals(namespace.name())
            && member.member().startsWith("getAll")) {
            error("MPL3301", "Unit.getAll类型() 只能用作 for 的遍历目标", call.span());
            return new HirConstant("0", ValueType.ERROR);
        }
        if (call.callee() instanceof MemberAccessExpression member
            && member.target() instanceof Identifier identifier
            && hardwareLinks.containsKey(identifier.name())) {
            error("MPL3201", "硬件控制方法只能作为独立语句调用", call.span());
            return new HirConstant("0", ValueType.ERROR);
        }
        error("MPL3201", "当前阶段不支持该调用表达式", call.span());
        return new HirConstant("0", ValueType.ERROR);
    }

    private HirExpression mathIntrinsic(String name, List<Expression> sourceArguments, SourceSpan span) {
        if (!"sin".equals(name) && !"cos".equals(name)) {
            error("MPL3201", "Math 目前仅支持 sin(x) 与 cos(x)", span);
            return new HirConstant("0", ValueType.ERROR);
        }
        if (sourceArguments.size() != 1) {
            error("MPL3201", "Math." + name + "(...) 需要恰好一个数值参数", span);
        }
        List<HirExpression> arguments = new ArrayList<>();
        for (Expression argument : sourceArguments) {
            HirExpression value = analyzeExpression(argument);
            requireNumeric(value.type(), argument.span(), "Math." + name + " 参数");
            arguments.add(value);
        }
        return new HirIntrinsicCall("Math", name, arguments, ValueType.FLOAT);
    }

    private HirExpression clockIntrinsic(String name, List<Expression> sourceArguments, SourceSpan span) {
        if (!sourceArguments.isEmpty()) {
            error("MPL3201", "Clock." + name + " 不接受参数", span);
        }
        ValueType type = switch (name) {
            // v146 exposes both @time and @tick as game-time doubles.  Keep the
            // exact target value rather than silently truncating @tick to Int.
            case "timeMs", "time", "timeMinutes", "timeHours", "tick" -> ValueType.FLOAT;
            default -> ValueType.ERROR;
        };
        if (type == ValueType.ERROR) {
            error("MPL3201", "Clock 目前支持 timeMs、time、timeMinutes、timeHours 与 tick", span);
            return new HirConstant("0", ValueType.ERROR);
        }
        return new HirIntrinsicCall("Clock", name, List.of(), type);
    }

    private HirExpression intIntrinsic(String name, List<Expression> sourceArguments, SourceSpan span) {
        if (!"floor".equals(name) && !"ceil".equals(name) && !"round".equals(name)) {
            error("MPL3201", "Int 目前支持 floor(x)、ceil(x) 与 round(x)", span);
            return new HirConstant("0", ValueType.ERROR);
        }
        if (sourceArguments.size() != 1) {
            error("MPL3201", "Int." + name + "(...) 需要恰好一个 Float 参数", span);
        }
        List<HirExpression> arguments = new ArrayList<>();
        for (Expression argument : sourceArguments) {
            HirExpression value = analyzeExpression(argument);
            if (value.type() != ValueType.FLOAT && value.type() != ValueType.ERROR) {
                error("MPL3103", "Int." + name + "(...) 只接受 Float 参数", argument.span());
            }
            arguments.add(value);
        }
        return new HirIntrinsicCall("Int", name, arguments, ValueType.INT);
    }

    private ValueType binaryType(String operator, MplType left, MplType right, SourceSpan span) {
        return switch (operator) {
            case "+" -> left == ValueType.STRING || right == ValueType.STRING
                ? typeError("String 拼接当前仅支持两个字符串字面量；动态拼接需要 String runtime", span)
                : numericResult(left, right, span, "运算符 +");
            case "-", "*" -> numericResult(left, right, span, "运算符 " + operator);
            case "/" -> {
                requireNumeric(left, span, "运算符 /");
                requireNumeric(right, span, "运算符 /");
                yield left == ValueType.ERROR || right == ValueType.ERROR ? ValueType.ERROR : ValueType.FLOAT;
            }
            case "%" -> left == ValueType.INT && right == ValueType.INT ? ValueType.INT
                : typeError("运算符 % 只接受 Int", span);
            case "<", "<=", ">", ">=" -> {
                requireNumeric(left, span, "比较运算符 " + operator);
                requireNumeric(right, span, "比较运算符 " + operator);
                yield left == ValueType.ERROR || right == ValueType.ERROR ? ValueType.ERROR : ValueType.BOOL;
            }
            case "==", "!=" -> compatibleForEquality(left, right, span) ? ValueType.BOOL : ValueType.ERROR;
            case "&&", "||" -> {
                requireBool(left, span, "逻辑运算符 " + operator);
                requireBool(right, span, "逻辑运算符 " + operator);
                yield left == ValueType.ERROR || right == ValueType.ERROR ? ValueType.ERROR : ValueType.BOOL;
            }
            default -> typeError("暂不支持的运算符：" + operator, span);
        };
    }

    private ValueType numericResult(MplType left, MplType right, SourceSpan span, String context) {
        requireNumeric(left, span, context);
        requireNumeric(right, span, context);
        if (left == ValueType.ERROR || right == ValueType.ERROR) return ValueType.ERROR;
        return left == ValueType.FLOAT || right == ValueType.FLOAT ? ValueType.FLOAT : ValueType.INT;
    }

    private ValueType requireNumeric(MplType type, SourceSpan span, String context) {
        if (type == ValueType.INT || type == ValueType.FLOAT || type == ValueType.ERROR) {
            return (ValueType) type;
        }
        return typeError(context + " 只接受 Int 或 Float", span);
    }

    private ValueType requireBool(MplType type, SourceSpan span, String context) {
        if (type == ValueType.BOOL || type == ValueType.ERROR) return (ValueType) type;
        return typeError(context + " 只接受 Bool", span);
    }

    private boolean compatibleForEquality(MplType left, MplType right, SourceSpan span) {
        if (left == ValueType.ERROR || right == ValueType.ERROR || left == right
            || left == ValueType.INT && right == ValueType.FLOAT || left == ValueType.FLOAT && right == ValueType.INT) {
            return true;
        }
        if (left instanceof UnitType unit && right == ValueType.NULL) {
            if (unit.nullable()) return true;
            error("MPL3103", "非空 " + unit.displayName() + " 不需要与 null 比较", span);
            return false;
        }
        if (right instanceof UnitType unit && left == ValueType.NULL) {
            if (unit.nullable()) return true;
            error("MPL3103", "非空 " + unit.displayName() + " 不需要与 null 比较", span);
            return false;
        }
        if (left instanceof UnitType leftUnit && right instanceof UnitType rightUnit
            && leftUnit.unitType().equals(rightUnit.unitType())) {
            return true;
        }
        if (left instanceof BuildingType building && right == ValueType.NULL) {
            if (building.nullable()) return true;
            error("MPL3103", "非空 " + building.displayName() + " 不需要与 null 比较", span);
            return false;
        }
        if (right instanceof BuildingType building && left == ValueType.NULL) {
            if (building.nullable()) return true;
            error("MPL3103", "非空 " + building.displayName() + " 不需要与 null 比较", span);
            return false;
        }
        if (left instanceof BuildingType leftBuilding && right instanceof BuildingType rightBuilding
            && leftBuilding.buildingType().equals(rightBuilding.buildingType())) {
            return true;
        }
        error("MPL3103", "不能比较 " + display(left) + " 与 " + display(right), span);
        return false;
    }

    private MplType parseType(String name, SourceSpan span) {
        if (name.startsWith("LinkedBuildingSet<") && name.endsWith(">")) {
            String buildingType = name.substring("LinkedBuildingSet<".length(), name.length() - 1);
            if (profile.buildingType(buildingType).isEmpty()) {
                return typeError("当前 target 不支持 Building 类型：" + buildingType, span);
            }
            return new LinkedBuildingSetType(buildingType);
        }
        if (name.startsWith("Set<Unit<") && name.endsWith(">>")) {
            String unitType = name.substring("Set<Unit<".length(), name.length() - 2);
            if (profile.unitType(unitType).filter(TargetProfile.UnitType::logicControllable).isEmpty()) {
                return typeError("当前 target 不支持 Unit 类型：" + unitType, span);
            }
            return new UnitSetType(unitType);
        }
        boolean nullable = name.endsWith("?");
        String nonNullableName = nullable ? name.substring(0, name.length() - 1) : name;
        if (nonNullableName.startsWith("Unit<") && nonNullableName.endsWith(">")) {
            String unitType = nonNullableName.substring("Unit<".length(), nonNullableName.length() - 1);
            if (profile.unitType(unitType).filter(TargetProfile.UnitType::logicControllable).isEmpty()) {
                return typeError("当前 target 不支持 Unit 类型：" + unitType, span);
            }
            return new UnitType(unitType, nullable);
        }
        if (nonNullableName.startsWith("Building<") && nonNullableName.endsWith(">")) {
            String buildingType = nonNullableName.substring("Building<".length(), nonNullableName.length() - 1);
            if (profile.buildingType(buildingType).isEmpty()) {
                return typeError("当前 target 不支持 Building 类型：" + buildingType, span);
            }
            return new BuildingType(buildingType, nullable);
        }
        if (nullable) return typeError("当前阶段只有 Unit<T> 与 Building<T> 对象引用支持可空类型", span);
        if (name.endsWith("[]")) {
            MplType element = parseType(name.substring(0, name.length() - 2), span);
            return collectionType(CollectionType.Kind.ARRAY, element, span);
        }
        if (name.startsWith("List<") && name.endsWith(">")) {
            return collectionType(CollectionType.Kind.LIST, name.substring("List<".length(), name.length() - 1), span);
        }
        if (name.startsWith("Set<") && name.endsWith(">")) {
            return collectionType(CollectionType.Kind.SET, name.substring("Set<".length(), name.length() - 1), span);
        }
        if (name.startsWith("(") && name.endsWith(")")) {
            List<String> members = splitTopLevel(name.substring(1, name.length() - 1));
            if (members.size() < 2) return typeError("元组类型至少需要两个元素", span);
            List<MplType> types = members.stream().map(member -> parseType(member, span)).toList();
            if (types.contains(ValueType.ERROR) || types.contains(ValueType.VOID)) return ValueType.ERROR;
            if (types.stream().anyMatch(this::isAggregate)) {
                return typeError("当前阶段不支持嵌套聚合类型；需要 Memory runtime", span);
            }
            return new TupleType(types);
        }
        return switch (name) {
            case "Int" -> ValueType.INT;
            case "Float" -> ValueType.FLOAT;
            case "Bool" -> ValueType.BOOL;
            case "String" -> ValueType.STRING;
            case "Void" -> ValueType.VOID;
            default -> typeError("当前阶段不支持类型：" + name, span);
        };
    }

    private ValueType typeError(String message, SourceSpan span) {
        error("MPL3103", message, span);
        return ValueType.ERROR;
    }

    private MplType collectionType(CollectionType.Kind kind, String elementName, SourceSpan span) {
        MplType element = parseType(elementName, span);
        return collectionType(kind, element, span);
    }

    private MplType collectionType(CollectionType.Kind kind, MplType element, SourceSpan span) {
        if (element == ValueType.ERROR || element == ValueType.VOID) return ValueType.ERROR;
        if (element == ValueType.NULL || element instanceof UnitType || element instanceof UnitSetType
            || element instanceof BuildingType || element instanceof LinkedBuildingSetType) {
            return typeError("当前阶段聚合类型不能存储可空值、游戏对象引用或对象查询", span);
        }
        if (isAggregate(element)) return typeError("当前阶段不支持嵌套聚合类型；需要 Memory runtime", span);
        return new CollectionType(kind, element);
    }

    private List<String> splitTopLevel(String text) {
        List<String> result = new ArrayList<>();
        int nesting = 0;
        int start = 0;
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character == '<' || character == '(' || character == '[') nesting++;
            if (character == '>' || character == ')' || character == ']') nesting--;
            if (character == ',' && nesting == 0) {
                result.add(text.substring(start, index));
                start = index + 1;
            }
        }
        result.add(text.substring(start));
        return result;
    }

    private HirExpression analyzeArrayLiteral(ArrayLiteral array) {
        if (array.elements().isEmpty()) {
            error("MPL3601", "空数组字面量缺少可推导的元素类型", array.span());
            return new HirConstant("0", ValueType.ERROR);
        }
        List<HirExpression> elements = array.elements().stream().map(this::analyzeExpression).toList();
        MplType elementType = commonElementType(elements.stream().map(HirExpression::type).toList(), array.span());
        if (elementType == ValueType.ERROR) return new HirConstant("0", ValueType.ERROR);
        if (isAggregate(elementType)) {
            error("MPL3601", "当前阶段不支持嵌套聚合值；需要 Memory runtime", array.span());
            return new HirConstant("0", ValueType.ERROR);
        }
        return new HirArrayLiteral(elements, new CollectionType(CollectionType.Kind.ARRAY, elementType));
    }

    private HirExpression analyzeTupleLiteral(TupleLiteral tuple) {
        List<HirExpression> elements = tuple.elements().stream().map(this::analyzeExpression).toList();
        List<MplType> types = elements.stream().map(HirExpression::type).toList();
        if (types.contains(ValueType.ERROR) || types.contains(ValueType.VOID)) return new HirConstant("0", ValueType.ERROR);
        if (types.stream().anyMatch(this::isAggregate)) {
            error("MPL3601", "当前阶段不支持嵌套聚合值；需要 Memory runtime", tuple.span());
            return new HirConstant("0", ValueType.ERROR);
        }
        return new HirTupleLiteral(elements, new TupleType(types));
    }

    private MplType commonElementType(List<MplType> types, SourceSpan span) {
        MplType result = types.get(0);
        for (int index = 1; index < types.size(); index++) {
            MplType candidate = types.get(index);
            if (result.equals(candidate)) continue;
            if ((result == ValueType.INT && candidate == ValueType.FLOAT)
                || (result == ValueType.FLOAT && candidate == ValueType.INT)) {
                result = ValueType.FLOAT;
                continue;
            }
            return typeError("数组元素必须具有可统一推导的类型，不能混用 " + display(result) + " 与 "
                + display(candidate), span);
        }
        return result;
    }

    private Optional<CollectionType.Kind> collectionFactory(Expression callee) {
        if (callee instanceof Identifier identifier) {
            return switch (identifier.name()) {
                case "listOf" -> Optional.of(CollectionType.Kind.LIST);
                case "setOf" -> Optional.of(CollectionType.Kind.SET);
                default -> Optional.empty();
            };
        }
        if (callee instanceof MemberAccessExpression member && member.target() instanceof Identifier namespace
            && "of".equals(member.member())) {
            return switch (namespace.name()) {
                case "List" -> Optional.of(CollectionType.Kind.LIST);
                case "Set" -> Optional.of(CollectionType.Kind.SET);
                default -> Optional.empty();
            };
        }
        return Optional.empty();
    }

    private HirExpression analyzeCollectionFactory(CollectionType.Kind kind, List<Expression> sourceElements, SourceSpan span) {
        if (sourceElements.isEmpty()) {
            error("MPL3601", kind + " 空字面量缺少可推导的元素类型", span);
            return new HirConstant("0", ValueType.ERROR);
        }
        List<HirExpression> elements = sourceElements.stream().map(this::analyzeExpression).toList();
        MplType elementType = commonElementType(elements.stream().map(HirExpression::type).toList(), span);
        if (elementType == ValueType.ERROR) return new HirConstant("0", ValueType.ERROR);
        if (kind == CollectionType.Kind.SET && !hasStaticallyDistinctElements(elements)) {
            error("MPL3601", "第一版 Set 元素必须是互不相同的静态字面量", span);
            return new HirConstant("0", ValueType.ERROR);
        }
        return new HirCollectionLiteral(elements, new CollectionType(kind, elementType));
    }

    private boolean hasStaticallyDistinctElements(List<HirExpression> elements) {
        Set<String> values = new HashSet<>();
        for (HirExpression element : elements) {
            String key;
            if (element instanceof HirConstant constant) key = constant.type().displayName() + ":" + constant.mlogLiteral();
            else if (element instanceof HirText text) key = "String:" + text.value();
            else return false;
            if (!values.add(key)) return false;
        }
        return true;
    }

    private HirExpression analyzeCollectionContains(Expression sourceTarget, Expression sourceCandidate, SourceSpan span) {
        HirExpression target = analyzeExpression(sourceTarget);
        HirExpression candidate = analyzeExpression(sourceCandidate);
        if (!(target.type() instanceof CollectionType collection)) {
            error("MPL3601", "contains(value) 仅支持 Array、List 或 Set", span);
            return new HirConstant("0", ValueType.ERROR);
        }
        if (!collection.elementType().canAssignFrom(candidate.type())) {
            error("MPL3103", "contains 参数必须是 " + collection.elementType().displayName(), sourceCandidate.span());
            return new HirConstant("0", ValueType.ERROR);
        }
        Integer size = aggregateSize(sourceTarget, target.type());
        if (size == null) {
            error("MPL3601", "contains 需要可静态确定长度的聚合值", span);
            return new HirConstant("0", ValueType.ERROR);
        }
        return new HirCollectionContains(target, candidate, size);
    }

    private HirExpression analyzeIndexAccess(IndexExpression access) {
        return analyzeIndexAccess(access.target(), access.index(), access.span());
    }

    private HirExpression analyzeIndexAccess(Expression sourceTarget, Expression sourceIndex, SourceSpan span) {
        HirExpression target = analyzeExpression(sourceTarget);
        HirExpression index = analyzeExpression(sourceIndex);
        if (index.type() != ValueType.INT) {
            error("MPL3601", "聚合下标必须是 Int", sourceIndex.span());
            return new HirConstant("0", ValueType.ERROR);
        }
        if (!(target.type() instanceof TupleType) && !(target.type() instanceof CollectionType)) {
            error("MPL3601", "下标访问仅支持元组、数组、List 或 Set", sourceTarget.span());
            return new HirConstant("0", ValueType.ERROR);
        }
        if (sourceIndex instanceof IntegerLiteral) {
            Integer size = aggregateSize(sourceTarget, target.type());
            Optional<Integer> staticIndex = staticAggregateIndex(sourceIndex, size);
            if (staticIndex.isEmpty()) return new HirConstant("0", ValueType.ERROR);
            MplType elementType = target.type() instanceof TupleType tuple
                ? tuple.elementTypes().get(staticIndex.orElseThrow())
                : ((CollectionType) target.type()).elementType();
            return new HirIndexAccess(target, index, elementType);
        }
        if (!(target.type() instanceof CollectionType collection)
            || collection.kind() != CollectionType.Kind.ARRAY
            || !(sourceTarget instanceof Identifier array)
            || !(target instanceof HirVariable)) {
            error("MPL3601", "动态下标当前只支持具名 Array", span);
            return new HirConstant("0", ValueType.ERROR);
        }
        MplType elementType = collection.elementType();
        if (!supportsDynamicArrayElement(elementType)) {
            error("MPL3601", "动态 Array 下标当前只支持 Int、Float 或 Bool 元素", span);
            return new HirConstant("0", ValueType.ERROR);
        }
        if (!hasArrayBoundsProof(array.name(), sourceIndex)) {
            error("MPL3601", "无法证明动态 Array 下标在范围内；请使用从 0 到 array.size 的标准计数 for 循环", sourceIndex.span());
            return new HirConstant("0", ValueType.ERROR);
        }
        return new HirDynamicIndexAccess(target, index, elementType);
    }

    private boolean supportsDynamicArrayElement(MplType type) {
        return type == ValueType.INT || type == ValueType.FLOAT || type == ValueType.BOOL;
    }

    private boolean hasArrayBoundsProof(String array, Expression sourceIndex) {
        if (!(sourceIndex instanceof Identifier index)) return false;
        return arrayBoundsProofs.stream().anyMatch(proof -> proof.array().equals(array) && proof.index().equals(index.name()));
    }

    private boolean isAggregate(MplType type) {
        return type instanceof TupleType || type instanceof CollectionType;
    }

    private MplType aggregateIterationElementType(MplType type, SourceSpan span) {
        if (type instanceof CollectionType collection) return collection.elementType();
        if (type instanceof TupleType tuple) {
            MplType first = tuple.elementTypes().get(0);
            if (tuple.elementTypes().stream().allMatch(first::equals)) return first;
            return typeError("只有元素类型一致的元组可以直接 for 遍历", span);
        }
        return typeError("该类型不可遍历：" + display(type), span);
    }

    private Integer aggregateSize(Expression source, MplType type) {
        if (type instanceof TupleType tuple) return tuple.elementTypes().size();
        if (source instanceof Identifier identifier) {
            Symbol symbol = lookup(identifier.name());
            return symbol == null ? null : symbol.staticAggregateSize();
        }
        if (source instanceof ArrayLiteral array) return array.elements().size();
        if (source instanceof TupleLiteral tuple) return tuple.elements().size();
        return null;
    }

    private Optional<Integer> staticAggregateIndex(Expression source, Integer size) {
        if (!(source instanceof IntegerLiteral literal)) {
            error("MPL3601", "当前阶段只支持可在编译期确定的聚合下标；动态下标需要 Memory runtime", source.span());
            return Optional.empty();
        }
        if (literal.value() < 0 || literal.value() > Integer.MAX_VALUE) {
            error("MPL3601", "聚合下标超出 Int 范围", source.span());
            return Optional.empty();
        }
        int index = (int) literal.value();
        if (size == null || index >= size) {
            error("MPL3601", "聚合下标越界：" + index, source.span());
            return Optional.empty();
        }
        return Optional.of(index);
    }

    private boolean declare(String name, Symbol symbol, SourceSpan span) {
        Map<String, Symbol> current = scopes.peek();
        if (hardwareLinks.containsKey(name) || functions.containsKey(name) || current.containsKey(name) || lookup(name) != null) {
            error("MPL3101", "变量已声明：" + name, span);
            return false;
        }
        current.put(name, symbol);
        return true;
    }

    private void recordGlobalAccess(String name, Symbol symbol, SourceSpan accessSpan) {
        if (currentFunction == null || !symbol.global()) return;
        directGlobalDependencies.get(currentFunction).add(name);
        SourceSpan functionSpan = functions.get(currentFunction).declaration().span();
        if (!startsBefore(symbol.declarationSpan(), functionSpan)) {
            error("MPL3506", "函数 " + currentFunction + " 不能访问在其声明后定义的顶层变量：" + name,
                accessSpan);
        }
    }

    private boolean startsBefore(SourceSpan left, SourceSpan right) {
        return left.startLine() < right.startLine()
            || left.startLine() == right.startLine() && left.startColumn() < right.startColumn();
    }

    private void validateTopLevelCalls() {
        Map<String, Set<String>> dependencies = new HashMap<>();
        for (String function : functions.keySet()) {
            dependencies.put(function, new HashSet<>(directGlobalDependencies.getOrDefault(function, Set.of())));
        }

        boolean changed;
        do {
            changed = false;
            for (String function : functions.keySet()) {
                Set<String> reachable = dependencies.get(function);
                for (String callee : callGraph.getOrDefault(function, Set.of())) {
                    changed |= reachable.addAll(dependencies.getOrDefault(callee, Set.of()));
                }
            }
        } while (changed);

        for (TopLevelCall call : topLevelCalls) {
            List<String> missing = dependencies.getOrDefault(call.function(), Set.of()).stream()
                .filter(name -> !call.initializedGlobals().contains(name))
                .sorted()
                .toList();
            if (!missing.isEmpty()) {
                error("MPL3507", "调用函数 " + call.function() + " 前尚未初始化顶层变量："
                    + String.join("、", missing), call.span());
            }
        }
    }

    private Symbol lookup(String name) {
        for (Map<String, Symbol> scope : scopes) {
            Symbol symbol = scope.get(name);
            if (symbol != null) return symbol;
        }
        return null;
    }

    private void error(String code, String message, SourceSpan span) {
        diagnostics.add(new Diagnostic(Severity.ERROR, code, message, Optional.ofNullable(file), Optional.of(span)));
    }

    private String display(MplType type) {
        return type.displayName();
    }

    /** Static bound is known only for immutable literal-backed strings in the first String slice. */
    private int staticStringLength(HirExpression expression) {
        if (expression instanceof HirText text) return text.value().length();
        if (expression instanceof HirBinary binary && binary.type() == ValueType.STRING && "+".equals(binary.operator())) {
            return staticStringLength(binary.left()) + staticStringLength(binary.right());
        }
        if (expression instanceof HirVariable variable && variable.type() == ValueType.STRING) {
            Symbol symbol = lookup(variable.name());
            return symbol == null || symbol.staticStringCodeUnits() == null ? 0 : symbol.staticStringCodeUnits();
        }
        return 0;
    }

    private Integer staticAggregateSize(HirExpression expression) {
        if (expression instanceof HirArrayLiteral array) return array.elements().size();
        if (expression instanceof HirTupleLiteral tuple) return tuple.elements().size();
        if (expression instanceof HirCollectionLiteral collection) return collection.elements().size();
        return null;
    }

    private record Symbol(MplType type, boolean mutable, Integer staticStringCodeUnits, Integer staticAggregateSize,
                          HirUnitQuery unitQuery, HirBuildingQuery buildingQuery, boolean global,
                          SourceSpan declarationSpan) {
        private Symbol withType(MplType narrowedType) {
            return new Symbol(narrowedType, mutable, staticStringCodeUnits, staticAggregateSize, unitQuery, buildingQuery,
                global, declarationSpan);
        }
    }

    private record FunctionSignature(FunctionDeclaration declaration, List<MplType> parameterTypes,
                                     MplType returnType) {
        private FunctionSignature {
            parameterTypes = List.copyOf(parameterTypes);
        }
    }

    private record TopLevelCall(String function, Set<String> initializedGlobals, SourceSpan span) {
        private TopLevelCall {
            initializedGlobals = Set.copyOf(initializedGlobals);
        }
    }

    private record ArrayBoundsProof(String index, String array) {
    }

    private record UnitQuery(String typeName, TargetProfile.UnitType type, List<Expression> filters, int managedLimit,
                             int managedId) {
    }

    private record BuildingQuery(String typeName, List<Expression> filters) {
        private BuildingQuery {
            filters = List.copyOf(filters);
        }
    }
}
