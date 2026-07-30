package com.arc.mpl.hir;

/** A source-level MPL type, including scalar and statically described aggregate types. */
public sealed interface MplType permits ValueType, TupleType, CollectionType, UnitType, BuildingType, ObjectType {
    /** Whether a value of {@code source} can be assigned without an implicit, lossy conversion. */
    boolean canAssignFrom(MplType source);

    /** Stable MPL spelling for diagnostics and MIL serialization. */
    String displayName();
}
