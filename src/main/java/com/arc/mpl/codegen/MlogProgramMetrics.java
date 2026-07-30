package com.arc.mpl.codegen;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Parsed target-program metrics shared by validation, runtime planning and optimization reporting. */
public record MlogProgramMetrics(int instructions, int labels, int maxTokensPerStatement,
                                 List<Line> lines) {
    public MlogProgramMetrics {
        if (instructions < 0 || labels < 0 || maxTokensPerStatement < 0) {
            throw new IllegalArgumentException("mlog metrics must not be negative");
        }
        lines = List.copyOf(Objects.requireNonNull(lines, "lines"));
    }

    public static MlogProgramMetrics analyze(String mlog) {
        Objects.requireNonNull(mlog, "mlog");
        int instructions = 0;
        int labels = 0;
        int maximumTokens = 0;
        List<Line> lines = new ArrayList<>();
        String[] sourceLines = mlog.split("\\R", -1);
        for (int index = 0; index < sourceLines.length; index++) {
            Line line = tokenize(index + 1, sourceLines[index]);
            lines.add(line);
            maximumTokens = Math.max(maximumTokens, line.tokens());
            if (line.tokens() == 0 || line.unterminatedString()) continue;
            if (line.label()) labels++;
            else instructions++;
        }
        return new MlogProgramMetrics(instructions, labels, maximumTokens, lines);
    }

    private static Line tokenize(int number, String source) {
        int tokens = 0;
        boolean inToken = false;
        boolean labelCandidate = false;

        for (int index = 0; index < source.length(); index++) {
            char character = source.charAt(index);
            if (character == '#') break;
            if (Character.isWhitespace(character)) {
                inToken = false;
                continue;
            }
            if (!inToken) {
                tokens++;
                inToken = true;
                labelCandidate = tokens == 1;
            }
            if (character != '"') continue;
            boolean closed = false;
            for (index++; index < source.length(); index++) {
                char quoted = source.charAt(index);
                if (quoted == '\\') {
                    index++;
                    continue;
                }
                if (quoted == '"') {
                    closed = true;
                    break;
                }
            }
            if (!closed) return new Line(number, tokens, false, true);
            inToken = true;
            labelCandidate = false;
        }

        String code = source.substring(0, commentStart(source)).strip();
        boolean label = tokens == 1 && labelCandidate && code.endsWith(":");
        return new Line(number, tokens, label, false);
    }

    private static int commentStart(String source) {
        boolean quoted = false;
        for (int index = 0; index < source.length(); index++) {
            char character = source.charAt(index);
            if (character == '\\' && quoted) {
                index++;
            } else if (character == '"') {
                quoted = !quoted;
            } else if (character == '#' && !quoted) {
                return index;
            }
        }
        return source.length();
    }

    public record Line(int number, int tokens, boolean label, boolean unterminatedString) {
        public Line {
            if (number < 1 || tokens < 0) throw new IllegalArgumentException("invalid mlog line metrics");
        }
    }
}
