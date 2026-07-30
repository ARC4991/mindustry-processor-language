package com.arc.mpl.codegen;

import com.arc.mpl.hir.HirAssignment;
import com.arc.mpl.hir.HirAggregateIteration;
import com.arc.mpl.hir.HirArrayLiteral;
import com.arc.mpl.hir.HirBinary;
import com.arc.mpl.hir.HirBlock;
import com.arc.mpl.hir.HirBuildingControl;
import com.arc.mpl.hir.HirBuildingIteration;
import com.arc.mpl.hir.HirBuildingQuery;
import com.arc.mpl.hir.HirBuildingQueryGet;
import com.arc.mpl.hir.HirBuildingQuerySize;
import com.arc.mpl.hir.HirBreak;
import com.arc.mpl.hir.HirCollectionContains;
import com.arc.mpl.hir.HirCollectionLiteral;
import com.arc.mpl.hir.HirCollectionSet;
import com.arc.mpl.hir.HirContinue;
import com.arc.mpl.hir.HirDoWhile;
import com.arc.mpl.hir.HirDraw;
import com.arc.mpl.hir.HirDrawFlush;
import com.arc.mpl.hir.HirDynamicCollectionSet;
import com.arc.mpl.hir.HirDynamicIndexAccess;
import com.arc.mpl.hir.HirHardwareLink;
import com.arc.mpl.hir.HirConstant;
import com.arc.mpl.hir.HirExpression;
import com.arc.mpl.hir.HirExpressionStatement;
import com.arc.mpl.hir.HirFor;
import com.arc.mpl.hir.HirFunction;
import com.arc.mpl.hir.HirFunctionCall;
import com.arc.mpl.hir.HirIntrinsicCall;
import com.arc.mpl.hir.HirIndexAccess;
import com.arc.mpl.hir.HirIf;
import com.arc.mpl.hir.HirMemberAccess;
import com.arc.mpl.hir.HirMethodCall;
import com.arc.mpl.hir.HirClass;
import com.arc.mpl.hir.HirNewObject;
import com.arc.mpl.hir.HirObjectFieldAssignment;
import com.arc.mpl.hir.HirObjectFieldRead;
import com.arc.mpl.hir.HirObjectRelease;
import com.arc.mpl.hir.HirPrintStatement;
import com.arc.mpl.hir.HirProgram;
import com.arc.mpl.hir.HirReturn;
import com.arc.mpl.hir.HirStatement;
import com.arc.mpl.hir.HirStringComparison;
import com.arc.mpl.hir.HirStringConcat;
import com.arc.mpl.hir.HirStringLength;
import com.arc.mpl.hir.HirStringSnapshot;
import com.arc.mpl.hir.HirText;
import com.arc.mpl.hir.HirTupleLiteral;
import com.arc.mpl.hir.HirUnary;
import com.arc.mpl.hir.HirUnitControl;
import com.arc.mpl.hir.HirUnitIteration;
import com.arc.mpl.hir.HirUnitQuery;
import com.arc.mpl.hir.HirUnitQueryGet;
import com.arc.mpl.hir.HirUnitQuerySize;
import com.arc.mpl.hir.HirVariable;
import com.arc.mpl.hir.HirVariableDeclaration;
import com.arc.mpl.hir.HirWhile;
import com.arc.mpl.hir.MplType;
import com.arc.mpl.hir.BuildingType;
import com.arc.mpl.hir.UnitType;
import com.arc.mpl.hir.ValueType;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Serializes validated HIR as structured macro intermediate language (MIL).
 *
 * <p>MIL is deliberately not a spelling of already-expanded mlog. Ordinary
 * declarations, expressions, and structured control flow remain source-like
 * so that a generated file is useful both for inspection and as the model for
 * the future MIL front end. Only operations that need a target-level binding
 * are lowered to profile-controlled macros: Unit traversal/control/property
 * reads and hardware output.</p>
 */
