package com.arc.mpl.project;

import com.arc.mpl.profile.TargetProfile;

/** Compiler-owned runtime topology. MPL source never names its processors or Memory blocks. */
public record RuntimePlan(
    TargetProfile.ProcessorKind processor,
    int instructions,
    int labels,
    int maxTokensPerStatement,
    int virtualSlots,
    int physicalSlots,
    int memoryCells,
    int memoryBanks
) {
    public String processorId() {
        return switch (processor) {
            case MICRO -> "micro";
            case LOGIC -> "logic";
            case HYPER -> "hyper";
        };
    }
}
