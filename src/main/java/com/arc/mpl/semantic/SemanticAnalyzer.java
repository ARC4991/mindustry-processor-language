package com.arc.mpl.semantic;

import com.arc.mpl.ast.AssignmentExpression;
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
import com.arc.mpl.ast.IfStatement;
import com.arc.mpl.ast.IntegerLiteral;
import com.arc.mpl.ast.LambdaExpression;
import com.arc.mpl.ast.MemberAccessExpression;
import com.arc.mpl.ast.MethodCallExpression;
import com.arc.mpl.ast.Program;
import com.arc.mpl.ast.ReturnStatement;
import com.arc.mpl.ast.Statement;
import com.arc.mpl.ast.StringLiteral;
import com.arc.mpl.ast.UnaryExpression;
import com.arc.mpl.ast.VariableDeclaration;
import com.arc.mpl.ast.WhileStatement;
import com.arc.mpl.diagnostic.Diagnostic;
import com.arc.mpl.diagnostic.Diagnostic.SourceSpan;
import com.arc.mpl.diagnostic.Severity;
import com.arc.mpl.hir.HirAssignment;
import com.arc.mpl.hir.HirBinary;
import com.arc.mpl.hir.HirBlock;
import com.arc.mpl.hir.HirBuildingControl;
import com.arc.mpl.hir.HirBreak;
import com.arc.mpl.hir.HirConstant;
import com.arc.mpl.hir.HirContinue;
import com.arc.mpl.hir.HirDoWhile;
import com.arc.mpl.hir.HirExpression;
import com.arc.mpl.hir.HirExpressionStatement;
import com.arc.mpl.hir.HirFor;
import com.arc.mpl.hir.HirFunction;
import com.arc.mpl.hir.HirFunctionCall;
import com.arc.mpl.hir.HirFunctionParameter;
import com.arc.mpl.hir.HirHardwareLink;
import com.arc.mpl.hir.HirIntrinsicCall;
import com.arc.mpl.hir.HirIf;
import com.arc.mpl.hir.HirMemberAccess;
import com.arc.mpl.hir.HirPrintStatement;
import com.arc.mpl.hir.HirProgram;
import com.arc.mpl.hir.HirReturn;
import com.arc.mpl.hir.HirStatement;
import com.arc.mpl.hir.HirText;
import com.arc.mpl.hir.HirUnary;
import com.arc.mpl.hir.HirUnitControl;
import com.arc.mpl.hir.HirUnitIteration;
import com.arc.mpl.hir.HirVariable;
import com.arc.mpl.hir.HirVariableDeclaration;
import com.arc.mpl.hir.HirWhile;
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
    private Path file;
    private Map<String, String> messages = Map.of();
    private Map<String, HardwareContract.LinkDeclaration> hardwareLinks = Map.of();
    private int unitIterationDepth;
    private int loopDepth;
    private String activeUnitBinding;
    private String currentFunction;
    private ValueType currentReturnType;
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
        Map<String, HardwareContract.LinkDeclaration> links = new HashMap<>();
        for (HardwareContract.LinkDeclaration link : hardware.links()) {
            if (links.put(link.mplName(), link) != null) {
                throw new IllegalArgumentException("重复的硬件常量：" + link.mplName());
            }
        }
        hardwareLinks = Map.copyOf(links);
        functions.clear();
        callGraph.clear();
        directGlobalDependencies.clear();
        topLevelCalls.clear();
        initializedGlobals.clear();
        unitIterationDepth = 0;
        loopDepth = 0;
        activeUnitBinding = null;
        currentFunction = null;
        currentReturnType = null;
        analyzingTopLevel = true;

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
        List<ValueType> parameters = function.parameters().stream()
            .map(parameter -> parseType(parameter.typeName(), parameter.span())).toList();
        if (parameters.contains(ValueType.VOID)) {
            error("MPL3503", "函数参数不能使用 Void 类型", function.span());
        }
        ValueType returnType = function.returnType().map(value -> parseType(value, function.span())).orElse(ValueType.VOID);
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
        ValueType previousReturnType = currentReturnType;
        currentFunction = function.name();
        currentReturnType = signature.returnType();
        scopes.push(new HashMap<>());
        try {
            List<HirFunctionParameter> parameters = new ArrayList<>();
            for (int index = 0; index < function.parameters().size(); index++) {
                FunctionParameter parameter = function.parameters().get(index);
                ValueType type = signature.parameterTypes().get(index);
                declare(parameter.name(), new Symbol(type, false, null, false, parameter.span()), parameter.span());
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
        List<HirStatement> consequence = analyzeBlock(branch.thenBlock());
        Optional<List<HirStatement>> alternative = branch.elseBranch().map(this::analyzeAlternative);
        return new HirIf(condition, consequence, alternative);
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
            HirExpression condition = loop.condition().map(this::analyzeExpression)
                .orElseGet(() -> new HirConstant("1", ValueType.BOOL));
            loop.condition().ifPresent(value -> requireBool(condition.type(), value.span(), "for 条件"));
            Optional<HirExpression> update = loop.update().map(this::analyzeExpression);
            List<HirStatement> body = analyzeLoopBlock(loop.body());
            return new HirFor(declarationInitializer, expressionInitializer, condition, update, body);
        } finally {
            scopes.pop();
        }
    }

    private HirStatement analyzeForEach(ForEachStatement loop) {
        if (currentFunction != null) {
            error("MPL3508", "第一版函数不支持 UnitSet 遍历", loop.span());
        }
        Optional<UnitQuery> query = parseUnitQuery(loop.iterable());
        if (query.isEmpty()) {
            error("MPL3301", "当前阶段 for 只支持 Unit.getAll类型() 查询", loop.iterable().span());
            return new HirBlock(analyzeBlock(loop.body()));
        }
        if (unitIterationDepth > 0) {
            error("MPL3306", "第一版不支持嵌套 Unit 遍历", loop.span());
        }

        String previousBinding = activeUnitBinding;
        activeUnitBinding = loop.name();
        unitIterationDepth++;
        loopDepth++;
        scopes.push(new HashMap<>());
        try {
            declare(loop.name(), new Symbol(ValueType.UNIT, false, null, false, loop.span()), loop.span());
            List<HirExpression> filters = new ArrayList<>();
            for (Expression filter : query.orElseThrow().filters()) {
                filters.add(analyzeUnitFilter(filter, loop.name()));
            }
            List<HirStatement> body = analyzeBlock(loop.body());
            TargetProfile.UnitType type = query.orElseThrow().type();
            return new HirUnitIteration(
                loop.name(),
                query.orElseThrow().typeName(),
                type.mlogName(),
                filters,
                query.orElseThrow().managedLimit(),
                body);
        } finally {
            scopes.pop();
            unitIterationDepth--;
            loopDepth--;
            activeUnitBinding = previousBinding;
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
                    error("MPL3307", "UnitSet.take(n) 必须放在所有 .where(...) 之后", modifier.span());
                    return Optional.empty();
                }
                if (modifier.arguments().size() != 1) {
                    error("MPL3302", "UnitSet.where(...) 需要恰好一个过滤 lambda", modifier.span());
                    return Optional.empty();
                }
                filters.add(modifier.arguments().get(0));
                continue;
            }

            if (managedLimit != 0) {
                error("MPL3307", "一个 UnitSet 查询只能调用一次 .take(n)", modifier.span());
                return Optional.empty();
            }
            if (modifier.arguments().size() != 1 || !(modifier.arguments().get(0) instanceof IntegerLiteral literal)) {
                error("MPL3307", "UnitSet.take(n) 只接受正 Int 字面量", modifier.span());
                return Optional.empty();
            }
            if (literal.value() <= 0 || literal.value() > Integer.MAX_VALUE) {
                error("MPL3307", "UnitSet.take(n) 的 n 必须位于 1 到 2147483647", literal.span());
                return Optional.empty();
            }
            managedLimit = (int) literal.value();
        }

        return Optional.of(new UnitQuery(typeName, type.orElseThrow(), List.copyOf(filters), managedLimit));
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
            scopes.peek().put(parameter, new Symbol(ValueType.UNIT, false, null, false, source.span()));
            HirExpression result = analyzeExpression(predicate);
            if (result.type() != ValueType.BOOL) {
                error("MPL3303", "UnitSet.where(...) 的过滤条件必须是 Bool", predicate.span());
            }
            if (!isPureUnitFilter(result, bindingName)) {
                error("MPL3303", "UnitSet.where(...) 只能读取当前单位属性与 val 标量", predicate.span());
            }
            return result;
        } finally {
            scopes.pop();
        }
    }

    private boolean isPureUnitFilter(HirExpression expression, String bindingName) {
        if (expression instanceof HirConstant || expression instanceof HirText) return true;
        if (expression instanceof HirVariable variable) {
            if (variable.type() == ValueType.UNIT) return bindingName.equals(variable.name());
            Symbol symbol = lookup(variable.name());
            return symbol != null && !symbol.mutable();
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
        return new HirExpressionStatement(value);
    }

    private HirStatement analyzeStatementCall(CallExpression call) {
        if (!(call.callee() instanceof MemberAccessExpression member)
            || !(member.target() instanceof Identifier target)) {
            return null;
        }
        HardwareContract.LinkDeclaration hardware = hardwareLinks.get(target.name());
        if (hardware != null) {
            if ("Message".equals(hardware.mplType()) && "print".equals(member.member())) {
                return analyzePrintCall(hardware.gameAlias(), call.arguments());
            }
            return analyzeBuildingControl(hardware, member.member(), call.arguments(), call.span());
        }

        Symbol targetSymbol = lookup(target.name());
        if (targetSymbol != null && targetSymbol.type() == ValueType.UNIT) {
            return analyzeUnitControl(target.name(), member.member(), call.arguments(), call.span());
        }
        return null;
    }

    private HirStatement analyzeBuildingControl(HardwareContract.LinkDeclaration link, String method,
                                                List<Expression> sourceArguments, SourceSpan span) {
        TargetProfile.BuildingType building = profile.buildingType(link.mplType()).orElse(null);
        TargetProfile.BuildingAction action = building == null ? null : building.actions().get(method);
        if (action == null) {
            error("MPL3201", "硬件 " + link.mplName() + "（" + link.mplType() + "）不支持控制方法：" + method, span);
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
        return new HirBuildingControl(new HirHardwareLink(link.mplName(), link.gameAlias(), link.mplType()), control, arguments);
    }

    private HirStatement analyzeLegacyMethodCall(MethodCallExpression call) {
        String linkName = messages.get(call.target());
        if (linkName != null && "print".equals(call.method())) {
            return analyzePrintCall(linkName, call.arguments());
        }
        error("MPL3201", "当前阶段不支持该成员调用", call.span());
        return new HirExpressionStatement(new HirConstant("0", ValueType.ERROR));
    }

    private HirStatement analyzePrintCall(String linkName, List<Expression> sourceArguments) {
        List<HirExpression> arguments = new ArrayList<>();
        for (Expression argument : sourceArguments) {
            HirExpression value = analyzeExpression(argument);
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

    private HirStatement analyzeUnitControl(String sourceBinding, String command, List<Expression> sourceArguments, SourceSpan span) {
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
        return new HirUnitControl(activeUnitBinding == null ? sourceBinding : activeUnitBinding, command, arguments);
    }

    private HirStatement analyzeDeclaration(VariableDeclaration declaration) {
        HirExpression initializer = analyzeExpression(declaration.initializer());
        if (initializer.type() == ValueType.BUILDING) {
            error("MPL3201", "硬件常量不能赋给普通变量；请直接读取字段或调用控制方法", declaration.initializer().span());
        }
        ValueType type = declaration.declaredType().map(value -> parseType(value, declaration.span())).orElse(initializer.type());
        if (type == ValueType.VOID) error("MPL3103", "变量不能使用 Void 类型", declaration.span());
        if (!type.canAssignFrom(initializer.type())) {
            error("MPL3103", "不能将 " + display(initializer.type()) + " 赋给 " + display(type), declaration.initializer().span());
        }
        if (type == ValueType.STRING && declaration.mutable()) {
            error("MPL3103", "当前阶段 String 仅支持 val 静态值；动态 String runtime 尚未启用", declaration.span());
        }
        boolean global = currentFunction == null && scopes.size() == 1;
        if (declare(declaration.name(),
            new Symbol(type, declaration.mutable(), staticStringLength(initializer), global, declaration.span()),
            declaration.span()) && global) {
            // The initializer has already been analyzed, so calls inside it
            // deliberately do not observe this variable as initialized.
            initializedGlobals.add(declaration.name());
        }
        return new HirVariableDeclaration(declaration.name(), type, declaration.mutable(), initializer);
    }

    private HirExpression analyzeExpression(Expression expression) {
        if (expression instanceof IntegerLiteral integer) {
            return new HirConstant(Long.toString(integer.value()), ValueType.INT);
        }
        if (expression instanceof FloatLiteral decimal) {
            return new HirConstant(Double.toString(decimal.value()), ValueType.FLOAT);
        }
        if (expression instanceof StringLiteral text) return new HirText(text.value());
        if (expression instanceof BooleanLiteral bool) {
            return new HirConstant(bool.value() ? "1" : "0", ValueType.BOOL);
        }
        if (expression instanceof Identifier identifier) {
            HardwareContract.LinkDeclaration hardware = hardwareLinks.get(identifier.name());
            if (hardware != null) return new HirHardwareLink(hardware.mplName(), hardware.gameAlias(), hardware.mplType());
            Symbol symbol = lookup(identifier.name());
            if (symbol == null) {
                error("MPL3102", "未声明的变量：" + identifier.name(), identifier.span());
                return new HirVariable(identifier.name(), ValueType.ERROR);
            }
            recordGlobalAccess(identifier.name(), symbol, identifier.span());
            String name = symbol.type() == ValueType.UNIT && activeUnitBinding != null
                ? activeUnitBinding
                : identifier.name();
            return new HirVariable(name, symbol.type());
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
        if ("=".equals(assignment.operator())) {
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
        HirExpression target = analyzeExpression(member.target());
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
        error("MPL3201", "当前阶段不支持该成员访问", member.span());
        return new HirConstant("0", ValueType.ERROR);
    }

    private HirExpression analyzeCallExpression(CallExpression call) {
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

    private ValueType binaryType(String operator, ValueType left, ValueType right, SourceSpan span) {
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

    private ValueType numericResult(ValueType left, ValueType right, SourceSpan span, String context) {
        requireNumeric(left, span, context);
        requireNumeric(right, span, context);
        if (left == ValueType.ERROR || right == ValueType.ERROR) return ValueType.ERROR;
        return left == ValueType.FLOAT || right == ValueType.FLOAT ? ValueType.FLOAT : ValueType.INT;
    }

    private ValueType requireNumeric(ValueType type, SourceSpan span, String context) {
        return type == ValueType.INT || type == ValueType.FLOAT || type == ValueType.ERROR ? type
            : typeError(context + " 只接受 Int 或 Float", span);
    }

    private ValueType requireBool(ValueType type, SourceSpan span, String context) {
        return type == ValueType.BOOL || type == ValueType.ERROR ? type
            : typeError(context + " 只接受 Bool", span);
    }

    private boolean compatibleForEquality(ValueType left, ValueType right, SourceSpan span) {
        if (left == ValueType.ERROR || right == ValueType.ERROR || left == right
            || left == ValueType.INT && right == ValueType.FLOAT || left == ValueType.FLOAT && right == ValueType.INT) {
            return true;
        }
        error("MPL3103", "不能比较 " + display(left) + " 与 " + display(right), span);
        return false;
    }

    private ValueType parseType(String name, SourceSpan span) {
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

    private String display(ValueType type) {
        return switch (type) {
            case INT -> "Int";
            case FLOAT -> "Float";
            case BOOL -> "Bool";
            case STRING -> "String";
            case UNIT -> "Unit";
            case BUILDING -> "Building";
            case VOID -> "Void";
            case ERROR -> "错误类型";
        };
    }

    /** Static bound is known only for immutable literal-backed strings in the first String slice. */
    private int staticStringLength(HirExpression expression) {
        if (expression instanceof HirText text) return text.value().length();
        if (expression instanceof HirVariable variable && variable.type() == ValueType.STRING) {
            Symbol symbol = lookup(variable.name());
            return symbol == null || symbol.staticStringCodeUnits() == null ? 0 : symbol.staticStringCodeUnits();
        }
        return 0;
    }

    private record Symbol(ValueType type, boolean mutable, Integer staticStringCodeUnits, boolean global,
                          SourceSpan declarationSpan) {
    }

    private record FunctionSignature(FunctionDeclaration declaration, List<ValueType> parameterTypes,
                                     ValueType returnType) {
        private FunctionSignature {
            parameterTypes = List.copyOf(parameterTypes);
        }
    }

    private record TopLevelCall(String function, Set<String> initializedGlobals, SourceSpan span) {
        private TopLevelCall {
            initializedGlobals = Set.copyOf(initializedGlobals);
        }
    }

    private record UnitQuery(String typeName, TargetProfile.UnitType type, List<Expression> filters, int managedLimit) {
    }
}