public final class MilCodeGenerator {
    private Map<String, HirFunction> functions = Map.of();
    /** Internal function ids are deliberately unique for overloaded declarations.
     *  MIL, however, must retain the source-level overloaded name so it can be
     *  parsed and resolved again by a subsequent compiler invocation. */
    private Map<String, String> functionSourceNames = Map.of();
    private Map<String, ObjectMethod> objectMethods = Map.of();

    /**
     * Emits a deterministic, inspectable MIL program.
     *
     * <p>Top-level completion is intentionally not rendered as a source macro.
     * The target emitter appends its mandatory {@code stop} instruction after
     * this structured program has completed, just as it does for MPL.</p>
     */
    public String generate(HirProgram program) {
        Objects.requireNonNull(program, "program");
        functions = program.functions().stream().collect(java.util.stream.Collectors.toMap(
            HirFunction::name, value -> value, (left, right) -> left, LinkedHashMap::new));
        functionSourceNames = program.functions().stream().collect(java.util.stream.Collectors.toMap(
            HirFunction::name, HirFunction::sourceName, (left, right) -> left, LinkedHashMap::new));
        Map<String, ObjectMethod> methods = new LinkedHashMap<>();
        for (HirClass type : program.classes()) {
            for (HirClass.Method method : type.methods()) {
                methods.put(method.functionName(), new ObjectMethod(method));
            }
        }
        objectMethods = Map.copyOf(methods);
        Writer writer = new Writer();
        writer.line("// 由 MPL 自动生成的 MIL；请通过 mpl build 重新生成，勿直接编辑。");
        writer.line("// 普通结构保留为 MIL；@unit.* 与 @io.* 是由 target profile 展开的受限宏。");
        for (HirClass type : program.classes()) emitClass(writer, type);
        for (HirFunction function : program.functions()) {
            if (!objectMethods.containsKey(function.name())) emitFunction(writer, function);
        }
        for (HirStatement statement : program.statements()) {
            emitStatement(writer, statement);
        }
        return writer.render();
    }

    private void emitClass(Writer writer, HirClass type) {
        writer.append(type.exported() ? "export " : "").append("class ")
            .append(identifier(type.name(), "类名"));
        type.superClass().ifPresent(parent -> writer.append(" extends ").append(identifier(parent, "父类名")));
        writer.line(" {");
        writer.indent();
        for (HirClass.Field field : type.fields()) {
            writer.line(access(field.publicAccess()) + identifier(field.name(), "字段名") + ": "
                + displayType(field.type()) + ";");
        }
        for (HirClass.Method method : type.methods()) {
            HirFunction function = functions.get(method.functionName());
            if (function == null) throw new IllegalArgumentException("MIL 缺少对象方法 HIR：" + method.functionName());
            emitObjectMethod(writer, method, function);
        }
        writer.unindent();
        writer.line("}");
    }

    private void emitObjectMethod(Writer writer, HirClass.Method method, HirFunction function) {
        writer.append(access(method.publicAccess())).append("fun ")
            .append(identifier(method.sourceName(), "方法名")).append("(");
        for (int index = 1; index < function.parameters().size(); index++) {
            if (index > 1) writer.append(", ");
            var parameter = function.parameters().get(index);
            writer.append(identifier(parameter.name(), "参数名")).append(": ").append(displayType(parameter.type()));
        }
        writer.append(")");
        if (!method.constructor() && function.returnType() != ValueType.VOID) {
            writer.append(": ").append(displayType(function.returnType()));
        }
        writer.append(" ");
        emitBlock(writer, function.body());
    }

    private String access(boolean publicAccess) {
        return publicAccess ? "public " : "private ";
    }

    private void emitFunction(Writer writer, HirFunction function) {
        writer.append("fun ").append(identifier(function.sourceName(), "函数名")).append("(");
        for (int index = 0; index < function.parameters().size(); index++) {
            if (index > 0) writer.append(", ");
            var parameter = function.parameters().get(index);
            writer.append(identifier(parameter.name(), "参数名")).append(": ").append(displayType(parameter.type()));
        }
        writer.append(")");
        if (function.returnType() != ValueType.VOID) writer.append(": ").append(displayType(function.returnType()));
        writer.append(" ");
        emitBlock(writer, function.body());
    }

