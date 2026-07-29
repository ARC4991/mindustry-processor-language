package com.arc.mpl.optimization;

import com.arc.mpl.hir.HirAggregateIteration;
import com.arc.mpl.hir.HirArrayLiteral;
import com.arc.mpl.hir.HirAssignment;
import com.arc.mpl.hir.HirBinary;
import com.arc.mpl.hir.HirBlock;
import com.arc.mpl.hir.HirBreak;
import com.arc.mpl.hir.HirBuildingControl;
import com.arc.mpl.hir.HirBuildingIteration;
import com.arc.mpl.hir.HirBuildingQuery;
import com.arc.mpl.hir.HirBuildingQueryGet;
import com.arc.mpl.hir.HirBuildingQuerySize;
import com.arc.mpl.hir.HirCollectionContains;
import com.arc.mpl.hir.HirCollectionLiteral;
import com.arc.mpl.hir.HirCollectionSet;
import com.arc.mpl.hir.HirConstant;
import com.arc.mpl.hir.HirContinue;
import com.arc.mpl.hir.HirDoWhile;
import com.arc.mpl.hir.HirDraw;
import com.arc.mpl.hir.HirDrawFlush;
import com.arc.mpl.hir.HirDynamicCollectionSet;
import com.arc.mpl.hir.HirDynamicIndexAccess;
import com.arc.mpl.hir.HirExpression;
import com.arc.mpl.hir.HirExpressionStatement;
import com.arc.mpl.hir.HirFor;
import com.arc.mpl.hir.HirFunction;
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
import com.arc.mpl.hir.HirTupleLiteral;
import com.arc.mpl.hir.HirUnary;
import com.arc.mpl.hir.HirUnitControl;
import com.arc.mpl.hir.HirUnitIteration;
import com.arc.mpl.hir.HirUnitQuery;
import com.arc.mpl.hir.HirUnitQueryGet;
import com.arc.mpl.hir.HirUnitQuerySize;
import com.arc.mpl.hir.HirVariableDeclaration;
import com.arc.mpl.hir.HirWhile;
import com.arc.mpl.hir.MplType;
import com.arc.mpl.hir.ValueType;
import com.arc.mpl.numeric.NumericBounds;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Pure, target-neutral simplifications applied after semantic analysis.
 *
 * <p>The optimizer deliberately does not propagate variables or simplify
 * algebraic identities. Either technique can remove a target read, assignment,
 * function call, or game-facing operation. It only evaluates expressions made
 * entirely of scalar constants and removes control flow whose condition is then
 * provably constant.</p>
 */
public final class HirOptimizer {
    private int constantFolds;
    private int eliminatedBranches;
    private int eliminatedLoops;
    private int eliminatedStatements;

    public HirOptimizationResult optimize(HirProgram input) {
        constantFolds = 0;
        eliminatedBranches = 0;
        eliminatedLoops = 0;
        eliminatedStatements = 0;
        List<HirFunction> functions = input.functions().stream().map(this::optimizeFunction).toList();
        HirProgram program = new HirProgram(input.classes(), functions, optimizeStatements(input.statements()));
        return new HirOptimizationResult(program,
            new OptimizationReport(constantFolds, eliminatedBranches, eliminatedLoops, eliminatedStatements));
    }

    private HirFunction optimizeFunction(HirFunction function) {
        return new HirFunction(function.name(), function.parameters(), function.returnType(), optimizeStatements(function.body()));
    }

    private List<HirStatement> optimizeStatements(List<HirStatement> statements) {
        List<HirStatement> result = new ArrayList<>();
        boolean terminated = false;
        for (HirStatement statement : statements) {
            if (terminated) {
                eliminatedStatements++;
                continue;
            }
            List<HirStatement> optimized = optimizeStatement(statement);
            result.addAll(optimized);
            if (!optimized.isEmpty() && isTerminal(optimized.get(optimized.size() - 1))) terminated = true;
        }
        return List.copyOf(result);
    }

    private boolean isTerminal(HirStatement statement) {
        return statement instanceof HirReturn || statement instanceof HirBreak || statement instanceof HirContinue;
    }

