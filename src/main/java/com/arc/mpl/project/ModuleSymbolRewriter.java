package com.arc.mpl.project;

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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Applies linked top-level symbol identities while respecting source lexical shadowing. */
final class ModuleSymbolRewriter {
    private final Map<String, String> bindings;
    private final Map<String, String> declarations;
    private final Deque<Set<String>> locals = new ArrayDeque<>();

    ModuleSymbolRewriter(Map<String, String> bindings, Map<String, String> declarations) {
        this.bindings = Map.copyOf(bindings);
        this.declarations = Map.copyOf(declarations);
    }

    Program rewrite(Program source) {
        List<FunctionDeclaration> functions = source.functions().stream().map(this::function).toList();
        List<Statement> statements = source.statements().stream().map(value -> statement(value, true)).toList();
        return new Program(functions, statements);
    }

    private FunctionDeclaration function(FunctionDeclaration source) {
        locals.push(new HashSet<>());
        source.parameters().forEach(parameter -> locals.peek().add(parameter.name()));
        try {
            return new FunctionDeclaration(declarations.getOrDefault(source.name(), source.name()), source.parameters(),
                source.returnType(), block(source.body()), source.span());
        } finally {
            locals.pop();
        }
    }

    private BlockStatement block(BlockStatement source) {
        locals.push(new HashSet<>());
        try {
            return new BlockStatement(source.statements().stream().map(value -> statement(value, false)).toList(),
                source.span());
        } finally {
            locals.pop();
        }
    }

    private Statement statement(Statement source, boolean topLevel) {
        if (source instanceof VariableDeclaration declaration) {
            Expression initializer = expression(declaration.initializer());
            String name = topLevel ? declarations.getOrDefault(declaration.name(), declaration.name()) : declaration.name();
            if (!topLevel && !locals.isEmpty()) locals.peek().add(declaration.name());
            return new VariableDeclaration(declaration.mutable(), name, declaration.declaredType(), initializer,
                declaration.span());
        }
        if (source instanceof ExpressionStatement value) {
            return new ExpressionStatement(expression(value.expression()), value.span());
        }
        if (source instanceof BlockStatement nested) return block(nested);
        if (source instanceof WhileStatement loop) {
            return new WhileStatement(expression(loop.condition()), block(loop.body()), loop.span());
        }
        if (source instanceof DoWhileStatement loop) {
            return new DoWhileStatement(block(loop.body()), expression(loop.condition()), loop.span());
        }
        if (source instanceof IfStatement branch) {
            return new IfStatement(expression(branch.condition()), block(branch.thenBlock()),
                branch.elseBranch().map(value -> statement(value, false)), branch.span());
        }
        if (source instanceof ForStatement loop) return forLoop(loop);
        if (source instanceof ForEachStatement loop) {
            Expression iterable = expression(loop.iterable());
            locals.push(new HashSet<>(Set.of(loop.name())));
            try {
                return new ForEachStatement(loop.name(), iterable, block(loop.body()), loop.span());
            } finally {
                locals.pop();
            }
        }
        if (source instanceof ReturnStatement returned) {
            return new ReturnStatement(returned.value().map(this::expression), returned.span());
        }
        if (source instanceof MilDrawStatement draw) {
            return new MilDrawStatement(draw.hardwareName(), draw.command(),
                draw.arguments().stream().map(this::expression).toList(), draw.span());
        }
        if (source instanceof MilMacroBlockStatement macro) {
            return new MilMacroBlockStatement((MilMacroCallExpression) expression(macro.macro()),
                block(macro.body()), macro.span());
        }
        if (source instanceof BreakStatement || source instanceof ContinueStatement) return source;
        throw new IllegalArgumentException("未知模块语句：" + source.getClass().getSimpleName());
    }

    private ForStatement forLoop(ForStatement loop) {
        locals.push(new HashSet<>());
        try {
            var declaration = loop.declarationInitializer().map(value -> (VariableDeclaration) statement(value, false));
            var initializer = loop.expressionInitializer().map(this::expression);
            var condition = loop.condition().map(this::expression);
            var update = loop.update().map(this::expression);
            return new ForStatement(declaration, initializer, condition, update, block(loop.body()), loop.span());
        } finally {
            locals.pop();
        }
    }

    private Expression expression(Expression source) {
        if (source instanceof Identifier identifier) {
            return new Identifier(linked(identifier.name()), identifier.span());
        }
        if (source instanceof AssignmentExpression assignment) {
            Identifier target = new Identifier(linked(assignment.target().name()), assignment.target().span());
            return new AssignmentExpression(target, assignment.operator(), expression(assignment.value()), assignment.span());
        }
        if (source instanceof BinaryExpression binary) {
            return new BinaryExpression(expression(binary.left()), binary.operator(), expression(binary.right()), binary.span());
        }
        if (source instanceof UnaryExpression unary) {
            return new UnaryExpression(unary.operator(), expression(unary.operand()), unary.span());
        }
        if (source instanceof CallExpression call) {
            return new CallExpression(expression(call.callee()), call.arguments().stream()
                .map(this::expression).toList(), call.span());
        }
        if (source instanceof MemberAccessExpression member) {
            return new MemberAccessExpression(expression(member.target()), member.member(), member.span());
        }
        if (source instanceof IndexExpression access) {
            return new IndexExpression(expression(access.target()), expression(access.index()), access.span());
        }
        if (source instanceof LambdaExpression lambda) {
            locals.push(new HashSet<>(Set.of(lambda.parameter())));
            try {
                return new LambdaExpression(lambda.parameter(), expression(lambda.body()), lambda.span());
            } finally {
                locals.pop();
            }
        }
        if (source instanceof ArrayLiteral array) {
            return new ArrayLiteral(array.elements().stream().map(this::expression).toList(), array.span());
        }
        if (source instanceof TupleLiteral tuple) {
            return new TupleLiteral(tuple.elements().stream().map(this::expression).toList(), tuple.span());
        }
        if (source instanceof MethodCallExpression call) {
            return new MethodCallExpression(linked(call.target()), call.method(), call.arguments().stream()
                .map(this::expression).toList(), call.span());
        }
        if (source instanceof MilMacroCallExpression macro) {
            return new MilMacroCallExpression(macro.name(), macro.arguments().stream()
                .map(this::expression).toList(), macro.span());
        }
        if (source instanceof MilGameSymbolExpression) return source;
        return source;
    }

    private String linked(String name) {
        if (locals.stream().anyMatch(scope -> scope.contains(name))) return name;
        return bindings.getOrDefault(name, name);
    }
}