    private void emitStatement(Writer writer, HirStatement statement) {
        if (statement instanceof HirVariableDeclaration declaration) {
            writer.line((declaration.mutable() ? "var " : "val ")
                + identifier(declaration.name(), "变量") + ": " + displayType(declaration.type())
                + " = " + expression(declaration.initializer()) + ";");
            return;
        }
        if (statement instanceof HirExpressionStatement expressionStatement) {
            writer.line(expression(expressionStatement.expression()) + ";");
            return;
        }
        if (statement instanceof HirPrintStatement print) {
            emitPrint(writer, print);
            return;
        }
        if (statement instanceof HirDraw draw) {
            writer.append("@io.draw(@").append(identifier(draw.displayName(), "显示链接"))
                .append(", ").append(drawCommand(draw.command()));
            for (HirExpression argument : draw.arguments()) writer.append(", ").append(expression(argument));
            writer.line(");");
            return;
        }
        if (statement instanceof HirDrawFlush flush) {
            writer.line("@io.drawFlush(@" + identifier(flush.displayName(), "显示链接") + ");");
            return;
        }
        if (statement instanceof HirBlock block) {
            emitBlock(writer, block.statements());
            return;
        }
        if (statement instanceof HirWhile loop) {
            writer.append("while (").append(expression(loop.condition())).append(") ");
            emitBlock(writer, loop.body());
            return;
        }
        if (statement instanceof HirDoWhile loop) {
            writer.append("do ");
            emitBlock(writer, loop.body());
            writer.line("while (" + expression(loop.condition()) + ");");
            return;
        }
        if (statement instanceof HirFor loop) {
            writer.append("for (");
            if (loop.declarationInitializer().isPresent()) {
                HirVariableDeclaration initializer = loop.declarationInitializer().orElseThrow();
                writer.append(initializer.mutable() ? "var " : "val ")
                    .append(identifier(initializer.name(), "变量"))
                    .append(": ").append(displayType(initializer.type()))
                    .append(" = ").append(expression(initializer.initializer()));
            } else if (loop.expressionInitializer().isPresent()) {
                writer.append(expression(loop.expressionInitializer().orElseThrow()));
            }
            writer.append("; ").append(expression(loop.condition())).append("; ");
            loop.update().ifPresent(value -> writer.append(expression(value)));
            writer.append(") ");
            emitBlock(writer, loop.body());
            return;
        }
        if (statement instanceof HirIf branch) {
            writer.append("if (").append(expression(branch.condition())).append(") ");
            emitBlock(writer, branch.thenBody());
            if (branch.elseBody().isPresent()) {
                writer.append("else ");
                emitBlock(writer, branch.elseBody().orElseThrow());
            }
            return;
        }
        if (statement instanceof HirUnitIteration iteration) {
            emitUnitIteration(writer, iteration);
            return;
        }
        if (statement instanceof HirAggregateIteration iteration) {
            writer.append("for (var ").append(identifier(iteration.bindingName(), "遍历变量"))
                .append(" : ").append(expression(iteration.source())).append(") ");
            emitBlock(writer, iteration.body());
            return;
        }
        if (statement instanceof HirBuildingIteration iteration) {
            writer.append("@building.each(@").append(identifier(iteration.mlogType(), "建筑内容名"))
                .append(", ").append(identifier(iteration.bindingName(), "遍历变量"));
            for (HirExpression filter : iteration.filters()) {
                writer.append(", ").append(expression(filter));
            }
            writer.append(") ");
            emitBlock(writer, iteration.body());
            return;
        }
        if (statement instanceof HirUnitControl control) {
            emitUnitControl(writer, control);
            return;
        }
        if (statement instanceof HirBuildingControl control) {
            emitBuildingControl(writer, control);
            return;
        }
        if (statement instanceof HirCollectionSet update) {
            writer.line(identifier(update.target(), "Array") + ".set(" + update.index() + ", "
                + expression(update.value()) + ");");
            return;
        }
        if (statement instanceof HirDynamicCollectionSet update) {
            writer.line(identifier(update.target(), "Array") + ".set(" + expression(update.index()) + ", "
                + expression(update.value()) + ");");
            return;
        }
        if (statement instanceof HirBreak) {
            writer.line("break;");
            return;
        }
        if (statement instanceof HirContinue) {
            writer.line("continue;");
            return;
        }
        if (statement instanceof HirReturn returned) {
            writer.line(returned.value().map(value -> "return " + expression(value) + ";").orElse("return;"));
            return;
        }
        // Ownership cleanup is reconstructed by semantic analysis when this
        // structured MIL is compiled again; it is never a user-visible macro.
        if (statement instanceof HirObjectRelease) return;
        throw new IllegalArgumentException("MIL 尚不能序列化 HIR 语句：" + statement.getClass().getSimpleName());
    }

