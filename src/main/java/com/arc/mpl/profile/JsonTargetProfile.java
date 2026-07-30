package com.arc.mpl.profile;

import java.util.Map;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import com.arc.mpl.hir.ValueType;

/** Target profile loaded from the versioned JSON resource. */
record JsonTargetProfile(
    String id,
    Set<String> capabilities,
    int memoryCellCapacity,
    int memoryBankCapacity,
    List<DisplayType> displayTypes,
    Map<ProcessorKind, Integer> instructionsPerTick,
    Map<ProcessorKind, Integer> linkRanges,
    int maxInstructions,
    int maxJumpLabels,
    int maxTokensPerStatement,
    int maxGraphicsBufferCommands,
    int displayFlushCommandLimit,
    int maxMessageUtf16CodeUnits,
    int maxDrawCoordinateMagnitude,
    Map<String, UnitType> unitTypes,
    Map<String, ValueType> unitPropertyTypes,
    Map<String, UnitAction> unitActions,
    Map<String, BuildingType> buildingTypes,
    List<Instruction> instructions,
    List<Macro> macros
) implements TargetProfile {
    JsonTargetProfile {
        id = requireText(id, "id");
        capabilities = Set.copyOf(Objects.requireNonNull(capabilities, "capabilities"));
        instructionsPerTick = Map.copyOf(Objects.requireNonNull(instructionsPerTick, "instructionsPerTick"));
        linkRanges = Map.copyOf(Objects.requireNonNull(linkRanges, "linkRanges"));
        displayTypes = List.copyOf(Objects.requireNonNull(displayTypes, "displayTypes"));
        if (displayTypes.isEmpty()) throw new IllegalArgumentException("目标配置至少需要一种 Display");
        if (displayTypes.stream().map(type -> type.width() + "x" + type.height()).distinct().count() != displayTypes.size()) {
            throw new IllegalArgumentException("目标配置包含重复的 Display 尺寸");
        }
        if (displayTypes.stream().map(DisplayType::mlogName).distinct().count() != displayTypes.size()) {
            throw new IllegalArgumentException("目标配置包含重复的 Display 方块类型");
        }
        for (ProcessorKind kind : ProcessorKind.values()) {
            if (!instructionsPerTick.containsKey(kind) || instructionsPerTick.get(kind) < 1) {
                throw new IllegalArgumentException("目标配置缺少有效的 " + kind + " IPT");
            }
            if (!linkRanges.containsKey(kind) || linkRanges.get(kind) < 0) {
                throw new IllegalArgumentException("目标配置缺少有效的 " + kind + " 连接半径");
            }
        }
        requirePositive(memoryCellCapacity, "memoryCellCapacity");
        requirePositive(memoryBankCapacity, "memoryBankCapacity");
        requirePositive(maxInstructions, "maxInstructions");
        requireNonNegative(maxJumpLabels, "maxJumpLabels");
        requirePositive(maxTokensPerStatement, "maxTokensPerStatement");
        requirePositive(maxGraphicsBufferCommands, "maxGraphicsBufferCommands");
        requirePositive(displayFlushCommandLimit, "displayFlushCommandLimit");
        requirePositive(maxMessageUtf16CodeUnits, "maxMessageUtf16CodeUnits");
        requirePositive(maxDrawCoordinateMagnitude, "maxDrawCoordinateMagnitude");
        unitTypes = Map.copyOf(Objects.requireNonNull(unitTypes, "unitTypes"));
        unitPropertyTypes = Map.copyOf(Objects.requireNonNull(unitPropertyTypes, "unitPropertyTypes"));
        unitActions = Map.copyOf(Objects.requireNonNull(unitActions, "unitActions"));
        buildingTypes = Map.copyOf(Objects.requireNonNull(buildingTypes, "buildingTypes"));
        instructions = List.copyOf(Objects.requireNonNull(instructions, "instructions"));
        if (instructions.stream().map(Instruction::opcode).distinct().count() != instructions.size()) {
            throw new IllegalArgumentException("目标配置包含重复的 mlog 指令名称");
        }
        macros = List.copyOf(Objects.requireNonNull(macros, "macros"));
        if (macros.stream().map(Macro::name).distinct().count() != macros.size()) {
            throw new IllegalArgumentException("目标配置包含重复的 MIL 宏名称");
        }
        Set<String> opcodes = instructions.stream().map(Instruction::opcode).collect(java.util.stream.Collectors.toSet());
        for (Macro macro : macros) {
            for (String lowering : macro.lowering()) {
                if (!opcodes.contains(lowering)) {
                    throw new IllegalArgumentException("MIL 宏 " + macro.name() + " 引用了未声明的 mlog 指令：" + lowering);
                }
            }
        }
    }

    @Override
    public int instructionsPerTick(ProcessorKind processor) {
        return instructionsPerTick.get(Objects.requireNonNull(processor, "processor"));
    }

    @Override
    public int linkRange(ProcessorKind processor) {
        return linkRanges.get(Objects.requireNonNull(processor, "processor"));
    }

    @Override
    public java.util.Optional<UnitType> unitType(String mplType) {
        return java.util.Optional.ofNullable(unitTypes.get(mplType));
    }

    @Override
    public java.util.Optional<ValueType> unitPropertyType(String property) {
        return java.util.Optional.ofNullable(unitPropertyTypes.get(property));
    }

    @Override
    public java.util.Optional<UnitAction> unitAction(String action) {
        return java.util.Optional.ofNullable(unitActions.get(action));
    }

    @Override
    public java.util.Optional<BuildingType> buildingType(String mplType) {
        return java.util.Optional.ofNullable(buildingTypes.get(mplType));
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("目标配置缺少 " + field);
        return value;
    }

    private static void requirePositive(int value, String field) {
        if (value < 1) throw new IllegalArgumentException("目标配置的 " + field + " 必须大于 0");
    }

    private static void requireNonNegative(int value, String field) {
        if (value < 0) throw new IllegalArgumentException("目标配置的 " + field + " 不得为负数");
    }
}
