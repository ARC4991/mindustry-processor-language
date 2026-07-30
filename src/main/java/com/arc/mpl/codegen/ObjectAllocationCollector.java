package com.arc.mpl.codegen;

import com.arc.mpl.hir.HirAggregateIteration;
import com.arc.mpl.hir.HirArrayLiteral;
import com.arc.mpl.hir.HirAssignment;
import com.arc.mpl.hir.HirBinary;
import com.arc.mpl.hir.HirBlock;
import com.arc.mpl.hir.HirBuildingControl;
import com.arc.mpl.hir.HirBuildingIteration;
import com.arc.mpl.hir.HirBuildingQuery;
import com.arc.mpl.hir.HirBuildingQueryGet;
import com.arc.mpl.hir.HirBuildingQuerySize;
import com.arc.mpl.hir.HirCollectionContains;
import com.arc.mpl.hir.HirCollectionLiteral;
import com.arc.mpl.hir.HirCollectionSet;
import com.arc.mpl.hir.HirDoWhile;
import com.arc.mpl.hir.HirDraw;
import com.arc.mpl.hir.HirDynamicCollectionSet;
import com.arc.mpl.hir.HirDynamicIndexAccess;
import com.arc.mpl.hir.HirExpression;
import com.arc.mpl.hir.HirExpressionStatement;
import com.arc.mpl.hir.HirFor;
import com.arc.mpl.hir.HirFunctionCall;
import com.arc.mpl.hir.HirIf;
import com.arc.mpl.hir.HirIndexAccess;
import com.arc.mpl.hir.HirIntrinsicCall;
import com.arc.mpl.hir.HirMemberAccess;
import com.arc.mpl.hir.HirNewObject;
import com.arc.mpl.hir.HirObjectFieldAssignment;
import com.arc.mpl.hir.HirObjectFieldRead;
import com.arc.mpl.hir.HirPrintStatement;
import com.arc.mpl.hir.HirProgram;
import com.arc.mpl.hir.HirReturn;
import com.arc.mpl.hir.HirStatement;
import com.arc.mpl.hir.HirStringComparison;
import com.arc.mpl.hir.HirStringConcat;
import com.arc.mpl.hir.HirStringLength;
import com.arc.mpl.hir.HirStringSnapshot;
import com.arc.mpl.hir.HirTupleLiteral;
import com.arc.mpl.hir.HirUnary;
import com.arc.mpl.hir.HirUnitControl;
import com.arc.mpl.hir.HirUnitIteration;
import com.arc.mpl.hir.HirUnitQuery;
import com.arc.mpl.hir.HirUnitQueryGet;
import com.arc.mpl.hir.HirUnitQuerySize;
import com.arc.mpl.hir.HirVariableDeclaration;
import com.arc.mpl.hir.HirWhile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Collects every statically identified object allocation point needed by field-dispatch lowering. */
final class ObjectAllocationCollector {
    Map<String, List<Integer>> collect(HirProgram program) {
        Map<String, List<Integer>> found = new LinkedHashMap<>();
        program.statements().forEach(statement -> collect(statement, found));
        program.functions().forEach(function -> function.body().forEach(statement -> collect(statement, found)));
        found.replaceAll((name, ids) -> ids.stream().sorted().toList());
        return Collections.unmodifiableMap(found);
    }

    private void collect(HirStatement statement, Map<String, List<Integer>> found) {
        if (statement instanceof HirVariableDeclaration declaration) {
            collect(declaration.initializer(), found);
        } else if (statement instanceof HirExpressionStatement expression) {
            collect(expression.expression(), found);
        } else if (statement instanceof HirPrintStatement print) {
            print.arguments().forEach(argument -> collect(argument, found));
        } else if (statement instanceof HirDraw draw) {
            draw.arguments().forEach(argument -> collect(argument, found));
        } else if (statement instanceof HirBlock block) {
            block.statements().forEach(nested -> collect(nested, found));
        } else if (statement instanceof HirWhile loop) {
            collect(loop.condition(), found);
            loop.body().forEach(nested -> collect(nested, found));
        } else if (statement instanceof HirDoWhile loop) {
            loop.body().forEach(nested -> collect(nested, found));
            collect(loop.condition(), found);
        } else if (statement instanceof HirFor loop) {
            loop.declarationInitializer().ifPresent(value -> collect(value, found));
            loop.expressionInitializer().ifPresent(value -> collect(value, found));
            collect(loop.condition(), found);
            loop.update().ifPresent(value -> collect(value, found));
            loop.body().forEach(nested -> collect(nested, found));
        } else if (statement instanceof HirIf branch) {
            collect(branch.condition(), found);
            branch.thenBody().forEach(nested -> collect(nested, found));
            branch.elseBody().ifPresent(body -> body.forEach(nested -> collect(nested, found)));
        } else if (statement instanceof HirUnitIteration iteration) {
            iteration.filters().forEach(filter -> collect(filter, found));
            iteration.body().forEach(nested -> collect(nested, found));
        } else if (statement instanceof HirBuildingIteration iteration) {
            iteration.filters().forEach(filter -> collect(filter, found));
            iteration.body().forEach(nested -> collect(nested, found));
        } else if (statement instanceof HirAggregateIteration iteration) {
            iteration.body().forEach(nested -> collect(nested, found));
        } else if (statement instanceof HirUnitControl control) {
            control.arguments().forEach(argument -> collect(argument, found));
        } else if (statement instanceof HirBuildingControl control) {
            collect(control.target(), found);
            control.arguments().forEach(argument -> collect(argument, found));
        } else if (statement instanceof HirCollectionSet update) {
            collect(update.value(), found);
        } else if (statement instanceof HirDynamicCollectionSet update) {
            collect(update.index(), found);
            collect(update.value(), found);
        } else if (statement instanceof HirReturn returned) {
            returned.value().ifPresent(value -> collect(value, found));
        }
    }

