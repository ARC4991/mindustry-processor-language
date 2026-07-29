package com.arc.mpl.hir;

/** Types that the first lowering stage can represent without runtime support. */
public enum ValueType implements MplType {
    INT,
    FLOAT,
    BOOL,
    STRING,
    UNIT,
    BUILDING,
    VOID,
    ERROR;

    @Override
    public boolean canAssignFrom(MplType value) {
        return this == value || this == FLOAT && value == INT || this == ERROR || value == ERROR;
    }

    @Override
    public String displayName() {
        return switch (this) {
            case INT -> "Int";
            case FLOAT -> "Float";
            case BOOL -> "Bool";
            case STRING -> "String";
            case UNIT -> "Unit";
            case BUILDING -> "Building";
            case VOID -> "Void";
            case ERROR -> "错误类型";
        };
    }
}
