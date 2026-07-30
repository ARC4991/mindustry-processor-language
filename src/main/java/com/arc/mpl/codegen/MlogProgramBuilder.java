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
    private int nextInstructionAddress;

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
        label.bind(nextInstructionAddress);
        instructions.add(new LabelInstruction(label));
    }

    void set(String target, String value) {
        add(new SetInstruction(target, value));
    }

    void setCounter(Label target) {
        add(new CounterJumpInstruction(target));
    }

    void print(String value) {
        add(new PrintInstruction(value));
    }

    void printChar(String value) {
        add(new PrintCharInstruction(value));
    }

    void printFlush(String target) {
        add(new PrintFlushInstruction(target));
    }

    void draw(String command, List<String> arguments) {
        if (arguments.size() != 6) throw new IllegalArgumentException("draw 指令需要六个参数");
        add(new DrawInstruction(command, arguments));
    }

    void drawFlush(String target) {
        add(new DrawFlushInstruction(target));
    }

    void read(String result, String memory, String index) {
        add(new ReadInstruction(result, memory, index));
    }

    void write(String value, String memory, String index) {
        add(new WriteInstruction(value, memory, index));
    }

    /** Writes a label's resolved instruction address into Memory. */
    void writeAddress(Label value, String memory, String index) {
        add(new LabelAddressWriteInstruction(value, memory, index));
    }

    void jump(Label target, JumpCondition condition, String value, String compare) {
        add(new JumpInstruction(target, condition, value, compare));
    }

    void unitBind(String unit) {
        add(new UnitBindInstruction(unit));
    }

    void sensor(String result, String target, String property) {
        add(new SensorInstruction(result, target, property));
    }

    void operation(Operation operation, String result, String left, String right) {
        add(new OperationInstruction(operation, result, left, right));
    }

    void unitControl(UnitControlCommand command, String first, String second, String third, String fourth, String fifth) {
        add(new UnitControlInstruction(command, first, second, third, fourth, fifth));
    }

    /** Emits the fixed-width target control instruction for a statically declared building link. */
    void buildingControl(String target, String action, List<String> arguments) {
        if (arguments.size() > 5) throw new IllegalArgumentException("control 指令至多接受五个参数");
        List<String> operands = new ArrayList<>(arguments);
        while (operands.size() < 5) operands.add("0");
        add(new BuildingControlInstruction(target, action, List.copyOf(operands)));
    }

    void stop() {
        add(StopInstruction.INSTANCE);
    }

    private void add(Instruction instruction) {
        instructions.add(instruction);
        nextInstructionAddress++;
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
        MIN("min"),
        MAX("max"),
        EQUAL("equal"),
        NOT_EQUAL("notEqual"),
        LESS_THAN("lessThan"),
        LESS_THAN_EQ("lessThanEq"),
        GREATER_THAN("greaterThan"),
        GREATER_THAN_EQ("greaterThanEq"),
        LAND("land"),
        OR("or"),
        SIN("sin"),
        COS("cos"),
        FLOOR("floor"),
        CEIL("ceil"),
        ROUND("round");

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
        LESS_THAN("lessThan"),
        GREATER_THAN_EQ("greaterThanEq"),
        GREATER_THAN("greaterThan"),
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

    static final class Label {
        private final String value;
        private int address = -1;

        Label(String value) {
            if (value == null || !value.matches("[_A-Za-z][_A-Za-z0-9]*")) {
                throw new IllegalArgumentException("invalid mlog label: " + value);
            }
            this.value = value;
        }

        String value() { return value; }

        void bind(int address) {
            if (this.address >= 0) throw new IllegalStateException("mlog label already bound: " + value);
            this.address = address;
        }

        int address() {
            if (address < 0) throw new IllegalStateException("mlog label not bound: " + value);
            return address;
        }
    }

    private sealed interface Instruction permits LabelInstruction, SetInstruction, PrintInstruction,
        PrintCharInstruction, PrintFlushInstruction, DrawInstruction, DrawFlushInstruction, JumpInstruction,
        UnitBindInstruction, SensorInstruction,
        ReadInstruction, WriteInstruction, LabelAddressWriteInstruction, OperationInstruction, UnitControlInstruction,
        BuildingControlInstruction, CounterJumpInstruction,
        StopInstruction {
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

    private record CounterJumpInstruction(Label target) implements Instruction {
        @Override public String render() {
            return "set @counter " + target.address();
        }
    }

    private record PrintInstruction(String value) implements Instruction {
        @Override public String render() {
            return "print " + value;
        }
    }

    private record PrintCharInstruction(String value) implements Instruction {
        @Override public String render() {
            return "printchar " + value;
        }
    }

    private record PrintFlushInstruction(String target) implements Instruction {
        @Override public String render() {
            return "printflush " + target;
        }
    }

    private record DrawInstruction(String command, List<String> arguments) implements Instruction {
        @Override public String render() { return "draw " + command + " " + String.join(" ", arguments); }
    }

    private record DrawFlushInstruction(String target) implements Instruction {
        @Override public String render() { return "drawflush " + target; }
    }

    private record ReadInstruction(String result, String memory, String index) implements Instruction {
        @Override public String render() { return "read " + result + " " + memory + " " + index; }
    }

    private record WriteInstruction(String value, String memory, String index) implements Instruction {
        @Override public String render() { return "write " + value + " " + memory + " " + index; }
    }

    private record LabelAddressWriteInstruction(Label value, String memory, String index) implements Instruction {
        @Override public String render() { return "write " + value.address() + " " + memory + " " + index; }
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

    private record BuildingControlInstruction(String target, String action, List<String> arguments) implements Instruction {
        @Override public String render() {
            return "control " + target + " " + action + " " + String.join(" ", arguments);
        }
    }

    private enum StopInstruction implements Instruction {
        INSTANCE;

        @Override public String render() {
            return "stop";
        }
    }
}
