package com.arc.mpl.hir;

import java.util.Objects;

/** A typed linked-Building object reference with Kotlin-style nullability. */
public record BuildingType(String buildingType, boolean nullable) implements MplType {
    public BuildingType {
        Objects.requireNonNull(buildingType, "buildingType");
        if (!buildingType.matches("[A-Z][A-Za-z0-9]*")) {
            throw new IllegalArgumentException("Building 类型必须使用大驼峰命名：" + buildingType);
        }
    }

    @Override
    public boolean canAssignFrom(MplType source) {
        if (source == ValueType.ERROR) return true;
        if (source == ValueType.NULL) return nullable;
        return source instanceof BuildingType building
            && buildingType.equals(building.buildingType())
            && (nullable || !building.nullable());
    }

    @Override
    public String displayName() {
        return "Building<" + buildingType + ">" + (nullable ? "?" : "");
    }

    public BuildingType nonNullable() {
        return nullable ? new BuildingType(buildingType, false) : this;
    }
}
