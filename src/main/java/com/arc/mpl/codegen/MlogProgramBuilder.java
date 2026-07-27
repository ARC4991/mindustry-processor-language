package com.arc.mpl.codegen;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Structured emitter for the Mindustry logic instructions currently produced by MPL.
 *
 * <p>The generator deals in target concepts ({@link #unitBind(String)},
 * {@link #jump(Label, JumpCondition, String, String)}, and so on) instead of
 * assembling instruction strings itself. Rendering remains deliberately thin:
 * the final output is still ordinary mlog that can be pasted into the game.</p>
 */
final class MlogProgramBuilder {
    private final MlogLabelStyle labelStyle;
    private final List<Instruction> instructions = new ArrayList<>();
    private int nextLabelIndex;

    MlogProgramBuilder(MlogLabelStyle labelStyle) {
        this.labelStyle = Objects.requireNonNull(labelStyle, "labelStyle");
    }

    Label newLabel(String debugRole) {
        if (debugRole == null || !debugRole.matches("[A-Za-z][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("invalid mlog label role: " + debugRole);
        }
        return new Label(labelStyle.nameFor(debugRole, nextLabelIndex++));
    }

    void label(Label label) {
        instructions.add(new LabelInstruction(label));
    }

    void set(String target, String value) {
        instructions.add(new SetInstruction(target, value));
    }

    void print(String value) {
        instructions.add(new PrintInstruction(value));
    }

    void printFlush(String target) {
        instructions.add(new PrintFlushInstruction(target));
    }

    void jump(Label target, JumpCondition condition, String value, String compare) {
        instructions.add(new JumpInstruction(target, condition, value, compare));
    }

    void unitBind(String unit) {
        instructions.add(new UnitBindInstruction(unit));
    }

    void sensor(String result, String target, String property) {
        instructions.add(new SensorInstruction(result, target, property));
    }

    void operation(Operation operation, String result, String left, String right) {
        instructions.add(new OperationInstruction(operation, result, left, right));
    }

    void unitControl(UnitControlCommand command, String first, String second, String third, String fourth, String fifth) {
        instructions.add(new UnitControlInstruction(command, first, second, third, fourth, fifth));
    }

    void stop() {
        instructions.add(StopInstruction.INSTANCE);
    }

    String render() {
        if (instructions.isEmpty()) return "";
        return instructions.stream().map(Instruction::render).collect(java.util.stream.Collectors.joining("\n", "", "\n"));
    }

    enum Operation {
        ADD("add"),
        SUB("sub"),
        MUL("mul"),
        DIV("div"),
        MOD("mod"),
        EQUAL("equal"),
        NOT_EQUAL("notEqual"),
        LESS_THAN("lessThan"),
        LESS_THAN_EQ("lessThanEq"),
        GREATER_THAN("greaterThan"),
        GREATER_THAN_EQ("greaterThanEq"),
        LAND("land"),
        OR("or"),
        SIN("sin"),
        COS("cos");

        private final String mnemonic;

        Operation(String mnemonic) {
            this.mnemonic = mnemonic;
        }
    }

    enum JumpCondition {
        ALWAYS("always"),
        EQUAL("equal"),
        NOT_EQUAL("notEqual"),
        LESS_THAN_EQ("lessThanEq"),
        GREATER_THAN_EQ("greaterThanEq"),
        STRICT_EQUAL("strictEqual");

        private final String mnemonic;

        JumpCondition(String mnemonic) {
            this.mnemonic = mnemonic;
        }
    }

    enum UnitControlCommand {
        FLAG("flag"),
        MOVE("move");

        private final String mnemonic;

        UnitControlCommand(String mnemonic) {
            this.mnemonic = mnemonic;
        }
    }

    record Label(String value) {
        Label {
            if (value == null || !value.matches("[_A-Za-z][_A-Za-z0-9]*")) {
                throw new IllegalArgumentException("invalid mlog label: " + value);
            }
        }
    }

    private sealed interface Instruction permits LabelInstruction, SetInstruction, PrintInstruction,
        PrintFlushInstruction, JumpInstruction, UnitBindInstruction, SensorInstruction,
        OperationInstruction, UnitControlInstruction, StopInstruction {
        String render();
    }

    private record LabelInstruction(Label label) implements Instruction {
        @Override public String render() {
            return label.value() + ":";
        }
    }

    private record SetInstruction(String target, String value) implements Instruction {
        @Override public String render() {
            return "set " + target + " " + value;
        }
    }

    private record PrintInstruction(String value) implements Instruction {
        @Override public String render() {
            return "print " + value;
        }
    }

    private record PrintFlushInstruction(String target) implements Instruction {
        @Override public String render() {
            return "printflush " + target;
        }
    }

    private record JumpInstruction(Label target, JumpCondition condition, String value, String compare)
        implements Instruction {
        @Override public String render() {
            return "jump " + target.value() + " " + condition.mnemonic + " " + value + " " + compare;
        }
    }

    private record UnitBindInstruction(String unit) implements Instruction {
        @Override public String render() {
            return "ubind " + unit;
        }
    }

    private record SensorInstruction(String result, String target, String property) implements Instruction {
        @Override public String render() {
            return "sensor " + result + " " + target + " " + property;
        }
    }

    private record OperationInstruction(Operation operation, String result, String left, String right)
        implements Instruction {
        @Override public String render() {
            return "op " + operation.mnemonic + " " + result + " " + left + " " + right;
        }
    }

    private record UnitControlInstruction(UnitControlCommand command, String first, String second,
                                          String third, String fourth, String fifth) implements Instruction {
        @Override public String render() {
            return "ucontrol " + command.mnemonic + " " + first + " " + second + " " + third
                + " " + fourth + " " + fifth;
        }
    }

    private enum StopInstruction implements Instruction {
        INSTANCE;

        @Override public String render() {
            return "stop";
        }
    }
}
