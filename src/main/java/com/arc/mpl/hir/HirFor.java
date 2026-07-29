package com.arc.mpl.hir;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Counting loop retained in HIR so continue can target its update expression. */
public record HirFor(Optional<HirVariableDeclaration> declarationInitializer,
                     Optional<HirExpression> expressionInitializer, HirExpression condition,
                     Optional<HirExpression> update, List<HirStatement> body) implements HirStatement {
    public HirFor {
        declarationInitializer = Objects.requireNonNull(declarationInitializer, "declarationInitializer");
        expressionInitializer = Objects.requireNonNull(expressionInitializer, "expressionInitializer");
        if (declarationInitializer.isPresent() && expressionInitializer.isPresent()) {
            throw new IllegalArgumentException("for 初始化不能同时是声明和表达式");
        }
        Objects.requireNonNull(condition, "condition");
        update = Objects.requireNonNull(update, "update");
        body = List.copyOf(Objects.requireNonNull(body, "body"));
    }
}
