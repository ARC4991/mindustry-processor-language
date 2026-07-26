package com.arc.mpl.hir;

public sealed interface HirExpression permits HirConstant, HirVariable, HirUnary, HirBinary, HirAssignment {
    ValueType type();
}
