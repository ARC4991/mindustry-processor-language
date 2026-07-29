package com.arc.mpl.mil.semantic;

import com.arc.mpl.ast.ArrayLiteral;
import com.arc.mpl.ast.AssignmentExpression;
import com.arc.mpl.ast.BinaryExpression;
import com.arc.mpl.ast.BlockStatement;
import com.arc.mpl.ast.BreakStatement;
import com.arc.mpl.ast.CallExpression;
import com.arc.mpl.ast.ContinueStatement;
import com.arc.mpl.ast.DoWhileStatement;
import com.arc.mpl.ast.Expression;
import com.arc.mpl.ast.ExpressionStatement;
import com.arc.mpl.ast.ForEachStatement;
import com.arc.mpl.ast.ForStatement;
import com.arc.mpl.ast.FunctionDeclaration;
import com.arc.mpl.ast.Identifier;
import com.arc.mpl.ast.IfStatement;
import com.arc.mpl.ast.IndexExpression;
import com.arc.mpl.ast.LambdaExpression;
import com.arc.mpl.ast.MemberAccessExpression;
import com.arc.mpl.ast.MethodCallExpression;
import com.arc.mpl.ast.MilDrawStatement;
import com.arc.mpl.ast.MilGameSymbolExpression;
import com.arc.mpl.ast.MilMacroBlockStatement;
import com.arc.mpl.ast.MilMacroCallExpression;
import com.arc.mpl.ast.Program;
import com.arc.mpl.ast.ReturnStatement;
import com.arc.mpl.ast.Statement;
import com.arc.mpl.ast.TupleLiteral;
import com.arc.mpl.ast.UnaryExpression;
import com.arc.mpl.ast.VariableDeclaration;
import com.arc.mpl.ast.WhileStatement;
import com.arc.mpl.diagnostic.Diagnostic;
import com.arc.mpl.diagnostic.Diagnostic.SourceSpan;
import com.arc.mpl.diagnostic.Severity;
import com.arc.mpl.mil.syntax.MilDocument;
import com.arc.mpl.profile.TargetProfile;
import com.arc.mpl.project.HardwareContract;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Expands public MIL macros into the same structured AST consumed by MPL semantic analysis.
 * Target-private runtime details remain in HIR/code generation rather than becoming source text.
 */
public final class MilLowerer {
    private final List<Diagnostic> diagnostics = new ArrayList<>();
    private TargetProfile profile;
    private HardwareContract hardware;
    private Path file;

    public MilLoweringResult lower(MilDocument document, Path sourceFile, TargetProfile targetProfile,
                                   HardwareContract hardwareContract) {
        diagnostics.clear();
        profile = targetProfile;
        hardware = hardwareContract;
        file = sourceFile;

        Program source = document.program();
        List<FunctionDeclaration> functions = source.functions().stream().map(this::lowerFunction).toList();
        List<Statement> statements = source.statements().stream().map(this::lowerStatement).toList();
        Program program = new Program(source.imports(), source.exports(), functions, statements);
        return new MilLoweringResult(diagnostics.isEmpty() ? Optional.of(program) : Optional.empty(), diagnostics);
    }

    private FunctionDeclaration lowerFunction(FunctionDeclaration function) {
        return new FunctionDeclaration(function.name(), function.parameters(), function.returnType(),
            lowerBlock(function.body()), function.span());
    }

    private BlockStatement lowerBlock(BlockStatement block) {
        return new BlockStatement(block.statements().stream().map(this::lowerStatement).toList(), block.span());
    }

