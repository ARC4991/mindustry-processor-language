package com.arc.mpl.hir;

import java.util.Objects;

/** A lazy, weakly consistent set query over one profile-defined Unit type. */
public record UnitSetType(String unitType) implements MplType {
    public UnitSetType {
        Objects.requireNonNull(unitType, "unitType");
        if (!unitType.matches("[A-Z][A-Za-z0-9]*")) {
            throw new IllegalArgumentException("Unit 类型必须使用大驼峰命名：" + unitType);
        }
    }

    @Override
    public boolean canAssignFrom(MplType source) {
        return equals(source);
    }

    @Override
    public String displayName() {
        return "Set<Unit<" + unitType + ">>";
    }
}
