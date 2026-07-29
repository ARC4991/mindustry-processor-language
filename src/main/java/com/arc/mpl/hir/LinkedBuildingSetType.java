package com.arc.mpl.hir;

import java.util.Objects;

/** An immutable lazy query over the matching links declared in hardware.mplh. */
public record LinkedBuildingSetType(String buildingType) implements MplType {
    public LinkedBuildingSetType {
        Objects.requireNonNull(buildingType, "buildingType");
        if (!buildingType.matches("[A-Z][A-Za-z0-9]*")) {
            throw new IllegalArgumentException("Building 类型必须使用大驼峰命名：" + buildingType);
        }
    }

    @Override
    public boolean canAssignFrom(MplType source) {
        return equals(source);
    }

    @Override
    public String displayName() {
        return "LinkedBuildingSet<" + buildingType + ">";
    }
}
