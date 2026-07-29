package com.arc.mpl.hir;

/** Types that the first lowering stage can represent without runtime support. */
public enum ValueType {
    INT,
    FLOAT,
    BOOL,
    STRING,
    UNIT,
    BUILDING,
    VOID,
    ERROR;

    public boolean canAssignFrom(ValueType value) {
        return this == value || this == FLOAT && value == INT || this == ERROR || value == ERROR;
    }
}
