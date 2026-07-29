package com.arc.mpl.profile;

import com.arc.mpl.hir.ValueType;

import java.util.List;
import java.util.Optional;
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

    /** Target content exposed by {@code Unit.getAll类型()} in this profile. */
    Optional<UnitType> unitType(String mplType);

    /** Read-only Unit field available to MPL in this profile. */
    Optional<ValueType> unitPropertyType(String property);

    /** Unit control action implemented by the current profile and compiler slice. */
    Optional<UnitAction> unitAction(String action);

    /** Linked building interface that may appear in a project hardware contract. */
    Optional<BuildingType> buildingType(String mplType);

    /** Raw target instructions declared by this profile. */
    List<Instruction> instructions();

    /** MIL macro contracts declared by this profile. */
    List<Macro> macros();

    record UnitType(String mlogName, boolean logicControllable) {
    }

    record UnitAction(String name, List<ValueType> parameterTypes) {
        public UnitAction {
            parameterTypes = List.copyOf(parameterTypes);
        }
    }

    record BuildingType(String mlogName, java.util.Map<String, ValueType> propertyTypes,
                        java.util.Map<String, BuildingAction> actions) {
        public BuildingType {
            propertyTypes = java.util.Map.copyOf(propertyTypes);
            actions = java.util.Map.copyOf(actions);
        }
    }

    record BuildingAction(String name, List<ValueType> parameterTypes) {
        public BuildingAction {
            parameterTypes = List.copyOf(parameterTypes);
        }
    }

    record Instruction(String opcode, List<String> operands, Set<String> permissions) {
        public Instruction {
            operands = List.copyOf(operands);
            permissions = Set.copyOf(permissions);
        }
    }

    record Macro(String name, List<MacroParameter> parameters, Set<String> effects,
                 MacroCost maxCost, List<String> lowering) {
        public Macro {
            parameters = List.copyOf(parameters);
            effects = Set.copyOf(effects);
            lowering = List.copyOf(lowering);
        }
    }

    record MacroParameter(String name, String type) {
    }

    record MacroCost(int instructions, int virtualSlots, int physicalSlots) {
        public MacroCost {
            if (instructions < 0 || virtualSlots < 0 || physicalSlots < 0) {
                throw new IllegalArgumentException("宏成本不得为负数");
            }
        }
    }

    enum ProcessorKind {
        MICRO,
        LOGIC,
        HYPER
    }
}
