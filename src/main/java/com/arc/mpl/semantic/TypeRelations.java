package com.arc.mpl.semantic;

import com.arc.mpl.hir.MplType;
import com.arc.mpl.hir.ObjectType;
import com.arc.mpl.hir.ValueType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Central nominal subtype and assignment relation used by semantic overload resolution. */
final class TypeRelations {
    private final Map<String, Optional<String>> parents;

    TypeRelations(Map<String, Optional<String>> parents) {
        Objects.requireNonNull(parents, "parents");
        this.parents = Map.copyOf(new LinkedHashMap<>(parents));
    }

    static TypeRelations empty() {
        return new TypeRelations(Map.of());
    }

    boolean canAssign(MplType target, MplType source) {
        if (target == ValueType.ERROR || source == ValueType.ERROR) return true;
        if (!(target instanceof ObjectType targetObject)) return target.canAssignFrom(source);
        if (source == ValueType.NULL) return targetObject.nullable();
        if (!(source instanceof ObjectType sourceObject)) return false;
        if (!targetObject.nullable() && sourceObject.nullable()) return false;
        return isSubtype(sourceObject.className(), targetObject.className());
    }

    boolean isSubtype(String candidate, String expected) {
        String current = candidate;
        while (current != null) {
            if (current.equals(expected)) return true;
            current = parents.getOrDefault(current, Optional.empty()).orElse(null);
        }
        return false;
    }

    int inheritanceDistance(String candidate, String expected) {
        int distance = 0;
        String current = candidate;
        while (current != null) {
            if (current.equals(expected)) return distance;
            current = parents.getOrDefault(current, Optional.empty()).orElse(null);
            distance++;
        }
        return Integer.MAX_VALUE;
    }

    boolean isStrictlyMoreSpecific(MplType candidate, MplType other) {
        return canAssign(other, candidate) && !canAssign(candidate, other);
    }
}
