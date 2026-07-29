package com.arc.mpl.ast;

import com.arc.mpl.diagnostic.Diagnostic.SourceSpan;

import java.util.List;
import java.util.Objects;

/** Source literal for a homogeneous array. */
public record ArrayLiteral(List<Expression> elements, SourceSpan span) implements Expression {
    public ArrayLiteral {
        elements = List.copyOf(Objects.requireNonNull(elements, "elements"));
        Objects.requireNonNull(span, "span");
    }
}
