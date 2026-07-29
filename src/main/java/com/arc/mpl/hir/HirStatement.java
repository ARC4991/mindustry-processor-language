package com.arc.mpl.hir;

public sealed interface HirStatement permits HirVariableDeclaration, HirExpressionStatement, HirPrintStatement,
    HirBlock, HirWhile, HirDoWhile, HirFor, HirIf, HirBreak, HirContinue, HirUnitIteration, HirUnitControl,
    HirBuildingIteration, HirBuildingControl, HirDraw, HirDrawFlush, HirReturn, HirCollectionSet, HirAggregateIteration {
}
