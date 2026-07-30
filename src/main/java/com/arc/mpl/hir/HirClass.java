package com.arc.mpl.hir;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Validated class layout and links to hidden-this method functions. */
public record HirClass(String name, Optional<String> superClass, boolean exported, List<Field> fields,
                       List<Method> methods) {
    public HirClass {
        Objects.requireNonNull(name, "name");
        superClass = Objects.requireNonNull(superClass, "superClass");
        fields = List.copyOf(Objects.requireNonNull(fields, "fields"));
        methods = List.copyOf(Objects.requireNonNull(methods, "methods"));
    }

    public HirClass(String name, boolean exported, List<Field> fields, List<Method> methods) {
        this(name, Optional.empty(), exported, fields, methods);
    }

    public record Field(String name, MplType type, boolean publicAccess) {
        public Field {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(type, "type");
        }
    }

    public record Method(String sourceName, String functionName, boolean publicAccess, boolean constructor) {
        public Method {
            Objects.requireNonNull(sourceName, "sourceName");
            Objects.requireNonNull(functionName, "functionName");
        }
    }
}
