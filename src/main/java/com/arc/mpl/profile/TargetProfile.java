package com.arc.mpl.profile;

import java.util.Set;

/** Immutable description of a Mindustry Logic target version. */
public interface TargetProfile {
    String id();

    Set<String> capabilities();

    int memoryCellCapacity();

    int memoryBankCapacity();

    int instructionsPerTick(ProcessorKind processor);

    enum ProcessorKind {
        MICRO,
        LOGIC,
        HYPER
    }
}