    private Statement lowerStatement(Statement statement) {
        if (statement instanceof MilMacroBlockStatement block) return lowerMacroBlock(block);
        if (statement instanceof VariableDeclaration declaration) {
            return new VariableDeclaration(declaration.mutable(), declaration.name(), declaration.declaredType(),
                lowerExpression(declaration.initializer()), declaration.span());
        }
        if (statement instanceof ExpressionStatement expression) {
            if (expression.expression() instanceof MilMacroCallExpression macro
                && "@io.draw".equals(macro.name())) return lowerDraw(macro);
            return new ExpressionStatement(lowerExpression(expression.expression()), expression.span());
        }
        if (statement instanceof BlockStatement block) return lowerBlock(block);
        if (statement instanceof WhileStatement loop) {
            return new WhileStatement(lowerExpression(loop.condition()), lowerBlock(loop.body()), loop.span());
        }
        if (statement instanceof DoWhileStatement loop) {
            return new DoWhileStatement(lowerBlock(loop.body()), lowerExpression(loop.condition()), loop.span());
        }
        if (statement instanceof IfStatement branch) {
            return new IfStatement(lowerExpression(branch.condition()), lowerBlock(branch.thenBlock()),
                branch.elseBranch().map(this::lowerStatement), branch.span());
        }
        if (statement instanceof ForStatement loop) {
            return new ForStatement(loop.declarationInitializer().map(value -> (VariableDeclaration) lowerStatement(value)),
                loop.expressionInitializer().map(this::lowerExpression), loop.condition().map(this::lowerExpression),
                loop.update().map(this::lowerExpression), lowerBlock(loop.body()), loop.span());
        }
        if (statement instanceof ForEachStatement loop) {
            return new ForEachStatement(loop.name(), lowerExpression(loop.iterable()), lowerBlock(loop.body()), loop.span());
        }
        if (statement instanceof ReturnStatement returned) {
            return new ReturnStatement(returned.value().map(this::lowerExpression), returned.span());
        }
        if (statement instanceof BreakStatement || statement instanceof ContinueStatement) return statement;
        throw new IllegalArgumentException("未知 MIL 语句：" + statement.getClass().getSimpleName());
    }

    private Statement lowerMacroBlock(MilMacroBlockStatement block) {
        MilMacroCallExpression macro = block.macro();
        return switch (macro.name()) {
            case "@unit.each" -> unitIteration(macro, block.body(), false);
            case "@unit.eachManaged" -> unitIteration(macro, block.body(), true);
            case "@building.each" -> buildingIteration(macro, block.body());
            default -> {
                error("MIL3101", "MIL 宏 " + macro.name() + " 不能拥有代码块", macro.span());
                yield lowerBlock(block.body());
            }
        };
    }

    private Statement unitIteration(MilMacroCallExpression macro, BlockStatement body, boolean managed) {
        String binding = identifier(macro.arguments().get(1), "Unit 绑定变量");
        int filterStart = managed ? 3 : 2;
        Expression query = unitQuery(macro.arguments(), binding, managed, filterStart);
        return new ForEachStatement(binding, query, lowerBlock(body), macro.span());
    }

    private Statement buildingIteration(MilMacroCallExpression macro, BlockStatement body) {
        String binding = identifier(macro.arguments().get(1), "Building 绑定变量");
        Expression query = buildingQuery(macro.arguments(), binding, 2);
        return new ForEachStatement(binding, query, lowerBlock(body), macro.span());
    }

    private MilDrawStatement lowerDraw(MilMacroCallExpression macro) {
        String hardwareName = hardwareName(macro.arguments().get(0), "Display");
        String command = identifier(macro.arguments().get(1), "绘制命令");
        List<Expression> arguments = macro.arguments().subList(2, macro.arguments().size()).stream()
            .map(this::lowerExpression).toList();
        return new MilDrawStatement(hardwareName, command, arguments, macro.span());
    }

