package com.arc.mpl.ast;

import com.arc.mpl.diagnostic.Diagnostic.SourceSpan;

import java.util.List;
import java.util.Objects;

/** Source literal for a fixed, positionally typed tuple. */
public record TupleLiteral(List<Expression> elements, SourceSpan span) implements Expression {
    public TupleLiteral {
        elements = List.copyOf(Objects.requireNonNull(elements, "elements"));
        if (elements.size() < 2) throw new IllegalArgumentException("tuple literal requires at least two elements");
        Objects.requireNonNull(span, "span");
    }
}
