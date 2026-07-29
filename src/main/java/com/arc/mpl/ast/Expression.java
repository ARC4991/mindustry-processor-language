package com.arc.mpl.ast;

import com.arc.mpl.diagnostic.Diagnostic.SourceSpan;

public sealed interface Expression permits IntegerLiteral, FloatLiteral, StringLiteral, BooleanLiteral, NullLiteral, Identifier,
    MethodCallExpression, MemberAccessExpression, CallExpression, LambdaExpression, UnaryExpression, BinaryExpression,
    AssignmentExpression, MemberAssignmentExpression, NewExpression, ArrayLiteral, TupleLiteral, IndexExpression,
    MilMacroCallExpression, MilGameSymbolExpression {
    SourceSpan span();
}
