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
        MlogProgramMetrics metrics = MlogProgramMetrics.analyze(mlog);
        for (MlogProgramMetrics.Line line : metrics.lines()) {
            if (line.unterminatedString()) {
                diagnostics.add(Diagnostic.error("MPL5004",
                    "生成的 mlog 第 " + line.number() + " 行包含未闭合字符串"));
                continue;
            }
            int tokens = line.tokens();
            if (tokens == 0) continue;
            if (tokens > profile.maxTokensPerStatement()) {
                diagnostics.add(Diagnostic.error("MPL5003",
                    "生成的 mlog 第 " + line.number() + " 行有 " + tokens + " 个 token，"
                        + "超过 target " + profile.id() + " 的 " + profile.maxTokensPerStatement() + " 个上限"));
            }
        }

        if (metrics.instructions() > profile.maxInstructions()) {
            diagnostics.add(Diagnostic.error("MPL5001",
                "生成的 mlog 有 " + metrics.instructions() + " 条指令，超过 target " + profile.id()
                    + " 的 " + profile.maxInstructions() + " 条上限"));
        }
        if (metrics.labels() > profile.maxJumpLabels()) {
            diagnostics.add(Diagnostic.error("MPL5002",
                "生成的 mlog 有 " + metrics.labels() + " 个跳转标签，超过 target " + profile.id()
                    + " 的 " + profile.maxJumpLabels() + " 个上限"));
        }
        return List.copyOf(diagnostics);
    }
}
