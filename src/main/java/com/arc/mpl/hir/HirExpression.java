package com.arc.mpl.hir;

public sealed interface HirExpression permits HirConstant, HirText, HirVariable, HirHardwareLink, HirUnary, HirBinary,
    HirAssignment, HirMemberAccess, HirIntrinsicCall, HirFunctionCall, HirArrayLiteral, HirTupleLiteral, HirIndexAccess,
    HirDynamicIndexAccess, HirCollectionLiteral, HirCollectionContains, HirUnitQuery, HirUnitQuerySize, HirUnitQueryGet,
    HirBuildingQuery, HirBuildingQuerySize, HirBuildingQueryGet, HirNewObject, HirObjectFieldRead,
    HirObjectFieldAssignment {
    MplType type();
}
