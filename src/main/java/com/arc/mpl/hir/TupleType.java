package com.arc.mpl.hir;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/** Fixed-length, positionally typed aggregate. */
public record TupleType(List<MplType> elementTypes) implements MplType {
    public TupleType {
        elementTypes = List.copyOf(Objects.requireNonNull(elementTypes, "elementTypes"));
        if (elementTypes.size() < 2) throw new IllegalArgumentException("tuple requires at least two elements");
        if (elementTypes.stream().anyMatch(type -> type == ValueType.VOID || type == ValueType.ERROR)) {
            throw new IllegalArgumentException("tuple elements must be concrete value types");
        }
    }

    @Override
    public boolean canAssignFrom(MplType source) {
        return equals(source);
    }

    @Override
    public String displayName() {
        return elementTypes.stream().map(MplType::displayName).collect(Collectors.joining(", ", "(", ")"));
    }
}