    private void collect(HirExpression expression, Map<String, List<Integer>> found) {
        if (expression instanceof HirNewObject allocation) {
            if (allocation.allocationKind() == HirNewObject.AllocationKind.FIXED) {
                found.computeIfAbsent(allocation.className(), ignored -> new ArrayList<>()).add(allocation.allocationId());
            }
            allocation.arguments().forEach(argument -> collect(argument, found));
            return;
        }
        if (expression instanceof HirFunctionCall call) {
            call.arguments().forEach(argument -> collect(argument, found));
        } else if (expression instanceof com.arc.mpl.hir.HirMethodCall call) {
            collect(call.receiver(), found);
            call.arguments().forEach(argument -> collect(argument, found));
        } else if (expression instanceof HirAssignment assignment) {
            collect(assignment.value(), found);
        } else if (expression instanceof HirBinary binary) {
            collect(binary.left(), found);
            collect(binary.right(), found);
        } else if (expression instanceof HirUnary unary) {
            collect(unary.operand(), found);
        } else if (expression instanceof HirStringConcat concat) {
            collect(concat.left(), found);
            collect(concat.right(), found);
        } else if (expression instanceof HirStringLength length) {
            collect(length.value(), found);
        } else if (expression instanceof HirStringComparison comparison) {
            collect(comparison.left(), found);
            collect(comparison.right(), found);
        } else if (expression instanceof HirStringSnapshot snapshot) {
            collect(snapshot.value(), found);
        } else if (expression instanceof HirIntrinsicCall call) {
            call.arguments().forEach(argument -> collect(argument, found));
        } else if (expression instanceof HirMemberAccess member) {
            collect(member.target(), found);
        } else if (expression instanceof HirArrayLiteral array) {
            array.elements().forEach(element -> collect(element, found));
        } else if (expression instanceof HirTupleLiteral tuple) {
            tuple.elements().forEach(element -> collect(element, found));
        } else if (expression instanceof HirCollectionLiteral collection) {
            collection.elements().forEach(element -> collect(element, found));
        } else if (expression instanceof HirIndexAccess access) {
            collect(access.target(), found);
            collect(access.index(), found);
        } else if (expression instanceof HirDynamicIndexAccess access) {
            collect(access.target(), found);
            collect(access.index(), found);
        } else if (expression instanceof HirCollectionContains contains) {
            collect(contains.target(), found);
            collect(contains.candidate(), found);
        } else if (expression instanceof HirUnitQuery query) {
            query.filters().forEach(filter -> collect(filter, found));
        } else if (expression instanceof HirUnitQuerySize size) {
            size.query().filters().forEach(filter -> collect(filter, found));
        } else if (expression instanceof HirUnitQueryGet get) {
            get.query().filters().forEach(filter -> collect(filter, found));
            collect(get.index(), found);
        } else if (expression instanceof HirBuildingQuery query) {
            query.filters().forEach(filter -> collect(filter, found));
        } else if (expression instanceof HirBuildingQuerySize size) {
            size.query().filters().forEach(filter -> collect(filter, found));
        } else if (expression instanceof HirBuildingQueryGet get) {
            get.query().filters().forEach(filter -> collect(filter, found));
            collect(get.index(), found);
        } else if (expression instanceof HirObjectFieldRead read) {
            collect(read.target(), found);
        } else if (expression instanceof HirObjectFieldAssignment assignment) {
            collect(assignment.target(), found);
            collect(assignment.value(), found);
        }
    }
}
