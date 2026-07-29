package com.arc.mpl.hir;

public sealed interface HirStatement permits HirVariableDeclaration, HirExpressionStatement, HirPrintStatement,
    HirBlock, HirWhile, HirDoWhile, HirIf, HirBreak, HirContinue, HirUnitIteration, HirUnitControl, HirBuildingControl {
}
