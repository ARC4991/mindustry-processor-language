package com.arc.mpl.project;

/** Exact target-code body cost used before helper Runtime wrappers are emitted. */
public record RuntimeHelperCost(int instructions, int labels) {
    public RuntimeHelperCost {
        if (instructions < 0 || labels < 0) throw new IllegalArgumentException("helper 成本不得为负数");
    }

    public static RuntimeHelperCost unit() {
        return new RuntimeHelperCost(1, 1);
    }
}
