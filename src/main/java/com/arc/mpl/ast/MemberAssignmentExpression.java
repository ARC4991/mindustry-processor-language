package com.arc.mpl.ast;

import com.arc.mpl.diagnostic.Diagnostic.SourceSpan;

import java.util.Objects;

public record MemberAssignmentExpression(Expression target, String member, String operator,
                                         Expression value, SourceSpan span) implements Expression {
    public MemberAssignmentExpression {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(member, "member");
        Objects.requireNonNull(operator, "operator");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(span, "span");
    }
}
