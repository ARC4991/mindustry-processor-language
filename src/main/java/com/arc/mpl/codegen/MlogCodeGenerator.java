package com.arc.mpl.codegen;

import com.arc.mpl.hir.HirAssignment;
import com.arc.mpl.hir.HirAggregateIteration;
import com.arc.mpl.hir.HirArrayLiteral;
import com.arc.mpl.hir.HirBinary;
import com.arc.mpl.hir.HirConstant;
import com.arc.mpl.hir.HirExpression;
import com.arc.mpl.hir.HirExpressionStatement;
import com.arc.mpl.hir.HirFor;
import com.arc.mpl.hir.HirFunction;
import com.arc.mpl.hir.HirFunctionCall;
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
import com.arc.mpl.hir.HirIntrinsicCall;
import com.arc.mpl.hir.HirIndexAccess;
import com.arc.mpl.hir.HirIf;
import com.arc.mpl.hir.HirMemberAccess;
import com.arc.mpl.hir.HirClass;
import com.arc.mpl.hir.HirNewObject;
import com.arc.mpl.hir.HirObjectFieldAssignment;
import com.arc.mpl.hir.HirObjectFieldRead;
import com.arc.mpl.hir.HirObjectRelease;
import com.arc.mpl.hir.HirProgram;
import com.arc.mpl.hir.HirReturn;
import com.arc.mpl.hir.HirPrintStatement;
import com.arc.mpl.hir.HirStatement;
import com.arc.mpl.hir.HirStringComparison;
import com.arc.mpl.hir.HirStringConcat;
import com.arc.mpl.hir.HirStringLength;
import com.arc.mpl.hir.HirStringSnapshot;
import com.arc.mpl.hir.HirUnary;
import com.arc.mpl.hir.HirText;
import com.arc.mpl.hir.HirTupleLiteral;
import com.arc.mpl.hir.HirUnitControl;
import com.arc.mpl.hir.HirUnitIteration;
import com.arc.mpl.hir.HirUnitQuery;
import com.arc.mpl.hir.HirUnitQueryGet;
import com.arc.mpl.hir.HirUnitQuerySize;
import com.arc.mpl.hir.HirVariable;
import com.arc.mpl.hir.HirVariableDeclaration;
import com.arc.mpl.hir.HirWhile;
import com.arc.mpl.hir.BuildingType;
import com.arc.mpl.hir.UnitType;
import com.arc.mpl.hir.TupleType;
import com.arc.mpl.hir.MplType;
import com.arc.mpl.memory.PhysicalMemoryLayout;
import com.arc.mpl.memory.StringRuntimeLayout;
import com.arc.mpl.memory.SharedRuntimeLayout;
import com.arc.mpl.project.RuntimeHelperPlan;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.arc.mpl.codegen.MlogProgramBuilder.JumpCondition;
import com.arc.mpl.codegen.MlogProgramBuilder.Operation;
import com.arc.mpl.codegen.MlogProgramBuilder.UnitControlCommand;

/** Deterministic baseline mlog emission for the first arithmetic HIR subset. */
public final class MlogCodeGenerator {
    private static final String FLOAT_MAX = Double.toString(Double.MAX_VALUE);
    private static final String FLOAT_MIN = Double.toString(-Double.MAX_VALUE);

    private final MlogLabelStyle labelStyle;
    private final PhysicalMemoryLayout memoryLayout;
    private final List<HardwareRequirement> hardwareRequirements;
    private final Set<String> targetCapabilities;
    private final MlogRuntimeContext runtimeContext;
    private MlogProgramBuilder output;
    private int temporaryIndex;
    private int unitIterationIndex;
    private Deque<LoopTarget> loopTargets;
    private Map<String, MlogProgramBuilder.Label> functionEntries;
    private Map<String, HirFunction> functions;
    private Map<String, Integer> functionIndexes;
    private Map<String, HirClass> classes;
    private Map<String, List<Integer>> objectAllocations;
    private Map<String, MlogProgramBuilder.Label> stringOutputLabels;
    private Set<String> globalVariables;
    private Map<String, HirHardwareLink> activeBuildingBindings;
    private Map<String, MlogGenerationResult.FunctionMetrics> functionMetrics;
    private String activeDrawTarget;
    private String currentFunction;

    /** Creates a compact release emitter, suitable for deployment to a processor. */
    public MlogCodeGenerator() {
        this(MlogLabelStyle.RELEASE, PhysicalMemoryLayout.empty(), List.of(), Set.of());
    }

    /** Creates an emitter with an explicit jump-label spelling policy. */
    public MlogCodeGenerator(MlogLabelStyle labelStyle) {
        this(labelStyle, PhysicalMemoryLayout.empty(), List.of(), Set.of());
    }

    /** Creates an emitter backed by the exact physical layout used by the runtime blueprint. */
    public MlogCodeGenerator(MlogLabelStyle labelStyle, PhysicalMemoryLayout memoryLayout) {
        this(labelStyle, memoryLayout, List.of(), Set.of());
    }

    /** Creates an emitter that waits for all manually connected hardware before entering user code. */
    public MlogCodeGenerator(MlogLabelStyle labelStyle, PhysicalMemoryLayout memoryLayout,
                             List<HardwareRequirement> hardwareRequirements) {
        this(labelStyle, memoryLayout, hardwareRequirements, Set.of());
    }

    /** Creates an emitter with the target capabilities used for profile-specific lowering. */
    public MlogCodeGenerator(MlogLabelStyle labelStyle, PhysicalMemoryLayout memoryLayout,
                             List<HardwareRequirement> hardwareRequirements, Set<String> targetCapabilities) {
        this(labelStyle, memoryLayout, hardwareRequirements, targetCapabilities, MlogRuntimeContext.singleShard());
    }

    /** Creates one shard emitter with a structured shared-Memory startup protocol. */
    public MlogCodeGenerator(MlogLabelStyle labelStyle, PhysicalMemoryLayout memoryLayout,
                             List<HardwareRequirement> hardwareRequirements, Set<String> targetCapabilities,
                             MlogRuntimeContext runtimeContext) {
        this.labelStyle = java.util.Objects.requireNonNull(labelStyle, "labelStyle");
        this.memoryLayout = java.util.Objects.requireNonNull(memoryLayout, "memoryLayout");
        this.hardwareRequirements = List.copyOf(java.util.Objects.requireNonNull(hardwareRequirements, "hardwareRequirements"));
        this.targetCapabilities = Set.copyOf(java.util.Objects.requireNonNull(targetCapabilities, "targetCapabilities"));
        this.runtimeContext = java.util.Objects.requireNonNull(runtimeContext, "runtimeContext");
        if (runtimeContext.worker() && !this.hardwareRequirements.isEmpty()) {
            throw new IllegalArgumentException("Worker shard 不能拥有外部硬件启动门");
        }
        runtimeContext.sharedRuntime().ifPresent(shared -> {
            if (!shared.header().equals(memoryLayout.allocations().get(SharedRuntimeLayout.storageKey()))) {
                throw new IllegalArgumentException("共享 Runtime header 不属于 mlog 的物理 Memory 布局");
            }
        });
    }

    public String generate(HirProgram program) {
        return generateWithMetrics(program).mlog();
    }

    /** Emits mlog and exact per-function target costs without parsing rendered labels back from text. */
    public MlogGenerationResult generateWithMetrics(HirProgram program) {
        output = new MlogProgramBuilder(labelStyle);
        temporaryIndex = 0;
        unitIterationIndex = 0;
        loopTargets = new ArrayDeque<>();
        functionEntries = new HashMap<>();
        functions = program.functions().stream().collect(java.util.stream.Collectors.toMap(
            HirFunction::name, value -> value, (left, right) -> left, java.util.LinkedHashMap::new));
        functionIndexes = new HashMap<>();
        for (int index = 0; index < program.functions().size(); index++) {
            functionIndexes.put(program.functions().get(index).name(), index);
        }
        classes = program.classes().stream().collect(java.util.stream.Collectors.toMap(
            HirClass::name, value -> value, (left, right) -> left, java.util.LinkedHashMap::new));
        objectAllocations = new ObjectAllocationCollector().collect(program);
        stringOutputLabels = new java.util.LinkedHashMap<>();
        prepareStringOutputLabels();
        globalVariables = new HashSet<>();
        activeBuildingBindings = new HashMap<>();
        functionMetrics = new java.util.LinkedHashMap<>();
        activeDrawTarget = null;
        for (HirStatement statement : program.statements()) {
            if (statement instanceof HirVariableDeclaration declaration) globalVariables.add(declaration.name());
        }
        for (HirFunction function : localFunctions(program)) {
            functionEntries.put(function.name(), label("function_" + function.name()));
        }
        currentFunction = null;
        SharedRuntimeStartupEmitter startup = new SharedRuntimeStartupEmitter(output, memoryLayout, runtimeContext);
        startup.emitPreparation();
        if (runtimeContext.main()) {
            emitStringRuntimeInitialization();
            emitObjectPoolInitialization();
        }
        startup.emitReady();
        if (runtimeContext.main()) emitHardwareStartupGate();
        if (runtimeContext.main()) {
            for (HirStatement statement : program.statements()) emitStatement(statement);
            flushPendingDraw();
            emitWorkerShutdown();
            // MPL 程序默认只执行一次；持续逻辑必须由用户显式写 while。
            output.stop();
        } else {
            emitWorkerTaskLoop();
        }
        for (HirFunction function : localFunctions(program)) {
            int instructionsBefore = output.instructionCount();
            int labelsBefore = output.labelCount();
            emitFunction(function);
            functionMetrics.put(function.name(), new MlogGenerationResult.FunctionMetrics(
                output.instructionCount() - instructionsBefore, output.labelCount() - labelsBefore));
        }
        emitStringOutputBlocks();
        return new MlogGenerationResult(output.render(), functionMetrics);
    }

    private List<HirFunction> localFunctions(HirProgram program) {
        if (!runtimeContext.helperPlan().enabled()) return program.functions();
        if (runtimeContext.main()) {
            return program.functions().stream()
                .filter(function -> runtimeContext.helperPlan().task(function.name()).isEmpty()).toList();
        }
        RuntimeHelperPlan.Worker worker = helperWorker();
        return program.functions().stream().filter(function -> worker.functions().contains(function.name())).toList();
    }

    private RuntimeHelperPlan.Worker helperWorker() {
        return runtimeContext.helperPlan().workers().stream()
            .filter(worker -> worker.id().equals(runtimeContext.shardId())).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Worker 缺少 helper 任务计划：" + runtimeContext.shardId()));
    }

    private void emitWorkerTaskLoop() {
        if (!runtimeContext.helperPlan().enabled()) {
            output.stop();
            return;
        }
        RuntimeHelperPlan.Worker worker = helperWorker();
        List<SharedRuntimeTaskEmitter.TaskHandler> handlers = worker.functions().stream().map(functionName -> {
            RuntimeHelperPlan.Task task = runtimeContext.helperPlan().task(functionName).orElseThrow();
            HirFunction function = functions.get(functionName);
            return new SharedRuntimeTaskEmitter.TaskHandler(task.kind(), payload -> {
                List<String> arguments = payload.subList(0, function.parameters().size());
                String result = emitPreparedFunctionCall(functionName, arguments, function.returnType());
                return List.of(result);
            });
        }).toList();
        new SharedRuntimeTaskEmitter(output, memoryLayout, runtimeContext)
            .emitWorkerLoop(worker.requestMailbox(), worker.responseMailbox(), handlers);
    }

    private void emitWorkerShutdown() {
        if (!runtimeContext.helperPlan().enabled()) return;
        SharedRuntimeTaskEmitter tasks = new SharedRuntimeTaskEmitter(output, memoryLayout, runtimeContext);
        for (RuntimeHelperPlan.Worker worker : runtimeContext.helperPlan().workers()) {
            tasks.emitShutdownAndAwait(worker.requestMailbox());
        }
    }

    private void emitHardwareStartupGate() {
        if (hardwareRequirements.isEmpty()) return;
        MlogProgramBuilder.Label wait = label("hardware_wait");
        emitLabel(wait);
        int requirementIndex = 0;
        for (HardwareRequirement requirement : hardwareRequirements) {
            String actualType = "__mpl_hw" + requirementIndex++;
            output.sensor(actualType, requirement.alias(), "@type");
            if (requirement.mlogBlocks().size() == 1) {
                emitJump(wait, JumpCondition.NOT_EQUAL, actualType, "@" + requirement.mlogBlocks().get(0));
                continue;
            }
            MlogProgramBuilder.Label accepted = label("hardware_ready");
            for (String mlogBlock : requirement.mlogBlocks()) {
                emitJump(accepted, JumpCondition.EQUAL, actualType, "@" + mlogBlock);
            }
            emitJump(wait, JumpCondition.ALWAYS, "0", "0");
            emitLabel(accepted);
        }
    }