    private Expression lowerExpression(Expression expression) {
        if (expression instanceof MilMacroCallExpression macro) return lowerMacroExpression(macro);
        if (expression instanceof MilGameSymbolExpression symbol) {
            error("MIL3102", "游戏符号 @" + symbol.name() + " 只能作为 MIL 宏的受限参数", symbol.span());
            return new Identifier("__mil_error", symbol.span());
        }
        if (expression instanceof AssignmentExpression assignment) {
            return new AssignmentExpression(assignment.target(), assignment.operator(), lowerExpression(assignment.value()),
                assignment.span());
        }
        if (expression instanceof BinaryExpression binary) {
            return new BinaryExpression(lowerExpression(binary.left()), binary.operator(),
                lowerExpression(binary.right()), binary.span());
        }
        if (expression instanceof UnaryExpression unary) {
            return new UnaryExpression(unary.operator(), lowerExpression(unary.operand()), unary.span());
        }
        if (expression instanceof CallExpression call) {
            return new CallExpression(lowerExpression(call.callee()), call.arguments().stream()
                .map(this::lowerExpression).toList(), call.span());
        }
        if (expression instanceof MemberAccessExpression member) {
            return new MemberAccessExpression(lowerExpression(member.target()), member.member(), member.span());
        }
        if (expression instanceof IndexExpression access) {
            return new IndexExpression(lowerExpression(access.target()), lowerExpression(access.index()), access.span());
        }
        if (expression instanceof LambdaExpression lambda) {
            return new LambdaExpression(lambda.parameter(), lowerExpression(lambda.body()), lambda.span());
        }
        if (expression instanceof ArrayLiteral array) {
            return new ArrayLiteral(array.elements().stream().map(this::lowerExpression).toList(), array.span());
        }
        if (expression instanceof TupleLiteral tuple) {
            return new TupleLiteral(tuple.elements().stream().map(this::lowerExpression).toList(), tuple.span());
        }
        if (expression instanceof MethodCallExpression call) {
            return new MethodCallExpression(call.target(), call.method(), call.arguments().stream()
                .map(this::lowerExpression).toList(), call.span());
        }
        return expression;
    }

    private Expression lowerMacroExpression(MilMacroCallExpression macro) {
        List<Expression> arguments = macro.arguments();
        return switch (macro.name()) {
            case "@unit.count" -> member(unitQuery(arguments, identifier(arguments.get(1), "Unit 绑定变量"), false, 2),
                "size", macro.span());
            case "@unit.countManaged" -> member(unitQuery(arguments,
                identifier(arguments.get(1), "Unit 绑定变量"), true, 3), "size", macro.span());
            case "@unit.get" -> call(member(unitQuery(arguments, identifier(arguments.get(1), "Unit 绑定变量"), false, 3),
                "get", macro.span()), List.of(lowerExpression(arguments.get(2))), macro.span());
            case "@unit.getManaged" -> call(member(unitQuery(arguments,
                identifier(arguments.get(1), "Unit 绑定变量"), true, 4), "get", macro.span()),
                List.of(lowerExpression(arguments.get(2))), macro.span());
            case "@unit.read", "@unit.refRead" -> member(lowerExpression(arguments.get(0)),
                identifier(arguments.get(1), "Unit 字段"), macro.span());
            case "@unit.alive", "@unit.refAlive" -> member(lowerExpression(arguments.get(0)), "alive", macro.span());
            case "@unit.move", "@unit.refMove" -> call(member(lowerExpression(arguments.get(0)), "move", macro.span()),
                arguments.subList(1, arguments.size()).stream().map(this::lowerExpression).toList(), macro.span());
            case "@building.count" -> member(buildingQuery(arguments,
                identifier(arguments.get(1), "Building 绑定变量"), 2), "size", macro.span());
            case "@building.get" -> call(member(buildingQuery(arguments,
                identifier(arguments.get(1), "Building 绑定变量"), 3), "get", macro.span()),
                List.of(lowerExpression(arguments.get(2))), macro.span());
            case "@building.read" -> member(lowerExpression(arguments.get(0)),
                identifier(arguments.get(1), "Building 字段"), macro.span());
            case "@building.control" -> call(member(lowerExpression(arguments.get(0)),
                buildingAction(identifier(arguments.get(1), "Building 动作"), macro.span()), macro.span()),
                arguments.subList(2, arguments.size()).stream().map(this::lowerExpression).toList(), macro.span());
            case "@io.print" -> call(member(new Identifier(hardwareName(arguments.get(0), "Message"), macro.span()),
                "print", macro.span()), arguments.subList(1, arguments.size()).stream()
                    .map(this::lowerExpression).toList(), macro.span());
            case "@io.draw" -> {
                error("MIL3101", "@io.draw 只能作为独立语句", macro.span());
                yield new Identifier("__mil_error", macro.span());
            }
            default -> {
                error("MIL3101", "尚未实现 MIL 宏的 lowering：" + macro.name(), macro.span());
                yield new Identifier("__mil_error", macro.span());
            }
        };
    }

