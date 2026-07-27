package com.arc.mpl.codegen;

import com.arc.mpl.diagnostic.Diagnostic;
import com.arc.mpl.profile.TargetProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Checks target-parser limits after all lowering and macro expansion have completed. */
public final class MlogOutputValidator {
    public List<Diagnostic> validate(String mlog, TargetProfile profile) {
        Objects.requireNonNull(mlog, "mlog");
        Objects.requireNonNull(profile, "profile");

        List<Diagnostic> diagnostics = new ArrayList<>();
        int instructions = 0;
        int labels = 0;
        String[] lines = mlog.split("\\R", -1);

        for (int index = 0; index < lines.length; index++) {
            Tokenization tokenization = tokenize(lines[index]);
            if (tokenization.unterminatedString()) {
                diagnostics.add(Diagnostic.error("MPL5004",
                    "生成的 mlog 第 " + (index + 1) + " 行包含未闭合字符串"));
                continue;
            }
            int tokens = tokenization.tokens();
            if (tokens == 0) continue;
            if (tokens > profile.maxTokensPerStatement()) {
                diagnostics.add(Diagnostic.error("MPL5003",
                    "生成的 mlog 第 " + (index + 1) + " 行有 " + tokens + " 个 token，"
                        + "超过 target " + profile.id() + " 的 " + profile.maxTokensPerStatement() + " 个上限"));
            }
            if (tokenization.label()) {
                labels++;
            } else {
                instructions++;
            }
        }

        if (instructions > profile.maxInstructions()) {
            diagnostics.add(Diagnostic.error("MPL5001",
                "生成的 mlog 有 " + instructions + " 条指令，超过 target " + profile.id()
                    + " 的 " + profile.maxInstructions() + " 条上限"));
        }
        if (labels > profile.maxJumpLabels()) {
            diagnostics.add(Diagnostic.error("MPL5002",
                "生成的 mlog 有 " + labels + " 个跳转标签，超过 target " + profile.id()
                    + " 的 " + profile.maxJumpLabels() + " 个上限"));
        }
        return List.copyOf(diagnostics);
    }

    private Tokenization tokenize(String line) {
        int tokens = 0;
        boolean inToken = false;
        boolean labelCandidate = false;

        for (int index = 0; index < line.length(); index++) {
            char character = line.charAt(index);
            if (character == '#') {
                break;
            }
            if (Character.isWhitespace(character)) {
                inToken = false;
                continue;
            }
            if (!inToken) {
                tokens++;
                inToken = true;
                labelCandidate = tokens == 1;
            }
            if (character == '"') {
                boolean closed = false;
                for (index++; index < line.length(); index++) {
                    char quoted = line.charAt(index);
                    if (quoted == '\\') {
                        index++;
                        continue;
                    }
                    if (quoted == '"') {
                        closed = true;
                        break;
                    }
                }
                if (!closed) return new Tokenization(tokens, false, true);
                inToken = true;
                labelCandidate = false;
            }
        }

        String trimmed = line.strip();
        boolean label = tokens == 1 && labelCandidate && trimmed.endsWith(":");
        return new Tokenization(tokens, label, false);
    }

    private record Tokenization(int tokens, boolean label, boolean unterminatedString) {
    }
}
