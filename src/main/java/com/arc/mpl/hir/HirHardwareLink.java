package com.arc.mpl.hir;

import java.util.Objects;

/** Compiler-known external building link; it is never represented as an MPL number. */
public record HirHardwareLink(String mplName, String gameAlias, String mplType) implements HirExpression {
    public HirHardwareLink {
        Objects.requireNonNull(mplName, "mplName");
        Objects.requireNonNull(gameAlias, "gameAlias");
        Objects.requireNonNull(mplType, "mplType");
    }

    @Override
    public ValueType type() {
        return ValueType.BUILDING;
    }
}