    private List<HirStatement> optimizeStatement(HirStatement statement) {
        if (statement instanceof HirVariableDeclaration declaration) {
            return List.of(new HirVariableDeclaration(declaration.name(), declaration.type(), declaration.mutable(),
                optimizeExpression(declaration.initializer())));
        }
        if (statement instanceof HirExpressionStatement expression) {
            return List.of(new HirExpressionStatement(optimizeExpression(expression.expression())));
        }
        if (statement instanceof HirPrintStatement print) {
            return List.of(new HirPrintStatement(print.linkName(), optimizeExpressions(print.arguments())));
        }
        if (statement instanceof HirDraw draw) {
            return List.of(new HirDraw(draw.displayName(), draw.command(), optimizeExpressions(draw.arguments())));
        }
        if (statement instanceof HirDrawFlush flush) return List.of(flush);
        if (statement instanceof HirBlock block) return List.of(new HirBlock(optimizeStatements(block.statements())));
        if (statement instanceof HirIf branch) return optimizeIf(branch);
        if (statement instanceof HirWhile loop) return optimizeWhile(loop);
        if (statement instanceof HirDoWhile loop) {
            return List.of(new HirDoWhile(optimizeStatements(loop.body()), optimizeExpression(loop.condition())));
        }
        if (statement instanceof HirFor loop) {
            Optional<HirVariableDeclaration> declaration = loop.declarationInitializer()
                .map(value -> new HirVariableDeclaration(value.name(), value.type(), value.mutable(), optimizeExpression(value.initializer())));
            return List.of(new HirFor(declaration, loop.expressionInitializer().map(this::optimizeExpression),
                optimizeExpression(loop.condition()), loop.update().map(this::optimizeExpression), optimizeStatements(loop.body())));
        }
        if (statement instanceof HirUnitIteration iteration) {
            return List.of(new HirUnitIteration(iteration.bindingName(), iteration.unitType(), iteration.mlogType(),
                optimizeExpressions(iteration.filters()), iteration.managedLimit(), iteration.managedId(),
                optimizeStatements(iteration.body())));
        }
        if (statement instanceof HirBuildingIteration iteration) {
            if (iteration.buildings().isEmpty()) {
                eliminatedStatements++;
                return List.of();
            }
            List<HirExpression> filters = optimizeExpressions(iteration.filters());
            if (filters.stream().map(this::booleanConstant).anyMatch(value -> value.filter(flag -> !flag).isPresent())) {
                eliminatedStatements++;
                return List.of();
            }
            filters = filters.stream().filter(filter -> booleanConstant(filter).filter(flag -> flag).isEmpty()).toList();
            return List.of(new HirBuildingIteration(iteration.bindingName(), iteration.buildingType(), iteration.mlogType(),
                iteration.buildings(), filters, optimizeStatements(iteration.body())));
        }
        if (statement instanceof HirAggregateIteration iteration) {
            return List.of(new HirAggregateIteration(iteration.bindingName(), iteration.source(), iteration.elementType(),
                iteration.size(), optimizeStatements(iteration.body())));
        }
        if (statement instanceof HirUnitControl control) {
            return List.of(new HirUnitControl(control.bindingName(), control.storedReference(), control.command(),
                optimizeExpressions(control.arguments())));
        }
        if (statement instanceof HirBuildingControl control) {
            return List.of(new HirBuildingControl(optimizeExpression(control.target()), control.action(),
                optimizeExpressions(control.arguments())));
        }
        if (statement instanceof HirCollectionSet update) {
            return List.of(new HirCollectionSet(update.target(), update.index(), optimizeExpression(update.value())));
        }
        if (statement instanceof HirDynamicCollectionSet update) {
            return List.of(new HirDynamicCollectionSet(update.target(), optimizeExpression(update.index()),
                optimizeExpression(update.value())));
        }
        if (statement instanceof HirReturn returned) {
            return List.of(new HirReturn(returned.value().map(this::optimizeExpression)));
        }
        return List.of(statement);
    }

