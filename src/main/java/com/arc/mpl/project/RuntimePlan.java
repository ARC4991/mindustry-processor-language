package com.arc.mpl.project;

import com.arc.mpl.memory.PhysicalMemoryLayout;
import com.arc.mpl.profile.TargetProfile;

/** Compiler-owned runtime topology. MPL source never names its processors or Memory blocks. */
public record RuntimePlan(
    TargetProfile.ProcessorKind processor,
    int instructions,
    int labels,
    int maxTokensPerStatement,
    int virtualSlots,
    PhysicalMemoryLayout physicalMemoryLayout
) {
    public RuntimePlan {
        if (physicalMemoryLayout == null) physicalMemoryLayout = PhysicalMemoryLayout.empty();
    }

    public String processorId() {
        return switch (processor) {
            case MICRO -> "micro";
            case LOGIC -> "logic";
            case HYPER -> "hyper";
        };
    }

    public int physicalSlots() {
        return physicalMemoryLayout.physicalSlots();
    }

    public int memoryCells() {
        return Math.toIntExact(physicalMemoryLayout.memoryCells());
    }

    public int memoryBanks() {
        return Math.toIntExact(physicalMemoryLayout.memoryBanks());
    }
}