    public record HardwareRequirement(String alias, List<String> mlogBlocks) {
        public HardwareRequirement {
            if (alias == null || !alias.matches("[_A-Za-z][_A-Za-z0-9]*")) {
                throw new IllegalArgumentException("无效的游戏硬件 alias：" + alias);
            }
            mlogBlocks = List.copyOf(mlogBlocks);
            if (mlogBlocks.isEmpty() || mlogBlocks.stream().anyMatch(block -> !block.matches("[a-z0-9-]+"))) {
                throw new IllegalArgumentException("无效的目标方块名称集合：" + mlogBlocks);
            }
            if (mlogBlocks.stream().distinct().count() != mlogBlocks.size()) {
                throw new IllegalArgumentException("目标方块名称集合包含重复项：" + mlogBlocks);
            }
        }

        public HardwareRequirement(String alias, String mlogBlock) {
            this(alias, List.of(mlogBlock));
        }
    }

    private void emitStatement(HirStatement statement) {
        if (statement instanceof HirVariableDeclaration declaration) {
            if (declaration.initializer() instanceof HirUnitQuery
                || declaration.initializer() instanceof HirBuildingQuery) return;
            if (declaration.initializer() instanceof HirArrayLiteral array) {
                emitAggregateDeclaration(declaration, array.elements());
                return;
            }
            if (declaration.initializer() instanceof HirTupleLiteral tuple) {
                emitAggregateDeclaration(declaration, tuple.elements());
                return;
            }
            if (declaration.initializer() instanceof HirCollectionLiteral collection) {
                emitAggregateDeclaration(declaration, collection.elements());
                return;
            }
            if (declaration.type() == com.arc.mpl.hir.ValueType.STRING) {
                StringRuntimeLayout.Entry target = stringVariable(declaration.name());
                String source = emitExpression(declaration.initializer());
                emitStringCopy(source, target);
                output.set(variable(declaration.name()), Integer.toString(target.handle()));
                return;
            }
            output.set(variable(declaration.name()), emitExpression(declaration.initializer()));
            return;
        }
        if (statement instanceof HirPrintStatement print) {
            for (HirExpression argument : print.arguments()) emitPrintValue(argument);
            output.printFlush(print.linkName());
            return;
        }
        if (statement instanceof HirDraw draw) {
            emitDraw(draw);
            return;
        }
        if (statement instanceof HirDrawFlush flush) {
            flushDrawBuffer(flush.displayName());
            return;
        }
        if (statement instanceof HirBlock block) {
            for (HirStatement nested : block.statements()) emitStatement(nested);
            return;
        }
        if (statement instanceof HirWhile loop) {
            emitWhile(loop);
            return;
        }
        if (statement instanceof HirDoWhile loop) {
            emitDoWhile(loop);
            return;
        }
        if (statement instanceof HirFor loop) {
            emitFor(loop);
            return;
        }
        if (statement instanceof HirIf branch) {
            emitIf(branch);
            return;
        }
        if (statement instanceof HirUnitIteration iteration) {
            emitUnitIteration(iteration);
            return;
        }
        if (statement instanceof HirAggregateIteration iteration) {
            emitAggregateIteration(iteration);
            return;
        }
        if (statement instanceof HirBuildingIteration iteration) {
            emitBuildingIteration(iteration);
            return;
        }
        if (statement instanceof HirUnitControl control) {
            emitUnitControl(control);
            return;
        }
        if (statement instanceof HirBuildingControl control) {
            emitBuildingControl(control);
            return;
        }
        if (statement instanceof HirCollectionSet update) {
            String value = emitExpression(update.value());
            if (update.value().type() == com.arc.mpl.hir.ValueType.STRING) {
                StringRuntimeLayout.Entry target = stringAggregateElement(update.target(), update.index());
                emitStringCopy(value, target);
                value = Integer.toString(target.handle());
            }
            storeAggregateElement(update.target(), update.index(), value);
            return;
        }
        if (statement instanceof HirDynamicCollectionSet update) {
            emitDynamicCollectionSet(update);
            return;
        }
        if (statement instanceof HirBreak) {
            emitLoopJump(true);
            return;
        }
        if (statement instanceof HirContinue) {
            emitLoopJump(false);
            return;
        }
        if (statement instanceof HirReturn returned) {
            emitReturn(returned);
            return;
        }
        if (statement instanceof HirObjectRelease release) {
            emitObjectRelease(release);
            return;
        }
        emitExpression(((HirExpressionStatement) statement).expression());
    }

    private void emitFunction(HirFunction function) {
        currentFunction = function.name();
        emitLabel(functionEntries.get(function.name()));
        for (HirStatement statement : function.body()) emitStatement(statement);
        if (function.returnType() == com.arc.mpl.hir.ValueType.VOID) {
            output.set("@counter", functionReturnSlot(function.name()));
        }
        currentFunction = null;
    }

    private void emitReturn(HirReturn returned) {
        if (currentFunction == null) throw new IllegalStateException("return 缺少函数上下文");
        returned.value().ifPresent(value -> {
            if (value.type() == com.arc.mpl.hir.ValueType.STRING) {
                StringRuntimeLayout.Entry target = memoryLayout.stringRuntime().functionResult(currentFunction)
                    .orElseThrow(() -> new IllegalArgumentException("String 函数缺少返回缓冲：" + currentFunction));
                emitStringCopy(emitExpression(value), target);
                output.set(functionResultSlot(currentFunction), Integer.toString(target.handle()));
            } else {
                output.set(functionResultSlot(currentFunction), emitExpression(value));
            }
        });
        returned.cleanup().forEach(this::emitObjectRelease);
        output.set("@counter", functionReturnSlot(currentFunction));
    }

    private void prepareStringOutputLabels() {
        if (supportsPrintChar()) return;
        for (StringRuntimeLayout.Entry entry : memoryLayout.stringRuntime().entries()) {
            if (!entry.isLiteral()) continue;
            for (String token : stringTokens(entry.literal())) {
                stringOutputLabels.computeIfAbsent(token, ignored -> label("string_unit"));
            }
        }
    }

    /** Initializes literal address sequences before user code can observe a String handle. */
    private void emitStringRuntimeInitialization() {
        for (StringRuntimeLayout.Entry entry : memoryLayout.stringRuntime().entries()) {
            writePhysicalConstant(memoryLayout.stringRuntime().bases(), entry.handle() - 1,
                Integer.toString(globalAddress(entry.allocation())));
            writePhysicalConstant(memoryLayout.stringRuntime().lengths(), entry.handle() - 1,
                Integer.toString(entry.isLiteral() ? entry.fixedLength() : 0));
            if (!entry.isLiteral()) continue;
            if (supportsPrintChar()) {
                for (int index = 0; index < entry.literal().length(); index++) {
                    writePhysicalConstant(entry.allocation(), index,
                        Integer.toString(entry.literal().charAt(index)));
                }
                continue;
            }
            List<String> tokens = stringTokens(entry.literal());
            for (int index = 0; index < tokens.size(); index++) {
                PhysicalMemoryLayout.Slice slice = constantSlice(entry.allocation(), index);
                output.writeAddress(stringOutputLabels.get(tokens.get(index)), segment(slice).alias(),
                    Integer.toString(slice.offset() + index - slice.logicalStart()));
            }
        }
    }

    /** Emits one shared output block per transport-safe UTF-16 runtime token. */
    private void emitStringOutputBlocks() {
        if (supportsPrintChar()) return;
        for (Map.Entry<String, MlogProgramBuilder.Label> entry : stringOutputLabels.entrySet()) {
            emitLabel(entry.getValue());
            if (!entry.getKey().isEmpty()) output.print(quote(entry.getKey()));
            output.set("@counter", "__mpl_string_return");
        }
    }

    /**
     * Keeps one physical slot per UTF-16 code unit. A supplementary code point
     * prints from its high-surrogate slot; its low-surrogate slot is a no-op.
     * This preserves Java/Mindustry length semantics without serializing an
     * isolated surrogate into the UTF-8 mlog artifact.
     */
    private List<String> stringTokens(String value) {
        List<String> tokens = new java.util.ArrayList<>(value.length());
        for (int index = 0; index < value.length(); index++) {
            char unit = value.charAt(index);
            if (Character.isHighSurrogate(unit) && index + 1 < value.length()
                && Character.isLowSurrogate(value.charAt(index + 1))) {
                tokens.add(value.substring(index, index + 2));
                tokens.add("");
                index++;
            } else {
                tokens.add(String.valueOf(unit));
            }
        }
        return List.copyOf(tokens);
    }

    private void emitObjectPoolInitialization() {
        for (PhysicalMemoryLayout.ObjectPool pool : memoryLayout.objectPools().values()) {
            String slot = temporary();
            output.set(slot, "0");
            MlogProgramBuilder.Label clear = label("object_pool_clear");
            emitLabel(clear);
            emitPhysicalWrite(pool.occupancy(), slot, "0");
            output.operation(Operation.ADD, slot, slot, "1");
            emitJump(clear, JumpCondition.LESS_THAN, slot, Integer.toString(pool.capacity()));
        }
    }

    private void emitObjectRelease(HirObjectRelease release) {
        PhysicalMemoryLayout.ObjectPool pool = memoryLayout.objectPool(release.className())
            .orElseThrow(() -> new IllegalArgumentException("对象释放缺少物理池：" + release.className()));
        String handle = variable(release.variable());
        String slot = pooledSlot(handle, pool);
        emitPhysicalWrite(pool.occupancy(), slot, "0");
    }

    private void emitWhile(HirWhile loop) {
        MlogProgramBuilder.Label start = label("while_start");
        MlogProgramBuilder.Label end = label("while_end");
        emitLabel(start);
        String condition = emitExpression(loop.condition());
        emitJump(end, JumpCondition.EQUAL, condition, "0");
        emitLoopBody(loop.body(), start, end);
        emitJump(start, JumpCondition.ALWAYS, "0", "0");
        emitLabel(end);
    }

    private void emitDoWhile(HirDoWhile loop) {
        MlogProgramBuilder.Label start = label("do_start");
        MlogProgramBuilder.Label condition = label("do_condition");
        MlogProgramBuilder.Label end = label("do_end");
        emitLabel(start);
        emitLoopBody(loop.body(), condition, end);
        emitLabel(condition);
        String accepted = emitExpression(loop.condition());
        emitJump(start, JumpCondition.NOT_EQUAL, accepted, "0");
        emitLabel(end);
    }

    private void emitFor(HirFor loop) {
        MlogProgramBuilder.Label condition = label("for_condition");
        MlogProgramBuilder.Label update = label("for_update");
        MlogProgramBuilder.Label end = label("for_end");
        loop.declarationInitializer().ifPresent(this::emitStatement);
        loop.expressionInitializer().ifPresent(this::emitExpression);
        emitLabel(condition);
        String accepted = emitExpression(loop.condition());
        emitJump(end, JumpCondition.EQUAL, accepted, "0");
        emitLoopBody(loop.body(), update, end);
        emitLabel(update);
        loop.update().ifPresent(this::emitExpression);
        emitJump(condition, JumpCondition.ALWAYS, "0", "0");
        emitLabel(end);
    }

    private void emitIf(HirIf branch) {
        MlogProgramBuilder.Label otherwise = branch.elseBody().isPresent() ? label("if_else") : null;
        MlogProgramBuilder.Label end = label("if_end");
        String condition = emitExpression(branch.condition());
        emitJump(otherwise == null ? end : otherwise, JumpCondition.EQUAL, condition, "0");
        for (HirStatement statement : branch.thenBody()) emitStatement(statement);
        if (branch.elseBody().isEmpty()) {
            emitLabel(end);
            return;
        }
        emitJump(end, JumpCondition.ALWAYS, "0", "0");
        emitLabel(otherwise);
        for (HirStatement statement : branch.elseBody().orElseThrow()) emitStatement(statement);
        emitLabel(end);
    }

    private void emitUnitIteration(HirUnitIteration iteration) {
        if (iteration.hasManagedLimit()) {
            emitManagedUnitIteration(iteration);
        } else {
            emitDirectUnitIteration(iteration);
        }
    }

