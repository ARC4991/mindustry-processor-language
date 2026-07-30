package com.arc.mpl.hir;

import java.util.Objects;

/** Homogeneous collection type. Capacity is a layout property, not part of its public type identity. */
public record CollectionType(Kind kind, MplType elementType) implements MplType {
    public CollectionType {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(elementType, "elementType");
        if (elementType == ValueType.VOID || elementType == ValueType.ERROR) {
            throw new IllegalArgumentException("collection element type must be a concrete value type");
        }
    }

    @Override
    public boolean canAssignFrom(MplType source) {
        return equals(source);
    }

    @Override
    public String displayName() {
        return switch (kind) {
            case ARRAY -> elementType.displayName() + "[]";
            case LIST -> "List<" + elementType.displayName() + ">";
            case SET -> "Set<" + elementType.displayName() + ">";
            case MUTABLE_LIST -> "MutableList<" + elementType.displayName() + ">";
        };
    }

    public enum Kind {
        ARRAY,
        LIST,
        SET,
        MUTABLE_LIST
    }
}
