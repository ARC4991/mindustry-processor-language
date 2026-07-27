package com.arc.mpl.profile;

import java.util.Set;

/** Immutable description of a Mindustry Logic target version. */
public interface TargetProfile {
    String id();

    Set<String> capabilities();

    int memoryCellCapacity();

    int memoryBankCapacity();

    int instructionsPerTick(ProcessorKind processor);

    /** Maximum executable mlog statements the target parser accepts. */
    int maxInstructions();

    /** Maximum named jump labels the target parser accepts. */
    int maxJumpLabels();

    /** Maximum tokens in one mlog statement, including the opcode. */
    int maxTokensPerStatement();

    /** Maximum unflushed draw commands held by one processor. */
    int maxGraphicsBufferCommands();

    /** Exclusive upper bound for a Display's existing plus newly flushed commands. */
    int displayFlushCommandLimit();

    /** Maximum visible Message text length, measured in UTF-16 code units. */
    int maxMessageUtf16CodeUnits();

    /** Largest magnitude preserved by v146/v159 draw command coordinate packing. */
    int maxDrawCoordinateMagnitude();

    enum ProcessorKind {
        MICRO,
        LOGIC,
        HYPER
    }
}