    private Expression unitQuery(List<Expression> arguments, String binding, boolean managed, int filterStart) {
        String mlogType = gameSymbol(arguments.get(0), "Unit 内容类型");
        String mplType = profile.unitTypes().entrySet().stream()
            .filter(entry -> entry.getValue().mlogName().equals(mlogType)).map(java.util.Map.Entry::getKey)
            .findFirst().orElse(null);
        if (mplType == null) {
            error("MIL3103", "target " + profile.id() + " 不支持 Unit 游戏类型 @" + mlogType,
                arguments.get(0).span());
            mplType = "Unknown";
        }
        Expression query = call(member(new Identifier("Unit", arguments.get(0).span()), "getAll" + mplType,
            arguments.get(0).span()), List.of(), arguments.get(0).span());
        for (int index = filterStart; index < arguments.size(); index++) {
            Expression predicate = lowerExpression(arguments.get(index));
            query = call(member(query, "where", predicate.span()),
                List.of(new LambdaExpression(binding, predicate, predicate.span())), predicate.span());
        }
        if (managed) {
            Expression limit = lowerExpression(arguments.get(filterStart - 1));
            query = call(member(query, "take", limit.span()), List.of(limit), limit.span());
        }
        return query;
    }

    private Expression buildingQuery(List<Expression> arguments, String binding, int filterStart) {
        String mlogType = gameSymbol(arguments.get(0), "Building 内容类型");
        String mplType = profile.buildingTypes().entrySet().stream()
            .filter(entry -> entry.getValue().mlogName().equals(mlogType)).map(java.util.Map.Entry::getKey)
            .findFirst().orElse(null);
        if (mplType == null) {
            error("MIL3103", "target " + profile.id() + " 不支持 Building 游戏类型 @" + mlogType,
                arguments.get(0).span());
            mplType = "Unknown";
        }
        Expression query = call(member(new Identifier("Building", arguments.get(0).span()), "getAll" + mplType,
            arguments.get(0).span()), List.of(), arguments.get(0).span());
        for (int index = filterStart; index < arguments.size(); index++) {
            Expression predicate = lowerExpression(arguments.get(index));
            query = call(member(query, "where", predicate.span()),
                List.of(new LambdaExpression(binding, predicate, predicate.span())), predicate.span());
        }
        return query;
    }

    private String buildingAction(String targetAction, SourceSpan span) {
        Set<String> names = new LinkedHashSet<>();
        for (TargetProfile.BuildingType building : profile.buildingTypes().values()) {
            building.actions().values().stream()
                .filter(action -> action.target().equals("control " + targetAction))
                .map(TargetProfile.BuildingAction::name).forEach(names::add);
        }
        if (names.size() == 1) return names.iterator().next();
        error("MIL3104", names.isEmpty() ? "未知 Building 目标动作：" + targetAction
            : "Building 目标动作存在歧义：" + targetAction, span);
        return "__mil_error";
    }

    private String hardwareName(Expression expression, String expectedType) {
        String alias = gameSymbol(expression, expectedType + " 游戏链接");
        List<HardwareContract.LinkDeclaration> matches = hardware.links().stream()
            .filter(link -> link.gameAlias().equals(alias) && link.mplType().equals(expectedType)).toList();
        if (matches.size() == 1) return matches.get(0).mplName();
        error("MIL3105", matches.isEmpty()
            ? "硬件声明中找不到 " + expectedType + " 游戏链接 @" + alias
            : "硬件声明中存在多个 " + expectedType + " 游戏链接 @" + alias, expression.span());
        return "__mil_hardware_error";
    }

    private String gameSymbol(Expression expression, String role) {
        if (expression instanceof MilGameSymbolExpression symbol) return symbol.name();
        error("MIL3102", role + " 必须使用 @游戏符号", expression.span());
        return "unknown";
    }

    private String identifier(Expression expression, String role) {
        if (expression instanceof Identifier identifier) return identifier.name();
        error("MIL3102", role + " 必须是标识符", expression.span());
        return "__mil_error";
    }

    private MemberAccessExpression member(Expression target, String name, SourceSpan span) {
        return new MemberAccessExpression(target, name, span);
    }

    private CallExpression call(Expression callee, List<Expression> arguments, SourceSpan span) {
        return new CallExpression(callee, arguments, span);
    }

    private void error(String code, String message, SourceSpan span) {
        diagnostics.add(new Diagnostic(Severity.ERROR, code, message, Optional.ofNullable(file), Optional.of(span)));
    }
}