    private String drawCommand(HirDraw.Command command) {
        return switch (command) {
            case CLEAR -> "clear";
            case COLOR -> "color";
            case RECT -> "rect";
            case LINE_RECT -> "lineRect";
            case LINE -> "line";
        };
    }

    private void emitBlock(Writer writer, List<HirStatement> statements) {
        writer.line("{");
        writer.indent();
        for (HirStatement nested : statements) {
            emitStatement(writer, nested);
        }
        writer.unindent();
        writer.line("}");
    }

    /**
     * {@link HirUnitIteration} is the lowering boundary for high-level
     * {@code Unit.getAll...()}, chained {@code where}, and {@code take}.
     * Its macro owns the target's one current-unit binding and, for the
     * managed form, all private flag/runtime bookkeeping.
     */
    private void emitUnitIteration(Writer writer, HirUnitIteration iteration) {
        String macro = iteration.hasManagedLimit() ? "@unit.eachManaged" : "@unit.each";
        writer.append(macro)
            .append("(@")
            .append(identifier(iteration.mlogType(), "单位内容名"))
            .append(", ")
            .append(identifier(iteration.bindingName(), "单位绑定变量"));
        if (iteration.hasManagedLimit()) {
            writer.append(", ").append(Integer.toString(iteration.managedLimit()));
        }
        for (HirExpression filter : iteration.filters()) {
            writer.append(", ").append(expression(filter));
        }
        writer.append(") ");
        emitBlock(writer, iteration.body());
    }

    /** Emits the automatic-flush Message API using its target link alias. */
    private void emitPrint(Writer writer, HirPrintStatement print) {
        writer.append("@io.print(")
            .append(targetLink(print.linkName()));
        for (HirExpression argument : print.arguments()) {
            writer.append(", ").append(expression(argument));
        }
        writer.line(");");
    }

    /** Emits the current first-slice Unit object operation as an explicit macro. */
    private void emitUnitControl(Writer writer, HirUnitControl control) {
        if (!"move".equals(control.command()) || control.arguments().size() != 2) {
            throw new IllegalArgumentException("MIL 尚不能序列化 Unit 操作：" + control.command());
        }
        writer.append(control.storedReference() ? "@unit.refMove(" : "@unit.move(")
            .append(identifier(control.bindingName(), "单位绑定变量"));
        for (HirExpression argument : control.arguments()) {
            writer.append(", ").append(expression(argument));
        }
        writer.line(");");
    }

    /** Keeps building control target-aware in MIL instead of leaking a raw mlog instruction. */
    private void emitBuildingControl(Writer writer, HirBuildingControl control) {
        writer.append("@building.control(")
            .append(buildingTarget(control.target()))
            .append(", ").append(identifier(control.action(), "建筑控制动作"));
        for (HirExpression argument : control.arguments()) writer.append(", ").append(expression(argument));
        writer.line(");");
    }

    private String buildingTarget(HirExpression target) {
        if (target instanceof HirHardwareLink hardware) return targetLink(hardware.gameAlias());
        if (target.type() == ValueType.BUILDING
            || target.type() instanceof BuildingType building && !building.nullable()) return expression(target);
        throw new IllegalArgumentException("MIL 建筑控制目标必须是链接或 Building 绑定");
    }

