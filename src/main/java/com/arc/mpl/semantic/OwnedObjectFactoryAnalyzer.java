package com.arc.mpl.semantic;

import com.arc.mpl.ast.BlockStatement;
import com.arc.mpl.ast.DoWhileStatement;
import com.arc.mpl.ast.ForEachStatement;
import com.arc.mpl.ast.ForStatement;
import com.arc.mpl.ast.IfStatement;
import com.arc.mpl.ast.MilMacroBlockStatement;
import com.arc.mpl.ast.NewExpression;
import com.arc.mpl.ast.ReturnStatement;
import com.arc.mpl.ast.Statement;
import com.arc.mpl.ast.WhileStatement;

/** Recognizes functions whose every value return creates and transfers one fresh object. */
final class OwnedObjectFactoryAnalyzer {
    boolean returnsFreshObject(BlockStatement body) {
        ReturnSummary summary = summarize(body);
        return summary.returns() > 0 && !summary.hasNonFreshReturn();
    }

    private ReturnSummary summarize(BlockStatement block) {
        ReturnSummary result = ReturnSummary.NONE;
        for (Statement statement : block.statements()) result = result.merge(summarize(statement));
        return result;
    }

    private ReturnSummary summarize(Statement statement) {
        if (statement instanceof ReturnStatement returned) {
            boolean fresh = returned.value().orElse(null) instanceof NewExpression;
            return new ReturnSummary(1, !fresh);
        }
        if (statement instanceof BlockStatement block) return summarize(block);
        if (statement instanceof IfStatement branch) {
            ReturnSummary result = summarize(branch.thenBlock());
            return branch.elseBranch().map(this::summarize).map(result::merge).orElse(result);
        }
        if (statement instanceof WhileStatement loop) return summarize(loop.body());
        if (statement instanceof DoWhileStatement loop) return summarize(loop.body());
        if (statement instanceof ForStatement loop) return summarize(loop.body());
        if (statement instanceof ForEachStatement loop) return summarize(loop.body());
        if (statement instanceof MilMacroBlockStatement macro) return summarize(macro.body());
        return ReturnSummary.NONE;
    }

    private record ReturnSummary(int returns, boolean hasNonFreshReturn) {
        private static final ReturnSummary NONE = new ReturnSummary(0, false);

        private ReturnSummary merge(ReturnSummary other) {
            return new ReturnSummary(returns + other.returns, hasNonFreshReturn || other.hasNonFreshReturn);
        }
    }
}
