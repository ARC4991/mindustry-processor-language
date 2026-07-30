package com.arc.mpl.hir;

public sealed interface HirExpression permits HirConstant, HirText, HirVariable, HirHardwareLink, HirUnary, HirBinary,
    HirAssignment, HirMemberAccess, HirIntrinsicCall, HirFunctionCall, HirArrayLiteral, HirTupleLiteral, HirIndexAccess,
    HirDynamicIndexAccess, HirCollectionLiteral, HirMutableListLiteral, HirCollectionContains, HirUnitQuery, HirUnitQuerySize, HirUnitQueryGet,
    HirBuildingQuery, HirBuildingQuerySize, HirBuildingQueryGet, HirNewObject, HirObjectFieldRead,
    HirObjectFieldAssignment, HirMethodCall, HirStringConcat, HirStringLength, HirStringComparison, HirStringSnapshot {
    MplType type();
}