    /**
     * Traverses the v146 per-type {@code ubind} carousel exactly once.
     *
     * <p>The first bound unit is retained as an object-valued sentinel. Before asking
     * {@code ubind @type} for the next candidate, the sentinel is rebound and checked
     * for removal. This avoids a non-terminating loop when the carousel wraps, while
     * also avoiding the unsafe assumption that a dead/removed unit reference remains
     * usable for the rest of the scan.</p>
     */
    private void emitDirectUnitIteration(HirUnitIteration iteration) {
        int iterationId = unitIterationIndex++;
        MlogProgramBuilder.Label scan = label("unit_scan");
        MlogProgramBuilder.Label next = label("unit_next");
        MlogProgramBuilder.Label end = label("unit_end");
        // Source variables are prefixed with mpl_. Keep compiler temporaries outside
        // that namespace so a perfectly ordinary user variable such as unit_sentinel0
        // can never overwrite the traversal state.
        String sentinel = "__mpl_unit_sentinel" + iterationId;

        // UnitBindI writes null when there is no controllable unit of this type.
        output.unitBind("@" + iteration.mlogType());
        emitJump(end, JumpCondition.STRICT_EQUAL, "@unit", "null");
        output.set(sentinel, "@unit");

        emitLabel(scan);
        // Separate where calls are short-circuited in source order.
        for (HirExpression filter : iteration.filters()) {
            String accepted = emitExpression(filter);
            emitJump(next, JumpCondition.EQUAL, accepted, "0");
        }
        emitLoopBody(iteration.body(), next, end);

        emitLabel(next);
        // Rebind by object rather than type: this both validates the sentinel and
        // preserves the type carousel position established by the preceding ubind.
        output.unitBind(sentinel);
        emitJump(end, JumpCondition.STRICT_EQUAL, "@unit", "null");
        String sentinelDead = temporary();
        output.sensor(sentinelDead, "@unit", "@dead");
        emitJump(end, JumpCondition.EQUAL, sentinelDead, "1");

        output.unitBind("@" + iteration.mlogType());
        emitJump(end, JumpCondition.STRICT_EQUAL, "@unit", "null");
        emitJump(end, JumpCondition.STRICT_EQUAL, "@unit", sentinel);
        emitJump(scan, JumpCondition.ALWAYS, "0", "0");
        emitLabel(end);
    }

    /**
     * Emits the private runtime behind {@code UnitSet.take(n)}.
     *
     * <p>v146 only exposes a per-type {@code ubind} carousel, so taking the
     * first {@code n} candidates on every pass would eventually command every
     * unit as that carousel rotates. Instead, the generated program owns a
     * small stable subset using the target's {@code Unit.flag} field. The
     * field is strictly compiler-private: source MPL and MIL have no access
     * to it.</p>
     *
     * <p>The first scan reconciles already-owned units before considering new
     * candidates. The second scan fills any remaining capacity and runs the
     * user body only for units carrying this traversal's owner token and
     * currently controlled by this processor. This ordering keeps an existing
     * group stable even when the target's bind cursor begins at a different
     * candidate on a later tick.</p>
     */
    private void emitManagedUnitIteration(HirUnitIteration iteration) {
        ManagedUnitQuery query = new ManagedUnitQuery(iteration.mlogType(), iteration.filters(),
            iteration.managedLimit(), iteration.managedId());
        emitManagedUnitScan(query, (next, end) -> emitLoopBody(iteration.body(), next, end));
    }

    /** Reconciles and fills one stable owner set, then visits every retained member. */
    private String emitManagedUnitScan(ManagedUnitQuery query, ManagedUnitVisitor visitor) {
        int scanId = unitIterationIndex++;
        MlogProgramBuilder.Label reconcileScan = label("managed_reconcile_scan");
        MlogProgramBuilder.Label reconcileTagged = label("managed_reconcile_tagged");
        MlogProgramBuilder.Label reconcileOwned = label("managed_reconcile_owned");
        MlogProgramBuilder.Label reconcileReject = label("managed_reconcile_reject");
        MlogProgramBuilder.Label reconcileExcess = label("managed_reconcile_excess");
        MlogProgramBuilder.Label reconcileNext = label("managed_reconcile_next");
        MlogProgramBuilder.Label reconcileEnd = label("managed_reconcile_end");
        MlogProgramBuilder.Label scan = label("managed_unit_scan");
        MlogProgramBuilder.Label owned = label("managed_unit_owned");
        MlogProgramBuilder.Label unclaimed = label("managed_unit_unclaimed");
        MlogProgramBuilder.Label claimedTagged = label("managed_unit_claimed_tagged");
        MlogProgramBuilder.Label ownedConfirmed = label("managed_unit_owned_confirmed");
        MlogProgramBuilder.Label claimed = label("managed_unit_claimed");
        MlogProgramBuilder.Label body = label("managed_unit_body");
        MlogProgramBuilder.Label next = label("managed_unit_next");
        MlogProgramBuilder.Label end = label("managed_unit_end");
        String sentinel = "__mpl_managed_sentinel" + scanId;
        String owner = "__mpl_managed_owner" + query.managedId();
        String retained = "__mpl_managed_count" + scanId;
        String limit = Integer.toString(query.limit());

        emitManagedOwnerToken(owner, query.managedId());
        output.set(retained, "0");

        // Phase A: preserve qualifying units already owned by this traversal
        // before filling the remaining capacity. A stale excess tag is released
        // so lowering remains correct after reducing take(n) and recompiling.
        output.unitBind("@" + query.mlogType());
        emitJump(reconcileEnd, JumpCondition.STRICT_EQUAL, "@unit", "null");
        output.set(sentinel, "@unit");
        emitLabel(reconcileScan);
        String currentFlag = temporary();
        output.sensor(currentFlag, "@unit", "@flag");
        emitJump(reconcileTagged, JumpCondition.STRICT_EQUAL, currentFlag, owner);
        emitJump(reconcileNext, JumpCondition.ALWAYS, "0", "0");

        emitLabel(reconcileTagged);
        String currentController = temporary();
        output.sensor(currentController, "@unit", "@controller");
        emitJump(reconcileOwned, JumpCondition.STRICT_EQUAL, currentController, "@this");
        emitJump(reconcileNext, JumpCondition.ALWAYS, "0", "0");

        emitLabel(reconcileOwned);
        emitFilterRejectionJump(query.filters(), reconcileReject);
        output.operation(Operation.ADD, retained, retained, "1");
        emitJump(reconcileNext, JumpCondition.LESS_THAN_EQ, retained, limit);

        emitLabel(reconcileExcess);
        output.unitControl(UnitControlCommand.FLAG, "0", "0", "0", "0", "0");
        output.operation(Operation.SUB, retained, retained, "1");
        emitJump(reconcileNext, JumpCondition.ALWAYS, "0", "0");

        emitLabel(reconcileReject);
        output.unitControl(UnitControlCommand.FLAG, "0", "0", "0", "0", "0");
        emitJump(reconcileNext, JumpCondition.ALWAYS, "0", "0");

        emitLabel(reconcileNext);
        emitUnitScanAdvance(sentinel, query.mlogType(), reconcileScan, reconcileEnd);
        emitLabel(reconcileEnd);

        // Phase B: only current owner-tagged units execute the MPL loop body.
        // Unflagged, qualifying candidates fill an empty slot; any foreign
        // non-zero flag is left untouched.
        output.unitBind("@" + query.mlogType());
        emitJump(end, JumpCondition.STRICT_EQUAL, "@unit", "null");
        output.set(sentinel, "@unit");
        emitLabel(scan);
        String candidateFlag = temporary();
        output.sensor(candidateFlag, "@unit", "@flag");
        emitJump(owned, JumpCondition.STRICT_EQUAL, candidateFlag, owner);
        emitJump(unclaimed, JumpCondition.STRICT_EQUAL, candidateFlag, "0");
        emitJump(next, JumpCondition.ALWAYS, "0", "0");
        emitLabel(unclaimed);
        emitJump(next, JumpCondition.GREATER_THAN_EQ, retained, limit);
        String candidateControl = temporary();
        output.sensor(candidateControl, "@unit", "@controlled");
        emitJump(next, JumpCondition.NOT_EQUAL, candidateControl, "0");
        emitFilterRejectionJump(query.filters(), next);
        output.unitControl(UnitControlCommand.FLAG, owner, "0", "0", "0", "0");
        String claimedFlag = temporary();
        output.sensor(claimedFlag, "@unit", "@flag");
        emitJump(claimedTagged, JumpCondition.STRICT_EQUAL, claimedFlag, owner);
        emitJump(next, JumpCondition.ALWAYS, "0", "0");
        emitLabel(claimedTagged);
        String claimedController = temporary();
        output.sensor(claimedController, "@unit", "@controller");
        emitJump(claimed, JumpCondition.STRICT_EQUAL, claimedController, "@this");
        emitJump(next, JumpCondition.ALWAYS, "0", "0");
        emitLabel(claimed);
        output.operation(Operation.ADD, retained, retained, "1");
        emitJump(body, JumpCondition.ALWAYS, "0", "0");

        emitLabel(owned);
        String ownedController = temporary();
        output.sensor(ownedController, "@unit", "@controller");
        emitJump(ownedConfirmed, JumpCondition.STRICT_EQUAL, ownedController, "@this");
        emitJump(next, JumpCondition.ALWAYS, "0", "0");
        emitLabel(ownedConfirmed);
        emitFilterRejectionJump(query.filters(), next);
        emitLabel(body);
        visitor.emit(next, end);

        emitLabel(next);
        emitUnitScanAdvance(sentinel, query.mlogType(), scan, end);
        emitLabel(end);
        return retained;
    }

    /** Builds a stable, numeric owner token without exposing target internals to MPL. */
    private void emitManagedOwnerToken(String owner, int iterationId) {
        // @thisx/@thisy can be half-tile coordinates for some processor sizes.
        // Doubling them produces a unique integer cell coordinate. @mapw makes
        // the two-dimensional coordinate injective within the current map; 4096
        // leaves room for distinct managed traversal sites in one program.
        String twiceX = temporary();
        String twiceY = temporary();
        String stride = temporary();
        String row = temporary();
        String cell = temporary();
        output.operation(Operation.MUL, twiceX, "@thisx", "2");
        output.operation(Operation.MUL, twiceY, "@thisy", "2");
        output.operation(Operation.MUL, stride, "@mapw", "2");
        output.operation(Operation.MUL, row, twiceY, stride);
        output.operation(Operation.ADD, cell, twiceX, row);
        output.operation(Operation.MUL, owner, cell, "4096");
        output.operation(Operation.ADD, owner, owner, "4000000000");
        output.operation(Operation.ADD, owner, owner, Integer.toString(iterationId));
    }

    private void emitFilterRejectionJump(List<HirExpression> filters, MlogProgramBuilder.Label reject) {
        for (HirExpression filter : filters) {
            String accepted = emitExpression(filter);
            emitJump(reject, JumpCondition.EQUAL, accepted, "0");
        }
    }

    /** Advances a v146 {@code ubind} carousel while safely detecting wraparound/removal. */
    private void emitUnitScanAdvance(String sentinel, String mlogType, MlogProgramBuilder.Label scan,
                                     MlogProgramBuilder.Label end) {
        output.unitBind(sentinel);
        emitJump(end, JumpCondition.STRICT_EQUAL, "@unit", "null");
        String sentinelDead = temporary();
        output.sensor(sentinelDead, "@unit", "@dead");
        emitJump(end, JumpCondition.EQUAL, sentinelDead, "1");
        output.unitBind("@" + mlogType);
        emitJump(end, JumpCondition.STRICT_EQUAL, "@unit", "null");
        emitJump(end, JumpCondition.STRICT_EQUAL, "@unit", sentinel);
        emitJump(scan, JumpCondition.ALWAYS, "0", "0");
    }

    private void emitUnitControl(HirUnitControl control) {
        if (!"move".equals(control.command()) || control.arguments().size() != 2) {
            throw new IllegalArgumentException("unsupported Unit control command: " + control.command());
        }
        String x = emitExpression(control.arguments().get(0));
        String y = emitExpression(control.arguments().get(1));
        MlogProgramBuilder.Label end = null;
        if (control.storedReference()) {
            end = label("unit_ref_control_end");
            output.unitBind(variable(control.bindingName()));
            emitJump(end, JumpCondition.STRICT_EQUAL, "@unit", "null");
            String dead = temporary();
            output.sensor(dead, "@unit", "@dead");
            emitJump(end, JumpCondition.EQUAL, dead, "1");
        }
        // v146 serializes all five ucontrol parameter slots even though move consumes x/y.
        output.unitControl(UnitControlCommand.MOVE, x, y, "0", "0", "0");
        if (end != null) emitLabel(end);
    }

