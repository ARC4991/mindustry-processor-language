package com.arc.mpl.ast;

import com.arc.mpl.diagnostic.Diagnostic.SourceSpan;

public sealed interface Expression permits IntegerLiteral, FloatLiteral, BooleanLiteral, Identifier, UnaryExpression, BinaryExpression, AssignmentExpression {
    SourceSpan span();
}
