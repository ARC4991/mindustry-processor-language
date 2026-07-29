package com.arc.mpl.ast;

import com.arc.mpl.diagnostic.Diagnostic.SourceSpan;

public sealed interface Statement permits VariableDeclaration, ExpressionStatement, BlockStatement, WhileStatement,
    IfStatement, ForEachStatement {
    SourceSpan span();
}