    private String expression(HirExpression value) {
        if (value instanceof HirConstant constant) return constant(constant);
        if (value instanceof HirText text) return quote(text.value());
        if (value instanceof HirVariable variable) return identifier(variable.name(), "变量");
        if (value instanceof HirArrayLiteral array) return aggregateLiteral("[", "]", array.elements());
        if (value instanceof HirTupleLiteral tuple) return aggregateLiteral("(", ")", tuple.elements());
        if (value instanceof HirCollectionLiteral collection) {
            String factory = collection.type().kind() == com.arc.mpl.hir.CollectionType.Kind.LIST ? "List.of" : "Set.of";
            return aggregateLiteral(factory + "(", ")", collection.elements());
        }
        if (value instanceof HirIndexAccess access) return expression(access.target()) + "[" + expression(access.index()) + "]";
        if (value instanceof HirDynamicIndexAccess access) {
            return expression(access.target()) + "[" + expression(access.index()) + "]";
        }
        if (value instanceof HirCollectionContains contains) {
            return expression(contains.target()) + ".contains(" + expression(contains.candidate()) + ")";
        }
        if (value instanceof HirBuildingQuery query) return buildingQuery(query);
        if (value instanceof HirBuildingQuerySize size) {
            HirBuildingQuery query = size.query();
            StringBuilder result = new StringBuilder("@building.count(@")
                .append(identifier(query.mlogType(), "建筑内容名"))
                .append(", ").append(identifier(query.bindingName(), "建筑绑定变量"));
            for (HirExpression filter : query.filters()) result.append(", ").append(expression(filter));
            return result.append(')').toString();
        }
        if (value instanceof HirBuildingQueryGet get) {
            HirBuildingQuery query = get.query();
            StringBuilder result = new StringBuilder("@building.get(@")
                .append(identifier(query.mlogType(), "建筑内容名"))
                .append(", ").append(identifier(query.bindingName(), "建筑绑定变量"))
                .append(", ").append(expression(get.index()));
            for (HirExpression filter : query.filters()) result.append(", ").append(expression(filter));
            return result.append(')').toString();
        }
        if (value instanceof HirUnitQuery query) return unitQuery(query);
        if (value instanceof HirUnitQueryGet get) {
            HirUnitQuery query = get.query();
            StringBuilder result = new StringBuilder(query.hasManagedLimit() ? "@unit.getManaged(@" : "@unit.get(@")
                .append(identifier(query.mlogType(), "单位内容名"))
                .append(", ").append(identifier(query.bindingName(), "单位绑定变量"))
                .append(", ").append(expression(get.index()));
            if (query.hasManagedLimit()) result.append(", ").append(query.managedLimit());
            for (HirExpression filter : query.filters()) result.append(", ").append(expression(filter));
            return result.append(')').toString();
        }
        if (value instanceof HirUnitQuerySize size) {
            HirUnitQuery query = size.query();
            StringBuilder result = new StringBuilder(query.hasManagedLimit() ? "@unit.countManaged(@" : "@unit.count(@")
                .append(identifier(query.mlogType(), "单位内容名"))
                .append(", ").append(identifier(query.bindingName(), "单位绑定变量"));
            if (query.hasManagedLimit()) result.append(", ").append(query.managedLimit());
            for (HirExpression filter : query.filters()) result.append(", ").append(expression(filter));
            return result.append(')').toString();
        }
        if (value instanceof HirUnary unary) {
            return "(" + unary.operator() + expression(unary.operand()) + ")";
        }
        if (value instanceof HirStringConcat concat) {
            return "(" + expression(concat.left()) + " + " + expression(concat.right()) + ")";
        }
        if (value instanceof HirStringLength length) return expression(length.value()) + ".length";
        if (value instanceof HirStringSnapshot snapshot) return expression(snapshot.value());
        if (value instanceof HirStringComparison comparison) {
            return "(" + expression(comparison.left()) + (comparison.equal() ? " == " : " != ")
                + expression(comparison.right()) + ")";
        }
        if (value instanceof HirBinary binary) {
            return "(" + expression(binary.left()) + " " + binary.operator() + " " + expression(binary.right()) + ")";
        }
        if (value instanceof HirAssignment assignment) {
            return identifier(assignment.target(), "赋值目标") + " " + assignment.operator()
                + " " + expression(assignment.value());
        }
        if (value instanceof HirMemberAccess member) return unitMember(member);
        if (value instanceof HirIntrinsicCall call) return intrinsic(call);
        if (value instanceof HirFunctionCall call) {
            ObjectMethod method = objectMethods.get(call.function());
            if (method != null && !method.declaration().constructor()) {
                if (call.arguments().isEmpty()) {
                    throw new IllegalArgumentException("对象方法缺少 this 参数：" + call.function());
                }
                StringBuilder result = new StringBuilder(expression(call.arguments().get(0))).append('.')
                    .append(identifier(method.declaration().sourceName(), "方法名")).append('(');
                appendExpressions(result, call.arguments().subList(1, call.arguments().size()));
                return result.append(')').toString();
            }
            String sourceName = functionSourceNames.getOrDefault(call.function(), call.function());
            StringBuilder result = new StringBuilder(identifier(sourceName, "函数名")).append('(');
            appendExpressions(result, call.arguments());
            return result.append(')').toString();
        }
        if (value instanceof HirMethodCall call) {
            StringBuilder result = new StringBuilder();
            if (call.kind() == HirMethodCall.InvocationKind.SUPER_CONSTRUCTOR) {
                result.append("super(");
            } else if (call.kind() == HirMethodCall.InvocationKind.SUPER_METHOD) {
                result.append("super.").append(identifier(call.sourceName(), "方法名")).append('(');
            } else {
                result.append(expression(call.receiver())).append('.')
                    .append(identifier(call.sourceName(), "方法名")).append('(');
            }
            appendExpressions(result, call.arguments());
            return result.append(')').toString();
        }
        if (value instanceof HirNewObject allocation) {
            StringBuilder result = new StringBuilder("new ").append(identifier(allocation.className(), "类名"))
                .append('(');
            appendExpressions(result, allocation.arguments());
            return result.append(')').toString();
        }
        if (value instanceof HirObjectFieldRead read) {
            return expression(read.target()) + "." + identifier(read.field(), "字段名");
        }
        if (value instanceof HirObjectFieldAssignment assignment) {
            return expression(assignment.target()) + "." + identifier(assignment.field(), "字段名") + " "
                + assignment.operator() + " " + expression(assignment.value());
        }
        throw new IllegalArgumentException("MIL 尚不能序列化 HIR 表达式：" + value.getClass().getSimpleName());
    }