    private void emitBuildingControl(HirBuildingControl control) {
        List<String> arguments = control.arguments().stream().map(this::emitExpression).toList();
        output.buildingControl(resolveBuildingTarget(control.target()), control.action(), arguments);
    }

    private void emitDraw(HirDraw draw) {
        if (activeDrawTarget != null && !activeDrawTarget.equals(draw.displayName())) output.drawFlush(activeDrawTarget);
        activeDrawTarget = draw.displayName();
        List<String> values = draw.arguments().stream().map(this::emitExpression).toList();
        List<String> operands = switch (draw.command()) {
            case CLEAR -> List.of(values.get(0), values.get(1), values.get(2), "0", "0", "0");
            case COLOR -> List.of(values.get(0), values.get(1), values.get(2), values.get(3), "0", "0");
            case RECT, LINE_RECT, LINE -> List.of(values.get(0), values.get(1), values.get(2), values.get(3), "0", "0");
        };
        String command = switch (draw.command()) {
            case CLEAR -> "clear";
            case COLOR -> "color";
            case RECT -> "rect";
            case LINE_RECT -> "lineRect";
            case LINE -> "line";
        };
        output.draw(command, operands);
    }

    /** Emits a print-only String concatenation without allocating a target String value. */
    private void emitPrintValue(HirExpression expression) {
        List<HirExpression> leaves = new java.util.ArrayList<>();
        collectPrintLeaves(expression, leaves);
        StringBuilder pendingText = new StringBuilder();
        for (HirExpression leaf : leaves) {
            if (leaf instanceof HirText text) {
                pendingText.append(text.value());
                continue;
            }
            if (!pendingText.isEmpty()) {
                output.print(quote(pendingText.toString()));
                pendingText.setLength(0);
            }
            if (leaf.type() == com.arc.mpl.hir.ValueType.STRING) {
                emitDynamicStringPrint(emitExpression(leaf));
            } else {
                output.print(emitExpression(leaf));
            }
        }
        if (!pendingText.isEmpty()) output.print(quote(pendingText.toString()));
    }

    private void emitDynamicStringPrint(String handle) {
        String length = emitStringLength(handle);
        String index = temporary();
        output.set(index, "0");
        MlogProgramBuilder.Label end = label("string_print_end");
        MlogProgramBuilder.Label loop = label("string_print_loop");
        emitJump(end, JumpCondition.GREATER_THAN_EQ, index, length);
        emitLabel(loop);
        String unit = emitStringUnitRead(handle, index);
        if (supportsPrintChar()) {
            output.printChar(unit);
        } else {
            output.operation(Operation.ADD, "__mpl_string_return", "@counter", "1");
            output.set("@counter", unit);
        }
        output.operation(Operation.ADD, index, index, "1");
        emitJump(loop, JumpCondition.LESS_THAN, index, length);
        emitLabel(end);
    }

    private void collectPrintLeaves(HirExpression expression, List<HirExpression> leaves) {
        if (expression instanceof HirStringConcat concat) {
            collectPrintLeaves(concat.left(), leaves);
            collectPrintLeaves(concat.right(), leaves);
            return;
        }
        if (expression instanceof HirBinary binary && binary.type() == com.arc.mpl.hir.ValueType.STRING
            && "+".equals(binary.operator())) {
            collectPrintLeaves(binary.left(), leaves);
            collectPrintLeaves(binary.right(), leaves);
            return;
        }
        leaves.add(expression);
    }

    private boolean supportsPrintChar() {
        return targetCapabilities.contains("printchar");
    }

    /** The target owns one graphics buffer, so runtime flushes it before switching Displays and at exit. */
    private void flushDrawBuffer(String requestedTarget) {
        if (activeDrawTarget != null) output.drawFlush(activeDrawTarget);
        else output.drawFlush(requestedTarget);
        activeDrawTarget = null;
    }

    private void flushPendingDraw() {
        if (activeDrawTarget != null) output.drawFlush(activeDrawTarget);
        activeDrawTarget = null;
    }

    private void emitBuildingIteration(HirBuildingIteration iteration) {
        MlogProgramBuilder.Label end = label("building_end");
        HirHardwareLink previous = activeBuildingBindings.get(iteration.bindingName());
        try {
            for (HirHardwareLink building : iteration.buildings()) {
                MlogProgramBuilder.Label next = label("building_next");
                activeBuildingBindings.put(iteration.bindingName(), building);
                emitFilterRejectionJump(iteration.filters(), next);
                emitLoopBody(iteration.body(), next, end);
                emitLabel(next);
            }
        } finally {
            restoreBuildingBinding(iteration.bindingName(), previous);
        }
        emitLabel(end);
    }

    private String emitBuildingQuerySize(HirBuildingQuery query) {
        String result = temporary();
        HirHardwareLink previous = activeBuildingBindings.get(query.bindingName());
        output.set(result, "0");
        try {
            for (HirHardwareLink building : query.buildings()) {
                MlogProgramBuilder.Label next = label("building_count_next");
                activeBuildingBindings.put(query.bindingName(), building);
                emitFilterRejectionJump(query.filters(), next);
                output.operation(Operation.ADD, result, result, "1");
                emitLabel(next);
            }
        } finally {
            restoreBuildingBinding(query.bindingName(), previous);
        }
        return result;
    }

    private String emitBuildingQueryGet(HirBuildingQueryGet get) {
        HirBuildingQuery query = get.query();
        String result = temporary();
        String index = temporary();
        String position = temporary();
        MlogProgramBuilder.Label end = label("building_get_end");
        HirHardwareLink previous = activeBuildingBindings.get(query.bindingName());

        output.set(result, "null");
        output.set(index, emitExpression(get.index()));
        emitJump(end, JumpCondition.LESS_THAN, index, "0");
        output.set(position, "0");
        try {
            for (HirHardwareLink building : query.buildings()) {
                MlogProgramBuilder.Label found = label("building_get_found");
                MlogProgramBuilder.Label next = label("building_get_next");
                activeBuildingBindings.put(query.bindingName(), building);
                emitFilterRejectionJump(query.filters(), next);
                emitJump(found, JumpCondition.EQUAL, position, index);
                output.operation(Operation.ADD, position, position, "1");
                emitJump(next, JumpCondition.ALWAYS, "0", "0");
                emitLabel(found);
                output.set(result, building.gameAlias());
                emitJump(end, JumpCondition.ALWAYS, "0", "0");
                emitLabel(next);
            }
        } finally {
            restoreBuildingBinding(query.bindingName(), previous);
        }
        emitLabel(end);
        return result;
    }

    private void restoreBuildingBinding(String bindingName, HirHardwareLink previous) {
        if (previous == null) activeBuildingBindings.remove(bindingName);
        else activeBuildingBindings.put(bindingName, previous);
    }

    private String resolveBuildingTarget(HirExpression target) {
        if (target instanceof HirHardwareLink hardware) return hardware.gameAlias();
        if (target instanceof HirVariable variable && variable.type() instanceof BuildingType building
            && !building.nullable()) {
            return variable(variable.name());
        }
        if (target instanceof HirVariable variable && variable.type() == com.arc.mpl.hir.ValueType.BUILDING) {
            HirHardwareLink building = activeBuildingBindings.get(variable.name());
            if (building != null) return building.gameAlias();
        }
        throw new IllegalArgumentException("building control lacks an active linked building target: " + target);
    }

    private void emitLoopBody(List<HirStatement> body, MlogProgramBuilder.Label continueTarget,
                              MlogProgramBuilder.Label breakTarget) {
        loopTargets.push(new LoopTarget(continueTarget, breakTarget));
        try {
            for (HirStatement statement : body) emitStatement(statement);
        } finally {
            loopTargets.pop();
        }
    }

    private void emitLoopJump(boolean breaking) {
        LoopTarget target = loopTargets.peek();
        if (target == null) throw new IllegalStateException("循环跳转缺少目标");
        emitJump(breaking ? target.breakTarget() : target.continueTarget(), JumpCondition.ALWAYS, "0", "0");
    }

    private String emitExpression(HirExpression expression) {
        if (expression instanceof HirConstant constant) return constant.mlogLiteral();
        if (expression instanceof HirText text) {
            return Integer.toString(memoryLayout.stringRuntime().literal(text).handle());
        }
        if (expression instanceof HirArrayLiteral || expression instanceof HirTupleLiteral || expression instanceof HirCollectionLiteral) {
            throw new IllegalArgumentException("aggregate literal must be assigned before target lowering");
        }
        if (expression instanceof HirVariable variable) return variable(variable.name());
        if (expression instanceof HirIndexAccess access) return emitIndexAccess(access);
        if (expression instanceof HirDynamicIndexAccess access) return emitDynamicIndexAccess(access);
        if (expression instanceof HirCollectionContains contains) return emitCollectionContains(contains);
        if (expression instanceof HirUnitQuerySize size) return emitUnitQuerySize(size.query());
        if (expression instanceof HirUnitQueryGet get) return emitUnitQueryGet(get);
        if (expression instanceof HirUnitQuery) {
            throw new IllegalArgumentException("Set<Unit<T>> 描述符只能保存、读取 size 或作为 for 遍历目标");
        }
        if (expression instanceof HirBuildingQuerySize size) return emitBuildingQuerySize(size.query());
        if (expression instanceof HirBuildingQueryGet get) return emitBuildingQueryGet(get);
        if (expression instanceof HirBuildingQuery) {
            throw new IllegalArgumentException("LinkedBuildingSet<T> 描述符只能保存、读取 size/get 或作为 for 遍历目标");
        }
        if (expression instanceof HirUnary unary) return emitUnary(unary);
        if (expression instanceof HirStringConcat concat) return emitStringConcat(concat);
        if (expression instanceof HirStringLength length) return emitStringLength(emitExpression(length.value()));
        if (expression instanceof HirStringSnapshot snapshot) return emitStringSnapshot(snapshot);
        if (expression instanceof HirStringComparison comparison) return emitStringComparison(comparison);
        if (expression instanceof HirBinary binary) return emitBinary(binary);
        if (expression instanceof HirMemberAccess member) return emitMemberAccess(member);
        if (expression instanceof HirIntrinsicCall call) return emitIntrinsicCall(call);
        if (expression instanceof HirFunctionCall call) return emitFunctionCall(call);
        if (expression instanceof HirNewObject allocation) return emitNewObject(allocation);
        if (expression instanceof HirObjectFieldRead read) return emitObjectFieldRead(read);
        if (expression instanceof HirObjectFieldAssignment assignment) return emitObjectFieldAssignment(assignment);
        return emitAssignment((HirAssignment) expression);
    }

    private void emitAggregateDeclaration(HirVariableDeclaration declaration, List<HirExpression> elements) {
        for (int index = 0; index < elements.size(); index++) {
            HirExpression element = elements.get(index);
            String value = emitExpression(element);
            if (element.type() == com.arc.mpl.hir.ValueType.STRING) {
                StringRuntimeLayout.Entry target = stringAggregateElement(declaration.name(), index);
                emitStringCopy(value, target);
                value = Integer.toString(target.handle());
            }
            storeAggregateElement(declaration.name(), index, value);
        }
    }

    private String emitStringConcat(HirStringConcat concat) {
        // Evaluate both sides before mutating the reusable concatenation buffer.
        String left = temporary();
        output.set(left, emitExpression(concat.left()));
        String right = temporary();
        output.set(right, emitExpression(concat.right()));
        StringRuntimeLayout.Entry target = memoryLayout.stringRuntime().concatenation(concat);
        String outputIndex = temporary();
        output.set(outputIndex, "0");
        emitStringAppend(left, target, outputIndex);
        emitStringAppend(right, target, outputIndex);
        emitStringLengthWrite(target, outputIndex);
        return Integer.toString(target.handle());
    }

    private String emitStringSnapshot(HirStringSnapshot snapshot) {
        String source = emitExpression(snapshot.value());
        StringRuntimeLayout.Entry target = memoryLayout.stringRuntime().snapshot(snapshot);
        emitStringCopy(source, target);
        return Integer.toString(target.handle());
    }

    private void emitStringCopy(String source, StringRuntimeLayout.Entry target) {
        if (target.isLiteral()) throw new IllegalArgumentException("不能覆写 String 字面量描述符");
        String outputIndex = temporary();
        output.set(outputIndex, "0");
        emitStringAppend(source, target, outputIndex);
        emitStringLengthWrite(target, outputIndex);
    }

