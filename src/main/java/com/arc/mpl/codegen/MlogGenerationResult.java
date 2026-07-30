package com.arc.mpl.codegen;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Target text plus exact per-function emission deltas from the same code-generation pass. */
public record MlogGenerationResult(String mlog, Map<String, FunctionMetrics> functions) {
    public MlogGenerationResult {
        Objects.requireNonNull(mlog, "mlog");
        functions = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(
            Objects.requireNonNull(functions, "functions")));
    }

    public record FunctionMetrics(int instructions, int labels) {
        public FunctionMetrics {
            if (instructions < 0 || labels < 0) {
                throw new IllegalArgumentException("函数 mlog 度量不得为负数");
            }
        }
    }
}
