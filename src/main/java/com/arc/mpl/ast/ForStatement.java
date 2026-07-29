package com.arc.mpl.ast;

import com.arc.mpl.diagnostic.Diagnostic.SourceSpan;

import java.util.Objects;
import java.util.Optional;

/** C-style counting loop; each header section may be omitted. */
public record ForStatement(Optional<VariableDeclaration> declarationInitializer,
                           Optional<Expression> expressionInitializer, Optional<Expression> condition,
                           Optional<Expression> update, BlockStatement body, SourceSpan span) implements Statement {
    public ForStatement {
        declarationInitializer = Objects.requireNonNull(declarationInitializer, "declarationInitializer");
        expressionInitializer = Objects.requireNonNull(expressionInitializer, "expressionInitializer");
        condition = Objects.requireNonNull(condition, "condition");
        update = Objects.requireNonNull(update, "update");
        if (declarationInitializer.isPresent() && expressionInitializer.isPresent()) {
            throw new IllegalArgumentException("for 初始化不能同时是声明和表达式");
        }
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(span, "span");
    }
}