    private void emitStringAppend(String source, StringRuntimeLayout.Entry target, String outputIndex) {
        String length = emitStringLength(source);
        String sourceIndex = temporary();
        output.set(sourceIndex, "0");
        MlogProgramBuilder.Label end = label("string_copy_end");
        MlogProgramBuilder.Label loop = label("string_copy_loop");
        emitJump(end, JumpCondition.GREATER_THAN_EQ, sourceIndex, length);
        emitLabel(loop);
        String unit = emitStringUnitRead(source, sourceIndex);
        emitPhysicalWrite(target.allocation(), outputIndex, unit);
        output.operation(Operation.ADD, sourceIndex, sourceIndex, "1");
        output.operation(Operation.ADD, outputIndex, outputIndex, "1");
        emitJump(loop, JumpCondition.LESS_THAN, sourceIndex, length);
        emitLabel(end);
    }

    private String emitStringLength(String handle) {
        String descriptorIndex = temporary();
        output.operation(Operation.SUB, descriptorIndex, handle, "1");
        return emitPhysicalRead(memoryLayout.stringRuntime().lengths(), descriptorIndex);
    }

    private String emitStringUnitRead(String handle, String index) {
        String descriptorIndex = temporary();
        output.operation(Operation.SUB, descriptorIndex, handle, "1");
        String base = emitPhysicalRead(memoryLayout.stringRuntime().bases(), descriptorIndex);
        String address = temporary();
        output.operation(Operation.ADD, address, base, index);
        return emitGlobalPhysicalRead(address);
    }

    private void emitStringLengthWrite(StringRuntimeLayout.Entry entry, String length) {
        writePhysicalConstant(memoryLayout.stringRuntime().lengths(), entry.handle() - 1, length);
    }

    private String emitStringComparison(HirStringComparison comparison) {
        String left = temporary();
        output.set(left, emitExpression(comparison.left()));
        String right = temporary();
        output.set(right, emitExpression(comparison.right()));
        return emitStringComparison(left, right, comparison.equal());
    }

    private String emitStringComparison(String left, String right, boolean equal) {
        String leftLength = emitStringLength(left);
        String rightLength = emitStringLength(right);
        String result = temporary();
        output.set(result, equal ? "1" : "0");
        MlogProgramBuilder.Label mismatch = label("string_compare_mismatch");
        MlogProgramBuilder.Label end = label("string_compare_end");
        emitJump(end, JumpCondition.STRICT_EQUAL, left, right);
        emitJump(mismatch, JumpCondition.NOT_EQUAL, leftLength, rightLength);
        String index = temporary();
        output.set(index, "0");
        MlogProgramBuilder.Label loop = label("string_compare_loop");
        emitJump(end, JumpCondition.GREATER_THAN_EQ, index, leftLength);
        emitLabel(loop);
        String leftUnit = emitStringUnitRead(left, index);
        String rightUnit = emitStringUnitRead(right, index);
        emitJump(mismatch, JumpCondition.NOT_EQUAL, leftUnit, rightUnit);
        output.operation(Operation.ADD, index, index, "1");
        emitJump(loop, JumpCondition.LESS_THAN, index, leftLength);
        emitJump(end, JumpCondition.ALWAYS, "0", "0");
        emitLabel(mismatch);
        output.set(result, equal ? "0" : "1");
        emitLabel(end);
        return result;
    }

    private StringRuntimeLayout.Entry stringVariable(String name) {
        return memoryLayout.stringRuntime().variable(currentFunction, name)
            .orElseThrow(() -> new IllegalArgumentException("String 变量缺少 runtime 缓冲：" + name));
    }

    private StringRuntimeLayout.Entry stringAggregateElement(String name, int index) {
        return memoryLayout.stringRuntime().aggregateElement(currentFunction, name, index)
            .orElseThrow(() -> new IllegalArgumentException("String 聚合元素缺少 runtime 缓冲：" + name + "[" + index + "]"));
    }

    private String emitUnitQuerySize(HirUnitQuery query) {
        if (query.hasManagedLimit()) {
            return emitManagedUnitScan(new ManagedUnitQuery(query.mlogType(), query.filters(),
                query.managedLimit(), query.managedId()), (next, end) -> { });
        }
        int queryId = unitIterationIndex++;
        MlogProgramBuilder.Label scan = label("unit_count_scan");
        MlogProgramBuilder.Label next = label("unit_count_next");
        MlogProgramBuilder.Label end = label("unit_count_end");
        String result = temporary();
        String sentinel = "__mpl_unit_count_sentinel" + queryId;

        output.set(result, "0");
        output.unitBind("@" + query.mlogType());
        emitJump(end, JumpCondition.STRICT_EQUAL, "@unit", "null");
        output.set(sentinel, "@unit");
        emitLabel(scan);
        emitFilterRejectionJump(query.filters(), next);
        output.operation(Operation.ADD, result, result, "1");
        emitLabel(next);
        emitUnitScanAdvance(sentinel, query.mlogType(), scan, end);
        emitLabel(end);
        return result;
    }

    private String emitUnitQueryGet(HirUnitQueryGet get) {
        HirUnitQuery query = get.query();
        if (query.hasManagedLimit()) return emitManagedUnitQueryGet(get);
        int queryId = unitIterationIndex++;
        MlogProgramBuilder.Label scan = label("unit_get_scan");
        MlogProgramBuilder.Label found = label("unit_get_found");
        MlogProgramBuilder.Label next = label("unit_get_next");
        MlogProgramBuilder.Label end = label("unit_get_end");
        String result = temporary();
        String index = temporary();
        String position = temporary();
        String sentinel = "__mpl_unit_get_sentinel" + queryId;

        output.set(result, "null");
        output.set(index, emitExpression(get.index()));
        emitJump(end, JumpCondition.LESS_THAN, index, "0");
        output.unitBind("@" + query.mlogType());
        emitJump(end, JumpCondition.STRICT_EQUAL, "@unit", "null");
        output.set(sentinel, "@unit");
        output.set(position, "0");
        emitLabel(scan);
        emitFilterRejectionJump(query.filters(), next);
        emitJump(found, JumpCondition.EQUAL, position, index);
        output.operation(Operation.ADD, position, position, "1");
        emitJump(next, JumpCondition.ALWAYS, "0", "0");
        emitLabel(found);
        output.set(result, "@unit");
        emitJump(end, JumpCondition.ALWAYS, "0", "0");
        emitLabel(next);
        emitUnitScanAdvance(sentinel, query.mlogType(), scan, end);
        emitLabel(end);
        return result;
    }

    private String emitManagedUnitQueryGet(HirUnitQueryGet get) {
        HirUnitQuery query = get.query();
        String result = temporary();
        String index = temporary();
        String position = temporary();
        MlogProgramBuilder.Label found = label("managed_unit_get_found");
        MlogProgramBuilder.Label end = label("managed_unit_get_end");

        output.set(result, "null");
        output.set(index, emitExpression(get.index()));
        emitJump(end, JumpCondition.LESS_THAN, index, "0");
        output.set(position, "0");
        emitManagedUnitScan(new ManagedUnitQuery(query.mlogType(), query.filters(),
            query.managedLimit(), query.managedId()), (next, scanEnd) -> {
                emitJump(found, JumpCondition.EQUAL, position, index);
                output.operation(Operation.ADD, position, position, "1");
                emitJump(next, JumpCondition.ALWAYS, "0", "0");
                emitLabel(found);
                output.set(result, "@unit");
                emitJump(scanEnd, JumpCondition.ALWAYS, "0", "0");
            });
        emitLabel(end);
        return result;
    }

    private void emitAggregateIteration(HirAggregateIteration iteration) {
        MlogProgramBuilder.Label end = label("aggregate_end");
        for (int index = 0; index < iteration.size(); index++) {
            MlogProgramBuilder.Label next = label("aggregate_next");
            output.set(variable(iteration.bindingName()), loadAggregateElement(iteration.source().name(), index));
            emitLoopBody(iteration.body(), next, end);
            emitLabel(next);
        }
        emitLabel(end);
    }

    private String emitIndexAccess(HirIndexAccess access) {
        if (access.target() instanceof HirObjectFieldRead read && access.index() instanceof HirConstant index) {
            return emitObjectTupleElementRead(read, objectTupleIndex(read, index));
        }
        if (!(access.target() instanceof HirVariable variable) || !(access.index() instanceof HirConstant index)) {
            throw new IllegalArgumentException("aggregate access must be statically resolved before target lowering");
        }
        int position;
        try {
            position = Math.toIntExact(Long.parseLong(index.mlogLiteral()));
        } catch (NumberFormatException | ArithmeticException exception) {
            throw new IllegalArgumentException("invalid aggregate index: " + index.mlogLiteral(), exception);
        }
        return loadAggregateElement(variable.name(), position);
    }

    private String emitDynamicIndexAccess(HirDynamicIndexAccess access) {
        if (!(access.target() instanceof HirVariable variable)) {
            throw new IllegalArgumentException("dynamic Array access target must be a variable");
        }
        PhysicalMemoryLayout.Allocation allocation = allocation(variable.name());
        String index = emitExpression(access.index());
        return emitPhysicalRead(allocation, index);
    }

    private String emitPhysicalRead(PhysicalMemoryLayout.Allocation allocation, String index) {
        String result = temporary();
        List<PhysicalMemoryLayout.Slice> slices = allocation.slices();
        MlogProgramBuilder.Label end = slices.size() > 1 ? label("memory_read_end") : null;
        for (int sliceIndex = 0; sliceIndex < slices.size(); sliceIndex++) {
            PhysicalMemoryLayout.Slice slice = slices.get(sliceIndex);
            boolean last = sliceIndex == slices.size() - 1;
            MlogProgramBuilder.Label next = last ? null : label("memory_read_next");
            if (!last) {
                emitJump(next, JumpCondition.GREATER_THAN_EQ, index,
                    Integer.toString(slice.logicalStart() + slice.length()));
            }
            output.read(result, segment(slice).alias(), physicalIndex(index, slice));
            if (!last) emitJump(end, JumpCondition.ALWAYS, "0", "0");
            if (next != null) emitLabel(next);
        }
        if (end != null) emitLabel(end);
        return result;
    }

    private String emitGlobalPhysicalRead(String address) {
        String result = temporary();
        MlogProgramBuilder.Label end = memoryLayout.segments().size() > 1 ? label("memory_global_read_end") : null;
        int globalStart = 0;
        for (int segmentIndex = 0; segmentIndex < memoryLayout.segments().size(); segmentIndex++) {
            PhysicalMemoryLayout.Segment segment = memoryLayout.segments().get(segmentIndex);
            boolean last = segmentIndex == memoryLayout.segments().size() - 1;
            MlogProgramBuilder.Label next = last ? null : label("memory_global_read_next");
            if (!last) {
                emitJump(next, JumpCondition.GREATER_THAN_EQ, address,
                    Integer.toString(globalStart + segment.capacity()));
            }
            String local = address;
            if (globalStart != 0) {
                local = temporary();
                output.operation(Operation.SUB, local, address, Integer.toString(globalStart));
            }
            output.read(result, segment.alias(), local);
            if (!last) emitJump(end, JumpCondition.ALWAYS, "0", "0");
            if (next != null) emitLabel(next);
            globalStart = Math.addExact(globalStart, segment.capacity());
        }
        if (end != null) emitLabel(end);
        return result;
    }

    private int globalAddress(PhysicalMemoryLayout.Allocation allocation) {
        PhysicalMemoryLayout.Slice first = allocation.slices().get(0);
        int address = first.offset();
        for (int index = 0; index < first.segmentIndex(); index++) {
            address = Math.addExact(address, memoryLayout.segments().get(index).capacity());
        }
        return address;
    }

    private void writePhysicalConstant(PhysicalMemoryLayout.Allocation allocation, int index, String value) {
        PhysicalMemoryLayout.Slice slice = constantSlice(allocation, index);
        output.write(value, segment(slice).alias(), Integer.toString(slice.offset() + index - slice.logicalStart()));
    }

    private void emitDynamicCollectionSet(HirDynamicCollectionSet update) {
        PhysicalMemoryLayout.Allocation allocation = allocation(update.target());
        String index = temporary();
        output.set(index, emitExpression(update.index()));
        String value = emitExpression(update.value());
        emitPhysicalWrite(allocation, index, value);
    }

    private void emitPhysicalWrite(PhysicalMemoryLayout.Allocation allocation, String index, String value) {
        List<PhysicalMemoryLayout.Slice> slices = allocation.slices();
        MlogProgramBuilder.Label end = slices.size() > 1 ? label("memory_write_end") : null;
        for (int sliceIndex = 0; sliceIndex < slices.size(); sliceIndex++) {
            PhysicalMemoryLayout.Slice slice = slices.get(sliceIndex);
            boolean last = sliceIndex == slices.size() - 1;
            MlogProgramBuilder.Label next = last ? null : label("memory_write_next");
            if (!last) {
                emitJump(next, JumpCondition.GREATER_THAN_EQ, index,
                    Integer.toString(slice.logicalStart() + slice.length()));
            }
            output.write(value, segment(slice).alias(), physicalIndex(index, slice));
            if (!last) emitJump(end, JumpCondition.ALWAYS, "0", "0");
            if (next != null) emitLabel(next);
        }
        if (end != null) emitLabel(end);
    }