    private String unitQuery(HirUnitQuery query) {
        StringBuilder result = new StringBuilder("Unit.getAll")
            .append(identifier(query.unitType(), "单位类型"))
            .append("()");
        for (HirExpression filter : query.filters()) {
            result.append(".where(").append(identifier(query.bindingName(), "单位绑定变量"))
                .append(" => ").append(expression(filter)).append(')');
        }
        if (query.hasManagedLimit()) result.append(".take(").append(query.managedLimit()).append(')');
        return result.toString();
    }

    private String buildingQuery(HirBuildingQuery query) {
        StringBuilder result = new StringBuilder("Building.getAll")
            .append(identifier(query.buildingType(), "建筑类型"))
            .append("()");
        for (HirExpression filter : query.filters()) {
            result.append(".where(").append(identifier(query.bindingName(), "建筑绑定变量"))
                .append(" => ").append(expression(filter)).append(')');
        }
        return result.toString();
    }

    private String aggregateLiteral(String prefix, String suffix, List<HirExpression> elements) {
        StringBuilder result = new StringBuilder(prefix);
        appendExpressions(result, elements);
        return result.append(suffix).toString();
    }

    /**
     * Unit fields are not copied back into MIL as ordinary object members.
     * The macro whitelist can therefore keep private fields such as flag out
     * of both MPL and hand-authored MIL.
     */
    private String unitMember(HirMemberAccess member) {
        if (member.target() instanceof HirHardwareLink hardware) {
            return "@building.read(" + targetLink(hardware.gameAlias()) + ", "
                + identifier(member.member(), "建筑属性") + ")";
        }
        if (member.target().type() == ValueType.BUILDING || member.target().type() instanceof BuildingType) {
            return "@building.read(" + expression(member.target()) + ", "
                + identifier(member.member(), "建筑属性") + ")";
        }
        if (member.target().type() != ValueType.UNIT && !(member.target().type() instanceof UnitType)) {
            return expression(member.target()) + "." + identifier(member.member(), "成员名");
        }
        if ("flag".equals(member.member())) {
            throw new IllegalArgumentException("Unit.flag 是编译器私有运行时属性，不能出现在 MIL");
        }
        String unit = expression(member.target());
        boolean storedReference = member.target().type() instanceof UnitType;
        String aliveMacro = storedReference ? "@unit.refAlive" : "@unit.alive";
        String readMacro = storedReference ? "@unit.refRead" : "@unit.read";
        if ("alive".equals(member.member())) {
            return aliveMacro + "(" + unit + ")";
        }
        if ("dead".equals(member.member())) {
            return "(!" + aliveMacro + "(" + unit + "))";
        }
        return readMacro + "(" + unit + ", " + identifier(member.member(), "Unit 属性") + ")";
    }