    private List<HirStatement> optimizeIf(HirIf branch) {
        HirExpression condition = optimizeExpression(branch.condition());
        List<HirStatement> thenBody = optimizeStatements(branch.thenBody());
        Optional<List<HirStatement>> elseBody = branch.elseBody().map(this::optimizeStatements);
        Optional<Boolean> value = booleanConstant(condition);
        if (value.isEmpty()) return List.of(new HirIf(condition, thenBody, elseBody));
        eliminatedBranches++;
        return value.orElseThrow() ? thenBody : elseBody.orElse(List.of());
    }

    private List<HirStatement> optimizeWhile(HirWhile loop) {
        HirExpression condition = optimizeExpression(loop.condition());
        if (booleanConstant(condition).filter(value -> !value).isPresent()) {
            eliminatedLoops++;
            return List.of();
        }
        return List.of(new HirWhile(condition, optimizeStatements(loop.body())));
    }

    private List<HirExpression> optimizeExpressions(List<HirExpression> expressions) {
        return expressions.stream().map(this::optimizeExpression).toList();
    }

    private HirExpression optimizeExpression(HirExpression expression) {
        if (expression instanceof HirConstant constant && constant.type() == ValueType.INT) {
            try {
                long parsed = Long.parseLong(constant.mlogLiteral());
                long normalized = NumericBounds.saturatingInt(parsed);
                return normalized == parsed ? constant : new HirConstant(Long.toString(normalized), ValueType.INT);
            } catch (NumberFormatException ignored) {
                return constant;
            }
        }
        if (expression instanceof HirUnary unary) {
            HirExpression operand = optimizeExpression(unary.operand());
            return foldUnary(unary.operator(), operand, unary.type()).orElse(new HirUnary(unary.operator(), operand, unary.type()));
        }
        if (expression instanceof HirBinary binary) return optimizeBinary(binary);
        if (expression instanceof HirAssignment assignment) {
            return new HirAssignment(assignment.target(), assignment.operator(), optimizeExpression(assignment.value()), assignment.type());
        }
        if (expression instanceof HirMemberAccess member) {
            return new HirMemberAccess(optimizeExpression(member.target()), member.member(), member.type());
        }
        if (expression instanceof HirIntrinsicCall call) {
            return new HirIntrinsicCall(call.namespace(), call.name(), optimizeExpressions(call.arguments()), call.type());
        }
        if (expression instanceof HirFunctionCall call) {
            return new HirFunctionCall(call.function(), optimizeExpressions(call.arguments()), call.type());
        }
        if (expression instanceof HirNewObject allocation) {
            return new HirNewObject(allocation.allocationId(), allocation.className(), allocation.constructorFunction(),
                optimizeExpressions(allocation.arguments()), allocation.type());
        }
        if (expression instanceof HirObjectFieldRead read) {
            return new HirObjectFieldRead(optimizeExpression(read.target()), read.className(), read.field(), read.type());
        }
        if (expression instanceof HirObjectFieldAssignment assignment) {
            return new HirObjectFieldAssignment(optimizeExpression(assignment.target()), assignment.className(),
                assignment.field(), assignment.operator(), optimizeExpression(assignment.value()), assignment.type());
        }
        if (expression instanceof HirArrayLiteral array) {
            return new HirArrayLiteral(optimizeExpressions(array.elements()), array.type());
        }
        if (expression instanceof HirTupleLiteral tuple) {
            return new HirTupleLiteral(optimizeExpressions(tuple.elements()), tuple.type());
        }
        if (expression instanceof HirCollectionLiteral collection) {
            return new HirCollectionLiteral(optimizeExpressions(collection.elements()), collection.type());
        }
        if (expression instanceof HirIndexAccess access) {
            return new HirIndexAccess(optimizeExpression(access.target()), optimizeExpression(access.index()), access.type());
        }
        if (expression instanceof HirDynamicIndexAccess access) {
            return new HirDynamicIndexAccess(optimizeExpression(access.target()), optimizeExpression(access.index()), access.type());
        }
        if (expression instanceof HirCollectionContains contains) {
            return new HirCollectionContains(optimizeExpression(contains.target()), optimizeExpression(contains.candidate()), contains.size());
        }
        if (expression instanceof HirUnitQuery query) return optimizeUnitQuery(query);
        if (expression instanceof HirUnitQuerySize size) return new HirUnitQuerySize(optimizeUnitQuery(size.query()));
        if (expression instanceof HirUnitQueryGet get) {
            return new HirUnitQueryGet(optimizeUnitQuery(get.query()), optimizeExpression(get.index()));
        }
        if (expression instanceof HirBuildingQuery query) return optimizeBuildingQuery(query);
        if (expression instanceof HirBuildingQuerySize size) {
            return new HirBuildingQuerySize(optimizeBuildingQuery(size.query()));
        }
        if (expression instanceof HirBuildingQueryGet get) {
            return new HirBuildingQueryGet(optimizeBuildingQuery(get.query()), optimizeExpression(get.index()));
        }
        return expression;
    }