    private void storeAggregateElement(String name, int index, String value) {
        java.util.Optional<PhysicalMemoryLayout.Allocation> allocation = memoryLayout.allocation(currentFunction, name);
        if (allocation.isEmpty()) {
            output.set(aggregateSlot(name, index), value);
            return;
        }
        PhysicalMemoryLayout.Slice slice = constantSlice(allocation.orElseThrow(), index);
        output.write(value, segment(slice).alias(), Integer.toString(slice.offset() + index - slice.logicalStart()));
    }

    private String loadAggregateElement(String name, int index) {
        java.util.Optional<PhysicalMemoryLayout.Allocation> allocation = memoryLayout.allocation(currentFunction, name);
        if (allocation.isEmpty()) return aggregateSlot(name, index);
        PhysicalMemoryLayout.Slice slice = constantSlice(allocation.orElseThrow(), index);
        String result = temporary();
        output.read(result, segment(slice).alias(), Integer.toString(slice.offset() + index - slice.logicalStart()));
        return result;
    }

    private PhysicalMemoryLayout.Allocation allocation(String name) {
        return memoryLayout.allocation(currentFunction, name)
            .orElseThrow(() -> new IllegalArgumentException("dynamic Array lacks physical Memory allocation: " + name));
    }

    private PhysicalMemoryLayout.Slice constantSlice(PhysicalMemoryLayout.Allocation allocation, int index) {
        return allocation.slices().stream().filter(slice -> slice.contains(index)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException(
                "index lies outside its physical Memory allocation: " + index));
    }

    private PhysicalMemoryLayout.Segment segment(PhysicalMemoryLayout.Slice slice) {
        return memoryLayout.segments().get(slice.segmentIndex());
    }

    private String physicalIndex(String logicalIndex, PhysicalMemoryLayout.Slice slice) {
        int adjustment = slice.offset() - slice.logicalStart();
        if (adjustment == 0) return logicalIndex;
        String result = temporary();
        output.operation(Operation.ADD, result, logicalIndex, Integer.toString(adjustment));
        return result;
    }

    private String emitCollectionContains(HirCollectionContains contains) {
        if (!(contains.target() instanceof HirVariable variable)) {
            throw new IllegalArgumentException("collection contains target must be a variable");
        }
        String candidate = temporary();
        output.set(candidate, emitExpression(contains.candidate()));
        String result = temporary();
        output.set(result, "0");
        for (int index = 0; index < contains.size(); index++) {
            String equal;
            if (contains.candidate().type() == com.arc.mpl.hir.ValueType.STRING) {
                equal = emitStringComparison(loadAggregateElement(variable.name(), index), candidate, true);
            } else {
                equal = temporary();
                output.operation(Operation.EQUAL, equal, loadAggregateElement(variable.name(), index), candidate);
            }
            output.operation(Operation.OR, result, result, equal);
        }
        return result;
    }

    private String emitFunctionCall(HirFunctionCall call) {
        List<String> savedArguments = new java.util.ArrayList<>();
        for (HirExpression argument : call.arguments()) {
            String saved = temporary();
            output.set(saved, emitExpression(argument));
            savedArguments.add(saved);
        }
        String result = runtimeContext.main() && runtimeContext.helperPlan().task(call.function()).isPresent()
            ? emitRemoteFunctionCall(call, savedArguments)
            : emitPreparedFunctionCall(call.function(), savedArguments, call.type());
        if (call.type() != com.arc.mpl.hir.ValueType.STRING) return result;
        StringRuntimeLayout.Entry target = memoryLayout.stringRuntime().callResult(call);
        emitStringCopy(result, target);
        return Integer.toString(target.handle());
    }

    private String emitRemoteFunctionCall(HirFunctionCall call, List<String> savedArguments) {
        RuntimeHelperPlan.Task task = runtimeContext.helperPlan().task(call.function()).orElseThrow();
        RuntimeHelperPlan.Worker worker = runtimeContext.helperPlan().workers().stream()
            .filter(value -> value.id().equals(task.worker())).findFirst().orElseThrow();
        List<String> payload = new java.util.ArrayList<>(savedArguments);
        while (payload.size() < worker.requestPayloadSlots()) payload.add("0");
        SharedMailboxProtocolEmitter mailboxes = new SharedMailboxProtocolEmitter(output, memoryLayout, runtimeContext);
        mailboxes.emitSend(worker.requestMailbox(), Integer.toString(task.kind()), payload);
        SharedMailboxProtocolEmitter.ReceivedMessage response = mailboxes.emitReceive(worker.responseMailbox());
        if (response.payload().size() != 1) {
            throw new IllegalArgumentException("helper 响应邮箱必须恰好包含一个结果槽：" + call.function());
        }
        return response.payload().get(0);
    }

    private String emitPreparedFunctionCall(String functionName, List<String> savedArguments, MplType returnType) {
        HirFunction function = functions.get(functionName);
        if (function == null) throw new IllegalArgumentException("unknown HIR function: " + functionName);
        for (int index = 0; index < savedArguments.size() && index < function.parameters().size(); index++) {
            var parameter = function.parameters().get(index);
            if (parameter.type() == com.arc.mpl.hir.ValueType.STRING) {
                StringRuntimeLayout.Entry target = memoryLayout.stringRuntime()
                    .variable(function.name(), parameter.name())
                    .orElseThrow(() -> new IllegalArgumentException("String 参数缺少 runtime 缓冲："
                        + function.name() + "." + parameter.name()));
                emitStringCopy(savedArguments.get(index), target);
                output.set(functionParameterSlot(function.name(), parameter.name()), Integer.toString(target.handle()));
            } else {
                output.set(functionParameterSlot(function.name(), parameter.name()), savedArguments.get(index));
            }
        }
        output.operation(Operation.ADD, functionReturnSlot(function.name()), "@counter", "1");
        output.setCounter(functionEntries.get(function.name()));
        if (returnType == com.arc.mpl.hir.ValueType.VOID) return "0";
        String result = temporary();
        output.set(result, functionResultSlot(function.name()));
        return result;
    }

    private String emitNewObject(HirNewObject allocation) {
        if (allocation.allocationKind() == HirNewObject.AllocationKind.POOLED) {
            return emitPooledNewObject(allocation);
        }
        List<HirExpression> arguments = new java.util.ArrayList<>();
        arguments.add(new HirConstant(Integer.toString(allocation.allocationId()), com.arc.mpl.hir.ValueType.INT));
        arguments.addAll(allocation.arguments());
        emitFunctionCall(new HirFunctionCall(allocation.constructorFunction(), arguments,
            com.arc.mpl.hir.ValueType.VOID));
        return Integer.toString(allocation.allocationId());
    }

    private String emitPooledNewObject(HirNewObject allocation) {
        PhysicalMemoryLayout.ObjectPool pool = memoryLayout.objectPool(allocation.className())
            .orElseThrow(() -> new IllegalArgumentException("池分配缺少物理布局：" + allocation.className()));
        String handle = temporary();
        output.set(handle, "0");
        String slot = temporary();
        output.set(slot, "0");
        MlogProgramBuilder.Label scan = label("object_pool_allocate_scan");
        MlogProgramBuilder.Label found = label("object_pool_allocate_found");
        MlogProgramBuilder.Label end = label("object_pool_allocate_end");
        emitLabel(scan);
        String occupied = emitPhysicalRead(pool.occupancy(), slot);
        emitJump(found, JumpCondition.EQUAL, occupied, "0");
        output.operation(Operation.ADD, slot, slot, "1");
        emitJump(scan, JumpCondition.LESS_THAN, slot, Integer.toString(pool.capacity()));
        emitJump(end, JumpCondition.ALWAYS, "0", "0");
        emitLabel(found);
        emitPhysicalWrite(pool.occupancy(), slot, "1");
        output.operation(Operation.ADD, handle, slot, Long.toString((long) pool.handleBase() + 1L));
        output.operation(Operation.MUL, handle, handle, "-1");
        emitLabel(end);
        List<String> savedArguments = new java.util.ArrayList<>();
        savedArguments.add(handle);
        for (HirExpression argument : allocation.arguments()) {
            String saved = temporary();
            output.set(saved, emitExpression(argument));
            savedArguments.add(saved);
        }
        emitPreparedFunctionCall(allocation.constructorFunction(), savedArguments, com.arc.mpl.hir.ValueType.VOID);
        return handle;
    }

    private String emitObjectFieldRead(HirObjectFieldRead read) {
        if (read.type() instanceof TupleType) {
            throw new IllegalArgumentException("元组对象字段必须通过常量下标读取："
                + read.className() + "." + read.field());
        }
        return emitObjectSlotRead(read.target(), read.className(), read.field(), null);
    }

    private String emitObjectTupleElementRead(HirObjectFieldRead read, int index) {
        return emitObjectSlotRead(read.target(), read.className(), read.field(), index);
    }

    private String emitObjectSlotRead(HirExpression sourceTarget, String className, String field, Integer element) {
        String target = temporary();
        output.set(target, emitExpression(sourceTarget));
        return emitObjectSlotRead(target, className, field, element);
    }

    private String emitObjectSlotRead(String target, String className, String field, Integer element) {
        String result = temporary();
        output.set(result, "0");
        MlogProgramBuilder.Label end = label("object_field_read_end");
        java.util.Optional<PhysicalMemoryLayout.ObjectPool> pooled = memoryLayout.objectPool(className);
        MlogProgramBuilder.Label fixed = pooled.isPresent() ? label("object_field_read_fixed") : null;
        if (pooled.isPresent()) {
            emitJump(fixed, JumpCondition.GREATER_THAN_EQ, target, "0");
            PhysicalMemoryLayout.ObjectPool pool = pooled.orElseThrow();
            PhysicalMemoryLayout.PoolField poolField = pool.field(field);
            output.set(result, emitPhysicalRead(poolField.allocation(), pooledFieldIndex(target, pool, poolField, element)));
            emitJump(end, JumpCondition.ALWAYS, "0", "0");
            emitLabel(fixed);
        }
        for (int allocationId : allocations(className)) {
            MlogProgramBuilder.Label next = label("object_field_read_next");
            emitJump(next, JumpCondition.NOT_EQUAL, target, Integer.toString(allocationId));
            output.set(result, objectFieldSlot(allocationId, field, element));
            emitJump(end, JumpCondition.ALWAYS, "0", "0");
            emitLabel(next);
        }
        emitLabel(end);
        return result;
    }

    private String emitObjectFieldAssignment(HirObjectFieldAssignment assignment) {
        HirClass.Field field = objectField(assignment.className(), assignment.field());
        if (field.type() instanceof TupleType tuple) {
            if (!"=".equals(assignment.operator()) || !(assignment.value() instanceof HirTupleLiteral literal)
                || literal.elements().size() != tuple.elementTypes().size()) {
                throw new IllegalArgumentException("元组对象字段只能由同形元组字面量整体初始化："
                    + assignment.className() + "." + assignment.field());
            }
            String target = temporary();
            output.set(target, emitExpression(assignment.target()));
            List<String> values = new java.util.ArrayList<>();
            for (HirExpression element : literal.elements()) {
                String saved = temporary();
                output.set(saved, emitExpression(element));
                values.add(saved);
            }
            emitObjectTupleWrite(target, assignment.className(), assignment.field(), tuple, values);
            return "0";
        }
        String target = temporary();
        output.set(target, emitExpression(assignment.target()));
        String value = temporary();
        output.set(value, emitExpression(assignment.value()));
        if (field.type() == com.arc.mpl.hir.ValueType.STRING) {
            if (!"=".equals(assignment.operator())) {
                throw new IllegalArgumentException("String 对象字段不支持复合赋值");
            }
            return emitStringObjectFieldWrite(target, assignment.className(), assignment.field(), null, value);
        }
        String result = value;
        if (!"=".equals(assignment.operator())) {
            String operator = assignment.operator().substring(0, 1);
            String current = emitObjectSlotRead(target, assignment.className(), assignment.field(), null);
            if (field.type() == com.arc.mpl.hir.ValueType.INT) {
                result = emitIntBinary(operator, current, value);
            } else if (field.type() == com.arc.mpl.hir.ValueType.FLOAT && isFloatArithmetic(operator)) {
                result = emitFloatBinary(operator, current, value);
            } else {
                result = temporary();
                output.operation(operation(operator), result, current, value);
            }
        }
        emitObjectSlotWrite(target, assignment.className(), assignment.field(), null, result);
        return result;
    }

