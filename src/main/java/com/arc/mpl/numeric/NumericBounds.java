package com.arc.mpl.numeric;

/** Shared target-independent bounds for MPL's total numeric semantics. */
public final class NumericBounds {
    public static final long INT_MIN = Integer.MIN_VALUE;
    public static final long INT_MAX = Integer.MAX_VALUE;

    private NumericBounds() {
    }

    /** Saturates an exact Java long to MPL's signed 32-bit Int range. */
    public static long saturatingInt(long value) {
        if (value < INT_MIN) return INT_MIN;
        if (value > INT_MAX) return INT_MAX;
        return value;
    }
}
