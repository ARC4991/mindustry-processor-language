package com.arc.mpl.hir;

import java.util.Objects;

/** A typed Unit object reference; nullable references require explicit source-level narrowing. */
public record UnitType(String unitType, boolean nullable) implements MplType {
    public UnitType {
        Objects.requireNonNull(unitType, "unitType");
        if (!unitType.matches("[A-Z][A-Za-z0-9]*")) {
            throw new IllegalArgumentException("Unit 类型必须使用大驼峰命名：" + unitType);
        }
    }

    @Override
    public boolean canAssignFrom(MplType source) {
        if (source == ValueType.ERROR) return true;
        if (source == ValueType.NULL) return nullable;
        return source instanceof UnitType unit
            && unitType.equals(unit.unitType())
            && (nullable || !unit.nullable());
    }

    @Override
    public String displayName() {
        return "Unit<" + unitType + ">" + (nullable ? "?" : "");
    }

    public UnitType nonNullable() {
        return nullable ? new UnitType(unitType, false) : this;
    }
}