    private void emitObjectTupleWrite(String target, String className, String field, TupleType type, List<String> values) {
        for (int index = 0; index < values.size(); index++) {
            if (type.elementTypes().get(index) == com.arc.mpl.hir.ValueType.STRING) {
                emitStringObjectFieldWrite(target, className, field, index, values.get(index));
            } else {
                emitObjectSlotWrite(target, className, field, index, values.get(index));
            }
        }
    }

    private String emitStringObjectFieldWrite(String objectHandle, String className, String field,
                                              Integer element, String source) {
        String result = temporary();
        output.set(result, "0");
        MlogProgramBuilder.Label end = label("object_string_write_end");
        for (int allocationId : allocations(className)) {
            MlogProgramBuilder.Label next = label("object_string_write_next");
            emitJump(next, JumpCondition.NOT_EQUAL, objectHandle, Integer.toString(allocationId));
            StringRuntimeLayout.Entry target = memoryLayout.stringRuntime()
                .objectField(allocationId, field, element)
                .orElseThrow(() -> new IllegalArgumentException("对象 String 字段缺少 runtime 缓冲："
                    + className + "." + field));
            emitStringCopy(source, target);
            output.set(objectFieldSlot(allocationId, field, element), Integer.toString(target.handle()));
            output.set(result, Integer.toString(target.handle()));
            emitJump(end, JumpCondition.ALWAYS, "0", "0");
            emitLabel(next);
        }
        emitLabel(end);
        return result;
    }

    private void emitObjectSlotWrite(String target, String className, String field, Integer element, String value) {
        MlogProgramBuilder.Label end = label("object_field_write_end");
        java.util.Optional<PhysicalMemoryLayout.ObjectPool> pooled = memoryLayout.objectPool(className);
        MlogProgramBuilder.Label fixed = pooled.isPresent() ? label("object_field_write_fixed") : null;
        if (pooled.isPresent()) {
            emitJump(fixed, JumpCondition.GREATER_THAN_EQ, target, "0");
            PhysicalMemoryLayout.ObjectPool pool = pooled.orElseThrow();
            PhysicalMemoryLayout.PoolField poolField = pool.field(field);
            emitPhysicalWrite(poolField.allocation(), pooledFieldIndex(target, pool, poolField, element), value);
            emitJump(end, JumpCondition.ALWAYS, "0", "0");
            emitLabel(fixed);
        }
        for (int allocationId : allocations(className)) {
            MlogProgramBuilder.Label next = label("object_field_write_next");
            emitJump(next, JumpCondition.NOT_EQUAL, target, Integer.toString(allocationId));
            output.set(objectFieldSlot(allocationId, field, element), value);
            emitJump(end, JumpCondition.ALWAYS, "0", "0");
            emitLabel(next);
        }
        emitLabel(end);
    }

    private String pooledFieldIndex(String handle, PhysicalMemoryLayout.ObjectPool pool,
                                    PhysicalMemoryLayout.PoolField field, Integer element) {
        String slot = pooledSlot(handle, pool);
        if (field.width() == 1) return slot;
        String index = temporary();
        output.operation(Operation.MUL, index, slot, Integer.toString(field.width()));
        if (element == null || element == 0) return index;
        output.operation(Operation.ADD, index, index, Integer.toString(element));
        return index;
    }

    private String pooledSlot(String handle, PhysicalMemoryLayout.ObjectPool pool) {
        String slot = temporary();
        output.operation(Operation.MUL, slot, handle, "-1");
        output.operation(Operation.SUB, slot, slot, Long.toString((long) pool.handleBase() + 1L));
        return slot;
    }

    private int objectTupleIndex(HirObjectFieldRead read, HirConstant index) {
        HirClass.Field field = objectField(read.className(), read.field());
        if (!(field.type() instanceof TupleType tuple)) {
            throw new IllegalArgumentException("对象字段不是元组：" + read.className() + "." + read.field());
        }
        int value;
        try {
            value = Math.toIntExact(Long.parseLong(index.mlogLiteral()));
        } catch (NumberFormatException | ArithmeticException exception) {
            throw new IllegalArgumentException("对象元组字段下标不是 Int 常量", exception);
        }
        if (value < 0 || value >= tuple.elementTypes().size()) {
            throw new IllegalArgumentException("对象元组字段下标越界：" + value);
        }
        return value;
    }

    private HirClass.Field objectField(String className, String fieldName) {
        HirClass type = classes.get(className);
        if (type == null) throw new IllegalArgumentException("unknown HIR class: " + className);
        return type.fields().stream().filter(field -> field.name().equals(fieldName)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("unknown HIR field: " + className + "." + fieldName));
    }

    private List<Integer> allocations(String className) {
        return objectAllocations.getOrDefault(className, List.of());
    }

    private String objectFieldSlot(int allocationId, String field, Integer element) {
        return "__mpl_obj" + allocationId + "_" + field + (element == null ? "" : "_e" + element);
    }

    private String emitMemberAccess(HirMemberAccess member) {
        if (member.target() instanceof HirHardwareLink hardware) {
            String result = temporary();
            output.sensor(result, hardware.gameAlias(), "@" + member.member());
            return result;
        }
        if (member.target() instanceof HirVariable variable && variable.type() == com.arc.mpl.hir.ValueType.BUILDING) {
            HirHardwareLink building = activeBuildingBindings.get(variable.name());
            if (building == null) throw new IllegalArgumentException("building field access lacks an active binding: " + variable.name());
            String result = temporary();
            output.sensor(result, building.gameAlias(), "@" + member.member());
            return result;
        }
        if (member.target() instanceof HirVariable variable && variable.type() instanceof BuildingType building
            && !building.nullable()) {
            String result = temporary();
            output.sensor(result, variable(variable.name()), "@" + member.member());
            return result;
        }
        if (member.target() instanceof HirVariable variable && variable.type() instanceof UnitType) {
            return emitStoredUnitMember(variable, member.member());
        }
        if (!(member.target() instanceof HirVariable variable) || variable.type() != com.arc.mpl.hir.ValueType.UNIT) {
            throw new IllegalArgumentException("unsupported member access target: " + member.target());
        }

        String result = temporary();
        String sensor = "alive".equals(member.member()) ? "dead" : member.member();
        output.sensor(result, "@unit", "@" + sensor);
        if ("alive".equals(member.member())) {
            String alive = temporary();
            output.operation(Operation.EQUAL, alive, result, "0");
            return alive;
        }
        return result;
    }

    private String emitStoredUnitMember(HirVariable reference, String member) {
        MlogProgramBuilder.Label end = label("unit_ref_read_end");
        String result = temporary();
        output.set(result, "dead".equals(member) ? "1" : "0");
        output.unitBind(variable(reference.name()));
        emitJump(end, JumpCondition.STRICT_EQUAL, "@unit", "null");
        String sensor = "alive".equals(member) ? "dead" : member;
        output.sensor(result, "@unit", "@" + sensor);
        if ("alive".equals(member)) output.operation(Operation.EQUAL, result, result, "0");
        emitLabel(end);
        return result;
    }

    private String emitIntrinsicCall(HirIntrinsicCall call) {
        if ("Clock".equals(call.namespace())) {
            if (!call.arguments().isEmpty()) {
                throw new IllegalArgumentException("Clock intrinsic does not accept arguments: " + call.name());
            }
            return switch (call.name()) {
                case "timeMs" -> "@time";
                case "time" -> emitScaledTime("1000.0");
                case "timeMinutes" -> emitScaledTime("60000.0");
                case "timeHours" -> emitScaledTime("3600000.0");
                case "tick" -> "@tick";
                default -> throw new IllegalArgumentException("unsupported Clock intrinsic: " + call.name());
            };
        }
        if ("Math".equals(call.namespace()) && ("sin".equals(call.name()) || "cos".equals(call.name()))
            && call.arguments().size() == 1) {
            String result = temporary();
            Operation operation = "sin".equals(call.name()) ? Operation.SIN : Operation.COS;
            output.operation(operation, result, emitExpression(call.arguments().get(0)), "0");
            return result;
        }
        if ("Int".equals(call.namespace()) && call.arguments().size() == 1) {
            return emitIntConversion(call.name(), emitExpression(call.arguments().get(0)));
        }
        throw new IllegalArgumentException("unsupported intrinsic: " + call.namespace() + "." + call.name());
    }

    /** Converts the target's millisecond clock to a derived floating-point unit. */
    private String emitScaledTime(String millisecondsPerUnit) {
        // The denominator is a fixed positive value greater than one, so this
        // derived Clock conversion cannot overflow a finite target time value.
        String result = temporary();
        output.operation(Operation.DIV, result, "@time", millisecondsPerUnit);
        return result;
    }

    private String emitUnary(HirUnary unary) {
        String operand = emitExpression(unary.operand());
        if ("+".equals(unary.operator())) return operand;
        if ("-".equals(unary.operator()) && unary.type() == com.arc.mpl.hir.ValueType.INT) {
            return emitIntBinary("-", "0", operand);
        }
        String temporary = temporary();
        Operation operation = switch (unary.operator()) {
            case "-" -> Operation.SUB;
            case "!" -> Operation.EQUAL;
            default -> throw new IllegalArgumentException("unsupported unary operator " + unary.operator());
        };
        output.operation(operation, temporary, "0", operand);
        return temporary;
    }

    private String emitBinary(HirBinary binary) {
        if ("&&".equals(binary.operator()) || "||".equals(binary.operator())) {
            return emitShortCircuitBinary(binary);
        }
        String left = emitExpression(binary.left());
        String right = emitExpression(binary.right());
        if ("===".equals(binary.operator()) || "!==".equals(binary.operator())) {
            return emitIdentityComparison(left, right, "===".equals(binary.operator()));
        }
        if (binary.type() == com.arc.mpl.hir.ValueType.INT) {
            return emitIntBinary(binary.operator(), left, right);
        }
        if (binary.type() == com.arc.mpl.hir.ValueType.FLOAT && isFloatArithmetic(binary.operator())) {
            return emitFloatBinary(binary.operator(), left, right);
        }
        String temporary = temporary();
        output.operation(operation(binary.operator()), temporary, left, right);
        return temporary;
    }

    private String emitIdentityComparison(String left, String right, boolean equal) {
        String result = temporary();
        MlogProgramBuilder.Label end = label("object_identity_end");
        output.set(result, equal ? "1" : "0");
        emitJump(end, JumpCondition.STRICT_EQUAL, left, right);
        output.set(result, equal ? "0" : "1");
        emitLabel(end);
        return result;
    }

    /** Emits MPL's lazy boolean operators without relying on mlog's eager op instructions. */
    private String emitShortCircuitBinary(HirBinary binary) {
        String left = emitExpression(binary.left());
        String result = temporary();
        MlogProgramBuilder.Label end = label("short_circuit_end");
        boolean conjunction = "&&".equals(binary.operator());
        output.set(result, conjunction ? "0" : "1");
        emitJump(end, conjunction ? JumpCondition.EQUAL : JumpCondition.NOT_EQUAL, left, "0");
        output.set(result, emitExpression(binary.right()));
        emitLabel(end);
        return result;
    }

    private String emitAssignment(HirAssignment assignment) {
        String target = variable(assignment.target());
        String value = emitExpression(assignment.value());
        if (assignment.type() == com.arc.mpl.hir.ValueType.STRING) {
            if (!"=".equals(assignment.operator())) {
                throw new IllegalArgumentException("String 复合赋值必须在语义层被拒绝");
            }
            StringRuntimeLayout.Entry storage = stringVariable(assignment.target());
            emitStringCopy(value, storage);
            output.set(target, Integer.toString(storage.handle()));
            return target;
        }
        if ("=".equals(assignment.operator())) {
            output.set(target, value);
        } else {
            String operator = assignment.operator().substring(0, 1);
            if (assignment.type() == com.arc.mpl.hir.ValueType.INT) {
                output.set(target, emitIntBinary(operator, target, value));
            } else if (assignment.type() == com.arc.mpl.hir.ValueType.FLOAT && isFloatArithmetic(operator)) {
                output.set(target, emitFloatBinary(operator, target, value));
            } else {
                output.operation(operation(operator), target, target, value);
            }
        }
        return target;
    }

