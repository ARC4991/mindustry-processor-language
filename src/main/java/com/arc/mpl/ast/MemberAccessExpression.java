package com.arc.mpl.ast;

import com.arc.mpl.diagnostic.Diagnostic.SourceSpan;

import java.util.Objects;

/** A property or method target before an optional call suffix. */
public record MemberAccessExpression(Expression target, String member, SourceSpan span) implements Expression {
    public MemberAccessExpression {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(member, "member");
        Objects.requireNonNull(span, "span");
    }
}
