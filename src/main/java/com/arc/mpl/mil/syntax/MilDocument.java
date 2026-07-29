package com.arc.mpl.mil.syntax;

import com.arc.mpl.ast.Program;
import com.arc.mpl.diagnostic.Diagnostic.SourceSpan;

import java.util.List;
import java.util.Objects;

/** Syntax-level MIL facts needed before typed macro expansion. */
public record MilDocument(Program program, List<MacroCall> macroCalls, List<GameSymbol> gameSymbols) {
    public MilDocument {
        Objects.requireNonNull(program, "program");
        macroCalls = List.copyOf(Objects.requireNonNull(macroCalls, "macroCalls"));
        gameSymbols = List.copyOf(Objects.requireNonNull(gameSymbols, "gameSymbols"));
    }

    public record MacroCall(String name, int argumentCount, boolean hasBody, SourceSpan span) {
        public MacroCall {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(span, "span");
        }
    }

    public record GameSymbol(String name, SourceSpan span) {
        public GameSymbol {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(span, "span");
        }
    }
}
