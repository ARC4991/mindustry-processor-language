package com.arc.mpl.semantic;

import com.arc.mpl.ast.AssignmentExpression;
import com.arc.mpl.ast.BinaryExpression;
import com.arc.mpl.ast.BlockStatement;
import com.arc.mpl.ast.BooleanLiteral;
import com.arc.mpl.ast.CallExpression;
import com.arc.mpl.ast.Expression;
import com.arc.mpl.ast.ExpressionStatement;
import com.arc.mpl.ast.FloatLiteral;
import com.arc.mpl.ast.ForEachStatement;
import com.arc.mpl.ast.Identifier;
import com.arc.mpl.ast.IntegerLiteral;
import com.arc.mpl.ast.LambdaExpression;
import com.arc.mpl.ast.MemberAccessExpression;
import com.arc.mpl.ast.MethodCallExpression;
import com.arc.mpl.ast.Program;
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
import com.arc.mpl.hir.HirConstant;
import com.arc.mpl.hir.HirExpression;
import com.arc.mpl.hir.HirExpressionStatement;
import com.arc.mpl.hir.HirIntrinsicCall;
import com.arc.mpl.hir.HirMemberAccess;
import com.arc.mpl.hir.HirPrintStatement;
import com.arc.mpl.hir.HirProgram;
import com.arc.mpl.hir.HirStatement;
import com.arc.mpl.hir.HirText;
import com.arc.mpl.hir.HirUnary;
import com.arc.mpl.hir.HirUnitControl;
import com.arc.mpl.hir.HirUnitIteration;
import com.arc.mpl.hir.HirVariable;
import com.arc.mpl.hir.HirVariableDeclaration;
import com.arc.mpl.hir.HirWhile;
import com.arc.mpl.hir.ValueType;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Name resolution and strict type checks for the currently implemented MPL subset. */
public final class SemanticAnalyzer {
    /**
     * This intentionally small table is the v146-compatible front-end seed.
     * It belongs in a generated profile description once profile content data is loaded.
     */
    private static final Map<String, UnitType> UNIT_TYPES = Map.of(
        "Alpha", new UnitType("alpha"),
        "Dagger", new UnitType("dagger")
    );

    /** Read-only Unit fields that the first UnitSet vertical slice permits in MPL. */
    private static final Map<String, ValueType> UNIT_PROPERTIES = Map.ofEntries(
        Map.entry("totalItems", ValueType.INT),
        Map.entry("itemCapacity", ValueType.INT),
        Map.entry("rotation", ValueType.FLOAT),
        Map.entry("health", ValueType.FLOAT),
        Map.entry("shield", ValueType.FLOAT),
        Map.entry("maxHealth", ValueType.FLOAT),
        Map.entry("ammo", ValueType.FLOAT),
        Map.entry("ammoCapacity", ValueType.FLOAT),
        Map.entry("x", ValueType.FLOAT),
        Map.entry("y", ValueType.FLOAT),
        Map.entry("dead", ValueType.BOOL),
        Map.entry("alive", ValueType.BOOL),
        Map.entry("shooting", ValueType.BOOL),
        Map.entry("boosting", ValueType.BOOL),
        Map.entry("range", ValueType.FLOAT),
        Map.entry("shootX", ValueType.FLOAT),
        Map.entry("shootY", ValueType.FLOAT),
        Map.entry("mining", ValueType.BOOL),
        Map.entry("mineX", ValueType.INT),
        Map.entry("mineY", ValueType.INT),
        Map.entry("speed", ValueType.FLOAT),
        Map.entry("payloadCount", ValueType.INT),
        Map.entry("size", ValueType.FLOAT)
    );

    private final Deque<Map<String, Symbol>> scopes = new ArrayDeque<>();
    private final List<Diagnostic> diagnostics = new ArrayList<>();
    private Path file;
    private Map<String, String> messages = Map.of();
    private int unitIterationDepth;
    private String activeUnitBinding;

    public SemanticResult analyze(Program program, Path sourceFile) {
        return analyze(program, sourceFile, Map.of());
    }

    public SemanticResult analyze(Program program, Path sourceFile, Map<String, String> messages) {
        scopes.clear();
        scopes.push(new HashMap<>());
        diagnostics.clear();
        file = sourceFile;
        this.messages = Map.copyOf(messages);
        unitIterationDepth = 0;
        activeUnitBinding = null;

        List<HirStatement> statements = new ArrayList<>();
        for (Statement statement : program.statements()) {
            statements.add(analyzeStatement(statement));
        }
        return new SemanticResult(diagnostics.isEmpty() ? Optional.of(new HirProgram(statements)) : Optional.empty(), diagnostics);
    }

    private HirStatement analyzeStatement(Statement statement) {
        if (statement instanceof VariableDeclaration declaration) {
            return analyzeDeclaration(declaration);
        }
        if (statement instanceof WhileStatement loop) {
            return analyzeWhile(loop);
        }
        if (statement instanceof ForEachStatement loop) {
            return analyzeForEach(loop);
        }
        if (statement instanceof BlockStatement block) {
            return new HirBlock(analyzeBlock(block));
        }
        ExpressionStatement expressionStatement = (ExpressionStatement) statement;
        return analyzeExpressionStatement(expressionStatement.expression());
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
        return new HirWhile(condition, analyzeBlock(loop.body()));
    }