    /** Lowers MPL's 32-bit total Int operations without relying on target long overflow. */
    private String emitIntBinary(String operator, String left, String right) {
        if ("%".equals(operator)) return emitIntRemainder(left, right);
        String result = temporary();
        output.operation(operation(operator), result, left, right);
        output.operation(Operation.MIN, result, result, "2147483647");
        output.operation(Operation.MAX, result, result, "-2147483648");
        return result;
    }

    /** v146 would otherwise turn modulo by zero into NaN and then silently into zero. */
    private String emitIntRemainder(String left, String right) {
        String result = temporary();
        MlogProgramBuilder.Label nonZero = label("int_mod_non_zero");
        MlogProgramBuilder.Label end = label("int_mod_end");
        emitJump(nonZero, JumpCondition.NOT_EQUAL, right, "0");
        output.set(result, "0");
        emitJump(end, JumpCondition.ALWAYS, "0", "0");
        emitLabel(nonZero);
        output.operation(Operation.MOD, result, left, right);
        emitLabel(end);
        return result;
    }

    /** Rounds a finite Float then clamps it to MPL's signed 32-bit Int domain. */
    private String emitIntConversion(String conversion, String value) {
        Operation operation = switch (conversion) {
            case "floor" -> Operation.FLOOR;
            case "ceil" -> Operation.CEIL;
            case "round" -> Operation.ROUND;
            default -> throw new IllegalArgumentException("unsupported Int conversion " + conversion);
        };
        String result = temporary();
        output.operation(operation, result, value, "0");
        output.operation(Operation.MIN, result, result, "2147483647");
        output.operation(Operation.MAX, result, result, "-2147483648");
        return result;
    }

    /** Emits a bounded floating arithmetic operation before the target can produce Infinity. */
    private String emitFloatBinary(String operator, String left, String right) {
        return switch (operator) {
            case "+" -> emitFloatAddition(left, right, false);
            case "-" -> emitFloatAddition(left, right, true);
            case "*" -> emitFloatMultiplication(left, right);
            case "/" -> emitFloatDivision(left, right);
            default -> throw new IllegalArgumentException("unsupported Float operator " + operator);
        };
    }

    private String emitFloatAddition(String left, String right, boolean subtract) {
        String result = temporary();
        String threshold = temporary();
        String prefix = subtract ? "float_sub" : "float_add";
        MlogProgramBuilder.Label rightPositive = label(prefix + "_rhs_positive");
        MlogProgramBuilder.Label compute = label(prefix + "_compute");
        MlogProgramBuilder.Label positiveOverflow = label(prefix + "_positive_overflow");
        MlogProgramBuilder.Label negativeOverflow = label(prefix + "_negative_overflow");
        MlogProgramBuilder.Label end = label(prefix + "_end");

        emitJump(rightPositive, JumpCondition.GREATER_THAN, right, "0");
        if (subtract) {
            output.operation(Operation.ADD, threshold, FLOAT_MAX, right);
            emitJump(positiveOverflow, JumpCondition.GREATER_THAN, left, threshold);
        } else {
            output.operation(Operation.SUB, threshold, FLOAT_MIN, right);
            emitJump(negativeOverflow, JumpCondition.LESS_THAN, left, threshold);
        }
        emitJump(compute, JumpCondition.ALWAYS, "0", "0");

        emitLabel(rightPositive);
        if (subtract) {
            output.operation(Operation.ADD, threshold, FLOAT_MIN, right);
            emitJump(negativeOverflow, JumpCondition.LESS_THAN, left, threshold);
        } else {
            output.operation(Operation.SUB, threshold, FLOAT_MAX, right);
            emitJump(positiveOverflow, JumpCondition.GREATER_THAN, left, threshold);
        }
        emitJump(compute, JumpCondition.ALWAYS, "0", "0");

        emitLabel(positiveOverflow);
        output.set(result, FLOAT_MAX);
        emitJump(end, JumpCondition.ALWAYS, "0", "0");
        emitLabel(negativeOverflow);
        output.set(result, FLOAT_MIN);
        emitJump(end, JumpCondition.ALWAYS, "0", "0");
        emitLabel(compute);
        output.operation(subtract ? Operation.SUB : Operation.ADD, result, left, right);
        emitLabel(end);
        return result;
    }

    private String emitFloatMultiplication(String left, String right) {
        String result = temporary();
        String threshold = temporary();
        MlogProgramBuilder.Label positiveLarge = label("float_mul_rhs_positive_large");
        MlogProgramBuilder.Label negativeLarge = label("float_mul_rhs_negative_large");
        MlogProgramBuilder.Label compute = label("float_mul_compute");
        MlogProgramBuilder.Label positiveOverflow = label("float_mul_positive_overflow");
        MlogProgramBuilder.Label negativeOverflow = label("float_mul_negative_overflow");
        MlogProgramBuilder.Label end = label("float_mul_end");

        emitJump(positiveLarge, JumpCondition.GREATER_THAN, right, "1");
        emitJump(negativeLarge, JumpCondition.LESS_THAN, right, "-1");
        emitJump(compute, JumpCondition.ALWAYS, "0", "0");

        emitLabel(positiveLarge);
        output.operation(Operation.DIV, threshold, FLOAT_MAX, right);
        emitJump(positiveOverflow, JumpCondition.GREATER_THAN, left, threshold);
        output.operation(Operation.DIV, threshold, FLOAT_MIN, right);
        emitJump(negativeOverflow, JumpCondition.LESS_THAN, left, threshold);
        emitJump(compute, JumpCondition.ALWAYS, "0", "0");

        emitLabel(negativeLarge);
        output.operation(Operation.DIV, threshold, FLOAT_MAX, right);
        emitJump(positiveOverflow, JumpCondition.LESS_THAN, left, threshold);
        output.operation(Operation.DIV, threshold, FLOAT_MIN, right);
        emitJump(negativeOverflow, JumpCondition.GREATER_THAN, left, threshold);
        emitJump(compute, JumpCondition.ALWAYS, "0", "0");

        emitLabel(positiveOverflow);
        output.set(result, FLOAT_MAX);
        emitJump(end, JumpCondition.ALWAYS, "0", "0");
        emitLabel(negativeOverflow);
        output.set(result, FLOAT_MIN);
        emitJump(end, JumpCondition.ALWAYS, "0", "0");
        emitLabel(compute);
        output.operation(Operation.MUL, result, left, right);
        emitLabel(end);
        return result;
    }

    private String emitFloatDivision(String left, String right) {
        String result = temporary();
        String threshold = temporary();
        MlogProgramBuilder.Label nonZero = label("float_div_non_zero");
        MlogProgramBuilder.Label positive = label("float_div_rhs_positive");
        MlogProgramBuilder.Label negativeSmall = label("float_div_rhs_negative_small");
        MlogProgramBuilder.Label positiveSmall = label("float_div_rhs_positive_small");
        MlogProgramBuilder.Label compute = label("float_div_compute");
        MlogProgramBuilder.Label positiveOverflow = label("float_div_positive_overflow");
        MlogProgramBuilder.Label negativeOverflow = label("float_div_negative_overflow");
        MlogProgramBuilder.Label end = label("float_div_end");

        emitJump(nonZero, JumpCondition.NOT_EQUAL, right, "0");
        output.set(result, "0.0");
        emitJump(end, JumpCondition.ALWAYS, "0", "0");

        emitLabel(nonZero);
        emitJump(positive, JumpCondition.GREATER_THAN, right, "0");
        emitJump(compute, JumpCondition.LESS_THAN_EQ, right, "-1");
        emitJump(negativeSmall, JumpCondition.ALWAYS, "0", "0");

        emitLabel(positive);
        emitJump(compute, JumpCondition.GREATER_THAN_EQ, right, "1");
        emitJump(positiveSmall, JumpCondition.ALWAYS, "0", "0");

        emitLabel(positiveSmall);
        output.operation(Operation.MUL, threshold, FLOAT_MAX, right);
        emitJump(positiveOverflow, JumpCondition.GREATER_THAN, left, threshold);
        output.operation(Operation.MUL, threshold, FLOAT_MIN, right);
        emitJump(negativeOverflow, JumpCondition.LESS_THAN, left, threshold);
        emitJump(compute, JumpCondition.ALWAYS, "0", "0");

        emitLabel(negativeSmall);
        output.operation(Operation.MUL, threshold, FLOAT_MAX, right);
        emitJump(positiveOverflow, JumpCondition.LESS_THAN, left, threshold);
        output.operation(Operation.MUL, threshold, FLOAT_MIN, right);
        emitJump(negativeOverflow, JumpCondition.GREATER_THAN, left, threshold);
        emitJump(compute, JumpCondition.ALWAYS, "0", "0");

        emitLabel(positiveOverflow);
        output.set(result, FLOAT_MAX);
        emitJump(end, JumpCondition.ALWAYS, "0", "0");
        emitLabel(negativeOverflow);
        output.set(result, FLOAT_MIN);
        emitJump(end, JumpCondition.ALWAYS, "0", "0");
        emitLabel(compute);
        output.operation(Operation.DIV, result, left, right);
        emitLabel(end);
        return result;
    }

    private boolean isFloatArithmetic(String operator) {
        return "+".equals(operator) || "-".equals(operator) || "*".equals(operator) || "/".equals(operator);
    }

    private Operation operation(String operator) {
        return switch (operator) {
            case "+" -> Operation.ADD;
            case "-" -> Operation.SUB;
            case "*" -> Operation.MUL;
            case "/" -> Operation.DIV;
            case "%" -> Operation.MOD;
            case "==" -> Operation.EQUAL;
            case "!=" -> Operation.NOT_EQUAL;
            case "<" -> Operation.LESS_THAN;
            case "<=" -> Operation.LESS_THAN_EQ;
            case ">" -> Operation.GREATER_THAN;
            case ">=" -> Operation.GREATER_THAN_EQ;
            default -> throw new IllegalArgumentException("unsupported binary operator " + operator);
        };
    }

    private String variable(String name) {
        if (currentFunction == null || globalVariables.contains(name)) return "mpl_" + name;
        HirFunction function = functions.get(currentFunction);
        if (function != null && function.parameters().stream().anyMatch(parameter -> parameter.name().equals(name))) {
            return functionParameterSlot(currentFunction, name);
        }
        return functionPrefix(currentFunction) + "_local_" + name;
    }

    private String aggregateSlot(String name, int index) {
        if (index < 0) throw new IllegalArgumentException("aggregate index must be non-negative");
        return variable(name) + "_e" + index;
    }

    private String functionParameterSlot(String function, String parameter) {
        HirFunction declaration = functions.get(function);
        if (declaration == null) throw new IllegalArgumentException("unknown HIR function: " + function);
        for (int index = 0; index < declaration.parameters().size(); index++) {
            if (declaration.parameters().get(index).name().equals(parameter)) {
                return functionPrefix(function) + "_arg" + index;
            }
        }
        throw new IllegalArgumentException("unknown HIR function parameter: " + function + "." + parameter);
    }

    private String functionReturnSlot(String function) { return functionPrefix(function) + "_return"; }
    private String functionResultSlot(String function) { return functionPrefix(function) + "_result"; }

    private String functionPrefix(String function) {
        Integer index = functionIndexes.get(function);
        if (index == null) throw new IllegalArgumentException("unknown HIR function: " + function);
        return "__mpl_fn" + index;
    }

    private String temporary() {
        return "__mpl_tmp" + temporaryIndex++;
    }

    private MlogProgramBuilder.Label label(String role) {
        return output.newLabel(role);
    }

    private void emitLabel(MlogProgramBuilder.Label label) {
        output.label(label);
    }

    private void emitJump(MlogProgramBuilder.Label target, JumpCondition condition, String value, String compare) {
        output.jump(target, condition, value, compare);
    }

    private String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r").replace("\"", "\\\"") + "\"";
    }

    private record LoopTarget(MlogProgramBuilder.Label continueTarget, MlogProgramBuilder.Label breakTarget) {
    }

    private record ManagedUnitQuery(String mlogType, List<HirExpression> filters, int limit, int managedId) {
        private ManagedUnitQuery {
            java.util.Objects.requireNonNull(mlogType, "mlogType");
            filters = List.copyOf(java.util.Objects.requireNonNull(filters, "filters"));
            if (limit <= 0) throw new IllegalArgumentException("managed Unit query limit must be positive");
            if (managedId < 0 || managedId >= 4096) {
                throw new IllegalArgumentException("managed Unit query id must be in [0, 4096)");
            }
        }
    }

    @FunctionalInterface
    private interface ManagedUnitVisitor {
        void emit(MlogProgramBuilder.Label next, MlogProgramBuilder.Label end);
    }
}
