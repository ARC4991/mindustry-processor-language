package com.arc.mpl.codegen;

/**
 * Naming policy for jump labels emitted into a Mindustry logic program.
 *
 * <p>Release labels intentionally contain no semantic text: source mlog has a
 * strict length budget and labels are repeated at every branch. Debug labels
 * retain the lowering role to make a pasted program practical to inspect.</p>
 */
public enum MlogLabelStyle {
    /** Smallest deterministic label spelling, for example {@code _0}. */
    RELEASE {
        @Override String nameFor(String role, int index) {
            return "_" + index;
        }
    },

    /** Readable deterministic label spelling, for example {@code mpl_unit_scan_0}. */
    DEBUG {
        @Override String nameFor(String role, int index) {
            return "mpl_" + role + "_" + index;
        }
    };

    abstract String nameFor(String role, int index);
}