    private HirStatement analyzeForEach(ForEachStatement loop) {
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
        scopes.push(new HashMap<>());
        try {
            declare(loop.name(), new Symbol(ValueType.UNIT, false), loop.span());
            List<HirExpression> filters = new ArrayList<>();
            for (Expression filter : query.orElseThrow().filters()) {
                filters.add(analyzeUnitFilter(filter, loop.name()));
            }
            List<HirStatement> body = analyzeBlock(loop.body());
            UnitType type = query.orElseThrow().type();
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
        UnitType type = UNIT_TYPES.get(typeName);
        if (type == null) {
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

        return Optional.of(new UnitQuery(typeName, type, List.copyOf(filters), managedLimit));
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
            scopes.peek().put(parameter, new Symbol(ValueType.UNIT, false));
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
        return new HirExpressionStatement(analyzeExpression(expression));
    }

    private HirStatement analyzeStatementCall(CallExpression call) {
        if (!(call.callee() instanceof MemberAccessExpression member)
            || !(member.target() instanceof Identifier target)) {
            return null;
        }
        String linkName = messages.get(target.name());
        if (linkName != null && "print".equals(member.member())) {
            return analyzePrintCall(linkName, call.arguments());
        }

        Symbol targetSymbol = lookup(target.name());
        if (targetSymbol != null && targetSymbol.type() == ValueType.UNIT) {
            return analyzeUnitControl(target.name(), member.member(), call.arguments(), call.span());
        }
        return null;
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
            if (value.type() == ValueType.ERROR && !(value instanceof HirText)) {
                error("MPL3202", "print 参数必须是数值、Bool 或字符串字面量", argument.span());
            }
            arguments.add(value);
        }
        return new HirPrintStatement(linkName, arguments);
    }

    private HirStatement analyzeUnitControl(String sourceBinding, String command, List<Expression> sourceArguments, SourceSpan span) {
        if (!"move".equals(command)) {
            error("MPL3305", "当前阶段 Unit 仅支持 move(x, y)", span);
            return new HirExpressionStatement(new HirConstant("0", ValueType.ERROR));
        }
        if (sourceArguments.size() != 2) {
            error("MPL3305", "Unit.move(x, y) 需要两个数值参数", span);
        }
        List<HirExpression> arguments = new ArrayList<>();
        for (Expression argument : sourceArguments) {
            HirExpression value = analyzeExpression(argument);
            requireNumeric(value.type(), argument.span(), "Unit.move 参数");
            arguments.add(value);
        }
        return new HirUnitControl(activeUnitBinding == null ? sourceBinding : activeUnitBinding, command, arguments);
    }

    private HirStatement analyzeDeclaration(VariableDeclaration declaration) {
        HirExpression initializer = analyzeExpression(declaration.initializer());
        ValueType type = declaration.declaredType().map(value -> parseType(value, declaration.span())).orElse(initializer.type());
        if (!type.canAssignFrom(initializer.type())) {
            error("MPL3103", "不能将 " + display(initializer.type()) + " 赋给 " + display(type), declaration.initializer().span());
        }
        declare(declaration.name(), new Symbol(type, declaration.mutable()), declaration.span());
        return new HirVariableDeclaration(declaration.name(), type, initializer);
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
            Symbol symbol = lookup(identifier.name());
            if (symbol == null) {
                error("MPL3102", "未声明的变量：" + identifier.name(), identifier.span());
                return new HirVariable(identifier.name(), ValueType.ERROR);
            }
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
        HirExpression target = analyzeExpression(member.target());
        if (target.type() == ValueType.UNIT) {
            if ("flag".equals(member.member())) {
                error("MPL3304", "Unit.flag 是编译器私有运行时属性，MPL 不允许访问", member.span());
                return new HirMemberAccess(target, member.member(), ValueType.ERROR);
            }
            ValueType type = UNIT_PROPERTIES.get(member.member());
            if (type == null) {
                error("MPL3304", "当前 target 的 Unit 不支持只读属性：" + member.member(), member.span());
                return new HirMemberAccess(target, member.member(), ValueType.ERROR);
            }
            return new HirMemberAccess(target, member.member(), type);
        }
        error("MPL3201", "当前阶段不支持该成员访问", member.span());
        return new HirConstant("0", ValueType.ERROR);
    }

    private HirExpression analyzeCallExpression(CallExpression call) {
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
            case "+", "-", "*" -> numericResult(left, right, span, "运算符 " + operator);
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
            default -> typeError("当前阶段不支持类型：" + name, span);
        };
    }

    private ValueType typeError(String message, SourceSpan span) {
        error("MPL3103", message, span);
        return ValueType.ERROR;
    }

    private void declare(String name, Symbol symbol, SourceSpan span) {
        Map<String, Symbol> current = scopes.peek();
        if (current.containsKey(name) || lookup(name) != null) {
            error("MPL3101", "变量已声明：" + name, span);
            return;
        }
        current.put(name, symbol);
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
            case UNIT -> "Unit";
            case ERROR -> "错误类型";
        };
    }

    private record Symbol(ValueType type, boolean mutable) {
    }

    private record UnitType(String mlogName) {
    }

    private record UnitQuery(String typeName, UnitType type, List<Expression> filters, int managedLimit) {
    }
}
