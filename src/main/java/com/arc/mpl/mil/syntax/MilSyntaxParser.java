package com.arc.mpl.mil.syntax;

import com.arc.mpl.diagnostic.Diagnostic;
import com.arc.mpl.diagnostic.Diagnostic.SourceSpan;
import com.arc.mpl.diagnostic.Severity;
import com.arc.mpl.profile.TargetProfile;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Independent ANTLR entry for structured, profile-restricted MIL source. */
public final class MilSyntaxParser {
    public MilParseResult parse(String source, Path file, TargetProfile profile, MilSourceKind sourceKind) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        SyntaxErrorListener errors = new SyntaxErrorListener(file, diagnostics);
        MilLexer lexer = new MilLexer(CharStreams.fromString(source));
        lexer.removeErrorListeners();
        lexer.addErrorListener(errors);

        MilParser parser = new MilParser(new CommonTokenStream(lexer));
        parser.removeErrorListeners();
        parser.addErrorListener(errors);
        MilParser.ProgramContext program = parser.program();
        if (!diagnostics.isEmpty()) return new MilParseResult(Optional.empty(), diagnostics);

        Collector collector = new Collector();
        collector.visit(program);
        MilDocument document = new MilDocument(collector.macroCalls, collector.gameSymbols);
        diagnostics.addAll(validateMacros(document, file, profile, sourceKind));
        return new MilParseResult(diagnostics.isEmpty() ? Optional.of(document) : Optional.empty(), diagnostics);
    }

    private List<Diagnostic> validateMacros(MilDocument document, Path file, TargetProfile profile,
                                            MilSourceKind sourceKind) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        for (MilDocument.MacroCall call : document.macroCalls()) {
            Optional<TargetProfile.Macro> declaration = profile.macro(call.name());
            if (declaration.isEmpty()) {
                diagnostics.add(error("MIL3001", "target " + profile.id() + " 未声明 MIL 宏 " + call.name(), file, call.span()));
                continue;
            }
            TargetProfile.Macro macro = declaration.orElseThrow();
            if (sourceKind == MilSourceKind.USER
                && macro.visibility() == TargetProfile.MacroVisibility.RUNTIME_PRIVATE) {
                diagnostics.add(error("MIL3002", "MIL 宏 " + call.name() + " 仅供编译器 runtime 使用", file, call.span()));
            }
            int fixedParameters = macro.parameters().size();
            boolean variadic = fixedParameters > 0
                && macro.parameters().get(fixedParameters - 1).type().endsWith("[]");
            int minimum = variadic ? fixedParameters - 1 : fixedParameters;
            if (call.argumentCount() < minimum || (!variadic && call.argumentCount() != fixedParameters)) {
                String expected = variadic ? "至少 " + minimum : Integer.toString(fixedParameters);
                diagnostics.add(error("MIL3003", "MIL 宏 " + call.name() + " 需要 " + expected
                    + " 个参数，实际为 " + call.argumentCount(), file, call.span()));
            }
            boolean bodyRequired = macro.body() == TargetProfile.MacroBody.REQUIRED;
            if (call.hasBody() != bodyRequired) {
                diagnostics.add(error("MIL3004", "MIL 宏 " + call.name()
                    + (bodyRequired ? " 必须带结构化代码块" : " 不能带结构化代码块"), file, call.span()));
            }
        }
        return List.copyOf(diagnostics);
    }

    private Diagnostic error(String code, String message, Path file, SourceSpan span) {
        return new Diagnostic(Severity.ERROR, code, message, Optional.ofNullable(file), Optional.of(span));
    }

    private static SourceSpan span(Token start, Token stop) {
        int endColumn = stop.getCharPositionInLine() + Math.max(1, stop.getText().length()) + 1;
        return new SourceSpan(start.getLine(), start.getCharPositionInLine() + 1,
            stop.getLine(), endColumn);
    }

    private static final class Collector extends MilParserBaseVisitor<Void> {
        private final List<MilDocument.MacroCall> macroCalls = new ArrayList<>();
        private final List<MilDocument.GameSymbol> gameSymbols = new ArrayList<>();

        @Override
        public Void visitMacroInvocation(MilParser.MacroInvocationContext context) {
            boolean hasBody = context.getParent() instanceof MilParser.MacroBlockStatementContext;
            macroCalls.add(new MilDocument.MacroCall(context.macroName().getText(), context.expression().size(),
                hasBody, span(context.getStart(), context.getStop())));
            return super.visitMacroInvocation(context);
        }

        @Override
        public Void visitGameSymbol(MilParser.GameSymbolContext context) {
            gameSymbols.add(new MilDocument.GameSymbol(context.IDENTIFIER().getText(),
                span(context.getStart(), context.getStop())));
            return null;
        }
    }

    private static final class SyntaxErrorListener extends BaseErrorListener {
        private final Path file;
        private final List<Diagnostic> diagnostics;

        private SyntaxErrorListener(Path file, List<Diagnostic> diagnostics) {
            this.file = file;
            this.diagnostics = diagnostics;
        }

        @Override
        public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line,
                                int charPositionInLine, String message, RecognitionException exception) {
            SourceSpan span = new SourceSpan(line, charPositionInLine + 1, line, charPositionInLine + 2);
            diagnostics.add(new Diagnostic(Severity.ERROR, "MIL2001", "MIL 语法错误：" + message,
                Optional.ofNullable(file), Optional.of(span)));
        }
    }
}