    private HirUnitQuery optimizeUnitQuery(HirUnitQuery query) {
        return new HirUnitQuery(query.bindingName(), query.unitType(), query.mlogType(),
            optimizeExpressions(query.filters()), query.managedLimit(), query.managedId());
    }

    private HirBuildingQuery optimizeBuildingQuery(HirBuildingQuery query) {
        return new HirBuildingQuery(query.bindingName(), query.buildingType(), query.mlogType(), query.buildings(),
            optimizeExpressions(query.filters()));
    }

    private HirExpression optimizeBinary(HirBinary binary) {
        HirExpression left = optimizeExpression(binary.left());
        if ("&&".equals(binary.operator())) {
            Optional<Boolean> leftValue = booleanConstant(left);
            if (leftValue.filter(value -> !value).isPresent()) return foldedBoolean(false);
            HirExpression right = optimizeExpression(binary.right());
            if (leftValue.filter(Boolean::booleanValue).isPresent()) {
                constantFolds++;
                return right;
            }
            return foldBinary(left, binary.operator(), right, binary.type()).orElse(new HirBinary(left, binary.operator(), right, binary.type()));
        }
        if ("||".equals(binary.operator())) {
            Optional<Boolean> leftValue = booleanConstant(left);
            if (leftValue.filter(Boolean::booleanValue).isPresent()) return foldedBoolean(true);
            HirExpression right = optimizeExpression(binary.right());
            if (leftValue.filter(value -> !value).isPresent()) {
                constantFolds++;
                return right;
            }
            return foldBinary(left, binary.operator(), right, binary.type()).orElse(new HirBinary(left, binary.operator(), right, binary.type()));
        }
        HirExpression right = optimizeExpression(binary.right());
        return foldBinary(left, binary.operator(), right, binary.type()).orElse(new HirBinary(left, binary.operator(), right, binary.type()));
    }

