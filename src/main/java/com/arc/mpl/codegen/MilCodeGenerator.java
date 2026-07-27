package com.arc.mpl.codegen;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Serializes the lowered instruction stream as the compiler's macro
 * intermediate language (MIL).
 *
 * <p>MIL deliberately names target operations as {@code @logic.*} macros
 * instead of exposing bare mlog opcodes. The target profile expands these
 * macros to the instruction stream passed to the Mindustry processor. This
 * serializer consumes compiler-generated mlog, whose small instruction subset
 * is already structurally validated by {@link MlogCodeGenerator}; it is not a
 * parser for arbitrary hand-written mlog.</p>
 */
public final class MilCodeGenerator {
    /**
     * Converts a generated mlog program into its inspectable MIL form.
     *
     * @param mlog compiler-generated mlog instructions
     * @return deterministic MIL text, including a trailing newline
     * @throws IllegalArgumentException when the input contains an instruction
     *                                  outside the implemented lowering subset
     */
    public String generate(String mlog) {
        Objects.requireNonNull(mlog, "mlog");
        List<String> lines = new ArrayList<>();
        lines.add("// 由 MPL 自动生成的 MIL；请通过 mpl build 重新生成，勿直接编辑。");
        lines.add("// @logic.* 由所选 target profile 展开为游戏 mlog 指令。");

        for (String line : mlog.split("\\R", -1)) {
            if (line.isBlank()) continue;
            lines.add(emitInstruction(line));
        }
        return String.join("\n", lines) + "\n";
    }

    private String emitInstruction(String line) {
        if (line.endsWith(":")) {
            String label = line.substring(0, line.length() - 1);
            requireIdentifier(label, "标签");
            return macro("label", List.of(label));
        }

        List<String> tokens = tokenize(line);
        if (tokens.isEmpty()) {
            throw new IllegalArgumentException("空 mlog 指令");
        }
        return switch (tokens.get(0)) {
            case "set" -> macroWithArity("set", tokens, 3);
            case "print" -> macroWithArity("print", tokens, 2);
            case "printflush" -> macroWithArity("printFlush", tokens, 2);
            case "jump" -> macroWithArity("jump", tokens, 5);
            case "ubind" -> macroWithArity("unitBind", tokens, 2);
            case "sensor" -> macroWithArity("sensor", tokens, 4);
            case "op" -> macroWithArity("op", tokens, 5);
            case "ucontrol" -> macroWithArity("unitControl", tokens, 7);
            case "stop" -> {
                requireArity(tokens, 1);
                yield macro("stop", List.of());
            }
            default -> throw new IllegalArgumentException("MIL 尚不能序列化 mlog 指令：" + tokens.get(0));
        };
    }

    private String macroWithArity(String name, List<String> tokens, int arity) {
        requireArity(tokens, arity);
        return macro(name, tokens.subList(1, tokens.size()));
    }

    private String macro(String name, List<String> arguments) {
        return "@logic." + name + "(" + String.join(", ", arguments) + ");";
    }

    /** Splits a generated mlog line without changing quoted string literals. */
    private List<String> tokenize(String line) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        boolean escaped = false;
        for (int index = 0; index < line.length(); index++) {
            char character = line.charAt(index);
            if (quoted) {
                current.append(character);
                if (escaped) {
                    escaped = false;
                } else if (character == '\\') {
                    escaped = true;
                } else if (character == '"') {
                    quoted = false;
                }
                continue;
            }
            if (character == '"') {
                quoted = true;
                current.append(character);
            } else if (Character.isWhitespace(character)) {
                if (!current.isEmpty()) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(character);
            }
        }
        if (quoted) {
            throw new IllegalArgumentException("生成的 mlog 含有未闭合字符串：" + line);
        }
        if (!current.isEmpty()) tokens.add(current.toString());
        return List.copyOf(tokens);
    }

    private void requireArity(List<String> tokens, int expected) {
        if (tokens.size() != expected) {
            throw new IllegalArgumentException("生成的 mlog 指令参数数量错误：" + String.join(" ", tokens));
        }
    }

    private void requireIdentifier(String value, String role) {
        if (!value.matches("[_A-Za-z][_A-Za-z0-9]*")) {
            throw new IllegalArgumentException(role + "不是有效的 MIL 标识符：" + value);
        }
    }
}
