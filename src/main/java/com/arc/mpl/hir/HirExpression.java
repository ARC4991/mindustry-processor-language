package com.arc.mpl.hir;

public sealed interface HirExpression permits HirConstant, HirText, HirVariable, HirUnary, HirBinary, HirAssignment {
    ValueType type();
}