    private Optional<HirExpression> foldUnary(String operator, HirExpression operand, MplType type) {
        if (!(operand instanceof HirConstant constant)) return Optional.empty();
        try {
            if ("!".equals(operator) && constant.type() == ValueType.BOOL) return Optional.of(foldedBoolean(!booleanConstant(constant).orElseThrow()));
            if (type == ValueType.INT && ("+".equals(operator) || "-".equals(operator))) {
                long value = Long.parseLong(constant.mlogLiteral());
                return Optional.of(foldedInt("-".equals(operator) ? -value : value));
            }
            if (type == ValueType.FLOAT && ("+".equals(operator) || "-".equals(operator))) {
                double value = Double.parseDouble(constant.mlogLiteral());
                double result = "-".equals(operator) ? -value : value;
                return Optional.of(foldedFloat(result));
            }
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    private Optional<HirExpression> foldBinary(HirExpression left, String operator, HirExpression right, MplType type) {
        if (!(left instanceof HirConstant leftConstant) || !(right instanceof HirConstant rightConstant)) return Optional.empty();
        try {
            if ("&&".equals(operator) || "||".equals(operator)) {
                boolean leftValue = booleanConstant(leftConstant).orElseThrow();
                boolean rightValue = booleanConstant(rightConstant).orElseThrow();
                return Optional.of(foldedBoolean("&&".equals(operator) ? leftValue && rightValue : leftValue || rightValue));
            }
            if (type == ValueType.INT && isIntegerArithmetic(operator)) {
                long leftValue = Long.parseLong(leftConstant.mlogLiteral());
                long rightValue = Long.parseLong(rightConstant.mlogLiteral());
                Long result = switch (operator) {
                    case "+" -> leftValue + rightValue;
                    case "-" -> leftValue - rightValue;
                    case "*" -> leftValue * rightValue;
                    case "%" -> rightValue == 0 ? 0L : leftValue % rightValue;
                    default -> null;
                };
                return result == null ? Optional.empty() : Optional.of(foldedInt(result));
            }
            if (isNumeric(leftConstant.type()) && isNumeric(rightConstant.type())) {
                double leftValue = Double.parseDouble(leftConstant.mlogLiteral());
                double rightValue = Double.parseDouble(rightConstant.mlogLiteral());
                if (isComparison(operator)) return Optional.of(foldedBoolean(compare(leftValue, operator, rightValue)));
                if (type == ValueType.FLOAT && isFloatingArithmetic(operator)) {
                    if ("/".equals(operator) && rightValue == 0.0) return Optional.of(foldedFloat(0.0));
                    double result = switch (operator) {
                        case "+" -> leftValue + rightValue;
                        case "-" -> leftValue - rightValue;
                        case "*" -> leftValue * rightValue;
                        case "/" -> leftValue / rightValue;
                        default -> Double.NaN;
                    };
                    return Optional.of(foldedFloat(result));
                }
                if (("==".equals(operator) || "!=".equals(operator))) {
                    boolean equal = Double.compare(leftValue, rightValue) == 0;
                    return Optional.of(foldedBoolean("==".equals(operator) == equal));
                }
            }
            if (leftConstant.type() == ValueType.BOOL && rightConstant.type() == ValueType.BOOL
                && ("==".equals(operator) || "!=".equals(operator))) {
                boolean equal = booleanConstant(leftConstant).orElseThrow().equals(booleanConstant(rightConstant).orElseThrow());
                return Optional.of(foldedBoolean("==".equals(operator) == equal));
            }
        } catch (NumberFormatException | ArithmeticException ignored) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    private HirConstant foldedBoolean(boolean value) {
        constantFolds++;
        return new HirConstant(value ? "1" : "0", ValueType.BOOL);
    }

    private HirConstant foldedInt(long value) {
        constantFolds++;
        return new HirConstant(Long.toString(NumericBounds.saturatingInt(value)), ValueType.INT);
    }

    private HirConstant foldedFloat(double value) {
        constantFolds++;
        if (Double.isNaN(value)) return new HirConstant("0.0", ValueType.FLOAT);
        if (value == Double.POSITIVE_INFINITY) return new HirConstant(Double.toString(Double.MAX_VALUE), ValueType.FLOAT);
        if (value == Double.NEGATIVE_INFINITY) return new HirConstant(Double.toString(-Double.MAX_VALUE), ValueType.FLOAT);
        return new HirConstant(Double.toString(value), ValueType.FLOAT);
    }

    private boolean isIntegerArithmetic(String operator) {
        return "+".equals(operator) || "-".equals(operator) || "*".equals(operator) || "%".equals(operator);
    }

    private boolean isFloatingArithmetic(String operator) {
        return "+".equals(operator) || "-".equals(operator) || "*".equals(operator) || "/".equals(operator);
    }

    private boolean isComparison(String operator) {
        return "<".equals(operator) || "<=".equals(operator) || ">".equals(operator) || ">=".equals(operator);
    }

    private boolean compare(double left, String operator, double right) {
        return switch (operator) {
            case "<" -> left < right;
            case "<=" -> left <= right;
            case ">" -> left > right;
            case ">=" -> left >= right;
            default -> throw new IllegalArgumentException("not a comparison: " + operator);
        };
    }

    private boolean isNumeric(MplType type) {
        return type == ValueType.INT || type == ValueType.FLOAT;
    }

    private Optional<Boolean> booleanConstant(HirExpression expression) {
        if (!(expression instanceof HirConstant constant) || constant.type() != ValueType.BOOL) return Optional.empty();
        return switch (constant.mlogLiteral()) {
            case "0" -> Optional.of(false);
            case "1" -> Optional.of(true);
            default -> Optional.empty();
        };
    }
}
