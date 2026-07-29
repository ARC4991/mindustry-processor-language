package com.arc.mpl.hir;

public sealed interface HirExpression permits HirConstant, HirText, HirVariable, HirHardwareLink, HirUnary, HirBinary,
    HirAssignment, HirMemberAccess, HirIntrinsicCall, HirFunctionCall, HirArrayLiteral, HirTupleLiteral, HirIndexAccess,
    HirCollectionLiteral, HirCollectionContains {
    MplType type();
}
