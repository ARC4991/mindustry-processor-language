package com.arc.mpl.semantic;

import com.arc.mpl.ast.ArrayLiteral;
import com.arc.mpl.ast.AssignmentExpression;
import com.arc.mpl.ast.BinaryExpression;
import com.arc.mpl.ast.BlockStatement;
import com.arc.mpl.ast.CallExpression;
import com.arc.mpl.ast.DoWhileStatement;
import com.arc.mpl.ast.Expression;
import com.arc.mpl.ast.ExpressionStatement;
import com.arc.mpl.ast.ForEachStatement;
import com.arc.mpl.ast.ForStatement;
import com.arc.mpl.ast.Identifier;
import com.arc.mpl.ast.IfStatement;
import com.arc.mpl.ast.IndexExpression;
import com.arc.mpl.ast.LambdaExpression;
import com.arc.mpl.ast.MemberAccessExpression;
import com.arc.mpl.ast.MemberAssignmentExpression;
import com.arc.mpl.ast.MethodCallExpression;
import com.arc.mpl.ast.MilDrawStatement;
import com.arc.mpl.ast.MilMacroBlockStatement;
import com.arc.mpl.ast.MilMacroCallExpression;
import com.arc.mpl.ast.NewExpression;
import com.arc.mpl.ast.ReturnStatement;
import com.arc.mpl.ast.Statement;
import com.arc.mpl.ast.TupleLiteral;
import com.arc.mpl.ast.UnaryExpression;
import com.arc.mpl.ast.VariableDeclaration;
import com.arc.mpl.ast.WhileStatement;

/**
 * Computes the first conservative object effect summary: whether a method can make its {@code this}
 * reference observable outside the current call. Calls through {@code this} remain escaping until
 * transitive method-effect summaries are introduced.
 */
final class ObjectReceiverEscapeAnalyzer {
    boolean receiverEscapes(BlockStatement body) {
        return body.statements().stream().anyMatch(this::receiverEscapes);
    }

    private boolean receiverEscapes(Statement statement) {
        if (statement instanceof VariableDeclaration declaration) return receiverEscapes(declaration.initializer(), false);
        if (statement instanceof ExpressionStatement expression) return receiverEscapes(expression.expression(), false);
        if (statement instanceof BlockStatement block) return receiverEscapes(block);
        if (statement instanceof WhileStatement loop) {
            return receiverEscapes(loop.condition(), false) || receiverEscapes(loop.body());
        }
        if (statement instanceof DoWhileStatement loop) {
            return receiverEscapes(loop.body()) || receiverEscapes(loop.condition(), false);
        }
        if (statement instanceof IfStatement branch) {
            return receiverEscapes(branch.condition(), false) || receiverEscapes(branch.thenBlock())
                || branch.elseBranch().map(this::receiverEscapes).orElse(false);
        }
        if (statement instanceof ForStatement loop) {
            return loop.declarationInitializer().map(value -> receiverEscapes(value.initializer(), false)).orElse(false)
                || loop.expressionInitializer().map(value -> receiverEscapes(value, false)).orElse(false)
                || loop.condition().map(value -> receiverEscapes(value, false)).orElse(false)
                || loop.update().map(value -> receiverEscapes(value, false)).orElse(false)
                || receiverEscapes(loop.body());
        }
        if (statement instanceof ForEachStatement loop) {
            return receiverEscapes(loop.iterable(), false) || receiverEscapes(loop.body());
        }
        if (statement instanceof ReturnStatement returned) {
            return returned.value().map(value -> receiverEscapes(value, false)).orElse(false);
        }
        if (statement instanceof MilDrawStatement draw) {
            return draw.arguments().stream().anyMatch(value -> receiverEscapes(value, false));
        }
        if (statement instanceof MilMacroBlockStatement macro) {
            return receiverEscapes(macro.macro(), false) || receiverEscapes(macro.body());
        }
        return false;
    }

    private boolean receiverEscapes(Expression expression, boolean borrowed) {
        if (expression instanceof Identifier identifier) return "this".equals(identifier.name()) && !borrowed;
        if (expression instanceof MemberAccessExpression member) return receiverEscapes(member.target(), true);
        if (expression instanceof MemberAssignmentExpression assignment) {
            return receiverEscapes(assignment.target(), true) || receiverEscapes(assignment.value(), false);
        }
        if (expression instanceof AssignmentExpression assignment) {
            return "this".equals(assignment.target().name()) || receiverEscapes(assignment.value(), false);
        }
        if (expression instanceof CallExpression call) {
            if (call.callee() instanceof MemberAccessExpression member
                && member.target() instanceof Identifier receiver && "this".equals(receiver.name())) {
                return true;
            }
            return receiverEscapes(call.callee(), false)
                || call.arguments().stream().anyMatch(value -> receiverEscapes(value, false));
        }
        if (expression instanceof MethodCallExpression call) {
            return call.arguments().stream().anyMatch(value -> receiverEscapes(value, false));
        }
        if (expression instanceof NewExpression allocation) {
            return allocation.arguments().stream().anyMatch(value -> receiverEscapes(value, false));
        }
        if (expression instanceof BinaryExpression binary) {
            boolean identity = "===".equals(binary.operator()) || "!==".equals(binary.operator())
                || "==".equals(binary.operator()) || "!=".equals(binary.operator());
            return receiverEscapes(binary.left(), identity) || receiverEscapes(binary.right(), identity);
        }
        if (expression instanceof UnaryExpression unary) return receiverEscapes(unary.operand(), false);
        if (expression instanceof LambdaExpression lambda) return receiverEscapes(lambda.body(), false);
        if (expression instanceof IndexExpression access) {
            return receiverEscapes(access.target(), false) || receiverEscapes(access.index(), false);
        }
        if (expression instanceof ArrayLiteral array) {
            return array.elements().stream().anyMatch(value -> receiverEscapes(value, false));
        }
        if (expression instanceof TupleLiteral tuple) {
            return tuple.elements().stream().anyMatch(value -> receiverEscapes(value, false));
        }
        if (expression instanceof MilMacroCallExpression macro) {
            return macro.arguments().stream().anyMatch(value -> receiverEscapes(value, false));
        }
        return false;
    }
}
