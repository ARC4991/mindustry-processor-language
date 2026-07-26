package com.arc.mpl.hir;

public sealed interface HirStatement permits HirVariableDeclaration, HirExpressionStatement, HirPrintStatement {
}