    private String intrinsic(HirIntrinsicCall call) {
        String namespace = identifier(call.namespace(), "内建命名空间");
        String name = identifier(call.name(), "内建名称");
        if ("Clock".equals(namespace) && call.arguments().isEmpty()) {
            return namespace + "." + name;
        }
        StringBuilder result = new StringBuilder(namespace).append('.').append(name).append('(');
        appendExpressions(result, call.arguments());
        return result.append(')').toString();
    }

    private String constant(HirConstant constant) {
        if (constant.type() == ValueType.BOOL) {
            return switch (constant.mlogLiteral()) {
                case "1" -> "true";
                case "0" -> "false";
                default -> throw new IllegalArgumentException("Bool 常量不是 0 或 1：" + constant.mlogLiteral());
            };
        }
        return constant.mlogLiteral();
    }

    private void appendExpressions(StringBuilder target, List<HirExpression> arguments) {
        for (int index = 0; index < arguments.size(); index++) {
            if (index > 0) target.append(", ");
            target.append(expression(arguments.get(index)));
        }
    }

    private String displayType(MplType type) {
        if (type == ValueType.ERROR) throw new IllegalArgumentException("不能将含错误类型的 HIR 序列化为 MIL");
        return type.displayName();
    }

    /** Prefixes game-owned hardware aliases so they cannot look like MPL variables. */
    private String targetLink(String linkName) {
        String bareName = linkName.startsWith("@") ? linkName.substring(1) : linkName;
        return "@" + identifier(bareName, "硬件链接名");
    }

    private String identifier(String value, String role) {
        if (!value.matches("[_A-Za-z][_A-Za-z0-9]*")) {
            throw new IllegalArgumentException(role + "不是有效的 MIL 标识符：" + value);
        }
        return value;
    }

    private String quote(String value) {
        return "\"" + value.replace("\\", "\\\\")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
            .replace("\"", "\\\"") + "\"";
    }

    /** Small indentation-aware writer used to keep generated MIL deterministic. */
    private static final class Writer {
        private final StringBuilder text = new StringBuilder();
        private int indentation;
        private boolean lineOpen;

        Writer append(String value) {
            if (!lineOpen) writeIndentation();
            text.append(value);
            lineOpen = true;
            return this;
        }

        void line(String value) {
            append(value);
            text.append('\n');
            lineOpen = false;
        }

        void indent() {
            if (lineOpen) throw new IllegalStateException("不能在未结束的 MIL 行内增加缩进");
            indentation++;
        }

        void unindent() {
            if (lineOpen) throw new IllegalStateException("不能在未结束的 MIL 行内减少缩进");
            if (indentation == 0) throw new IllegalStateException("MIL 缩进已经在最外层");
            indentation--;
        }

        String render() {
            if (lineOpen) text.append('\n');
            return text.toString();
        }

        private void writeIndentation() {
            text.append("    ".repeat(indentation));
        }
    }

    private record ObjectMethod(HirClass.Method declaration) {
    }
}
