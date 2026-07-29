package com.arc.mpl.profile;

import com.arc.mpl.hir.ValueType;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Immutable description of a Mindustry Logic target version. */
public interface TargetProfile {
    String id();

    Set<String> capabilities();

    int memoryCellCapacity();

    int memoryBankCapacity();

    /** Statically sized Display blocks accepted by a hardware contract. */
    List<DisplayType> displayTypes();

    default Optional<DisplayType> displayType(int width, int height) {
        return displayTypes().stream().filter(type -> type.width() == width && type.height() == height).findFirst();
    }

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
    Map<String, UnitType> unitTypes();

    Optional<UnitType> unitType(String mplType);

    /** Read-only Unit field available to MPL in this profile. */
    Optional<ValueType> unitPropertyType(String property);

    /** Unit control action implemented by the current profile and compiler slice. */
    Optional<UnitAction> unitAction(String action);

    /** Linked building interface that may appear in a project hardware contract. */
    Map<String, BuildingType> buildingTypes();

    Optional<BuildingType> buildingType(String mplType);

    /** Raw target instructions declared by this profile. */
    List<Instruction> instructions();

    /** MIL macro contracts declared by this profile. */
    List<Macro> macros();

    default Optional<Macro> macro(String name) {
        return macros().stream().filter(macro -> macro.name().equals(name)).findFirst();
    }

    record UnitType(String mlogName, boolean logicControllable) {
    }

    record DisplayType(String mlogName, int width, int height) {
        public DisplayType {
            if (mlogName == null || mlogName.isBlank()) throw new IllegalArgumentException("Display mlogName 不能为空");
            if (width < 1 || height < 1) throw new IllegalArgumentException("Display 尺寸必须为正数");
        }
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

    record BuildingAction(String name, List<ValueType> parameterTypes, String target) {
        public BuildingAction {
            parameterTypes = List.copyOf(parameterTypes);
            if (!target.matches("control [a-zA-Z][a-zA-Z0-9]*")) {
                throw new IllegalArgumentException("Building 动作必须映射到受限 control 指令：" + target);
            }
        }
    }

    record Instruction(String opcode, List<String> operands, Set<String> permissions) {
        public Instruction {
            operands = List.copyOf(operands);
            permissions = Set.copyOf(permissions);
        }
    }

    record Macro(String name, MacroVisibility visibility, MacroBody body, List<MacroParameter> parameters, Set<String> effects,
                 MacroCost maxCost, List<String> lowering) {
        public Macro {
            if (name == null || !name.matches("@[a-z]+(?:\\.[a-zA-Z][a-zA-Z0-9]*)+")) {
                throw new IllegalArgumentException("MIL 宏名称无效：" + name);
            }
            if (visibility == null) throw new IllegalArgumentException("MIL 宏必须声明可见性：" + name);
            if (body == null) throw new IllegalArgumentException("MIL 宏必须声明代码块形态：" + name);
            parameters = List.copyOf(parameters);
            effects = Set.copyOf(effects);
            lowering = List.copyOf(lowering);
            if (lowering.isEmpty()) throw new IllegalArgumentException("MIL 宏必须声明 lowering：" + name);
        }
    }

    enum MacroVisibility {
        PUBLIC,
        RUNTIME_PRIVATE
    }

    enum MacroBody {
        NONE,
        REQUIRED
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
