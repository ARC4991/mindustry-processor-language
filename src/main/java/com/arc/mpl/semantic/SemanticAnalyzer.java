package com.arc.mpl.semantic;

import com.arc.mpl.ast.AssignmentExpression;
import com.arc.mpl.ast.ArrayLiteral;
import com.arc.mpl.ast.BinaryExpression;
import com.arc.mpl.ast.BlockStatement;
import com.arc.mpl.ast.BooleanLiteral;
import com.arc.mpl.ast.BreakStatement;
import com.arc.mpl.ast.CallExpression;
import com.arc.mpl.ast.ClassDeclaration;
import com.arc.mpl.ast.ClassFieldDeclaration;
import com.arc.mpl.ast.ClassMethodDeclaration;
import com.arc.mpl.ast.ContinueStatement;
import com.arc.mpl.ast.DoWhileStatement;
import com.arc.mpl.ast.Expression;
import com.arc.mpl.ast.ExpressionStatement;
import com.arc.mpl.ast.FloatLiteral;
import com.arc.mpl.ast.ForEachStatement;
import com.arc.mpl.ast.ForStatement;
import com.arc.mpl.ast.FunctionDeclaration;
import com.arc.mpl.ast.FunctionParameter;
import com.arc.mpl.ast.Identifier;
import com.arc.mpl.ast.IndexExpression;
import com.arc.mpl.ast.IfStatement;
import com.arc.mpl.ast.IntegerLiteral;
import com.arc.mpl.ast.LambdaExpression;
import com.arc.mpl.ast.MemberAccessExpression;
import com.arc.mpl.ast.MemberAssignmentExpression;
import com.arc.mpl.ast.MethodCallExpression;
import com.arc.mpl.ast.MilDrawStatement;
import com.arc.mpl.ast.NullLiteral;
import com.arc.mpl.ast.NewExpression;
import com.arc.mpl.ast.Program;
import com.arc.mpl.ast.ReturnStatement;
import com.arc.mpl.ast.Statement;
import com.arc.mpl.ast.StringLiteral;
import com.arc.mpl.ast.TupleLiteral;
import com.arc.mpl.ast.UnaryExpression;
import com.arc.mpl.ast.VariableDeclaration;
import com.arc.mpl.ast.WhileStatement;
import com.arc.mpl.diagnostic.Diagnostic;
import com.arc.mpl.diagnostic.Diagnostic.SourceSpan;
import com.arc.mpl.diagnostic.Severity;
import com.arc.mpl.hir.HirAssignment;
import com.arc.mpl.hir.HirAggregateIteration;
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
import com.arc.mpl.hir.HirMutableListLiteral;
import com.arc.mpl.hir.HirMutableListAdd;
import com.arc.mpl.hir.HirMutableListClear;
import com.arc.mpl.hir.HirMutableListRemoveAt;
import com.arc.mpl.hir.HirClass;
import com.arc.mpl.hir.HirConstant;
import com.arc.mpl.hir.HirContinue;
import com.arc.mpl.hir.HirDoWhile;
import com.arc.mpl.hir.HirDraw;
import com.arc.mpl.hir.HirDynamicCollectionSet;
import com.arc.mpl.hir.HirDynamicIndexAccess;
import com.arc.mpl.hir.HirExpression;
import com.arc.mpl.hir.HirExpressionStatement;
import com.arc.mpl.hir.HirFor;
import com.arc.mpl.hir.HirFunction;
import com.arc.mpl.hir.HirFunctionCall;
import com.arc.mpl.hir.HirFunctionParameter;
import com.arc.mpl.hir.HirHardwareLink;
import com.arc.mpl.hir.HirIntrinsicCall;
import com.arc.mpl.hir.HirIf;
import com.arc.mpl.hir.HirArrayLiteral;
import com.arc.mpl.hir.HirIndexAccess;
import com.arc.mpl.hir.HirMemberAccess;
import com.arc.mpl.hir.HirMethodCall;
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
import com.arc.mpl.hir.ObjectType;
import com.arc.mpl.hir.BuildingType;
import com.arc.mpl.hir.CollectionType;
import com.arc.mpl.hir.TupleType;
import com.arc.mpl.hir.UnitType;
import com.arc.mpl.hir.ValueType;
import com.arc.mpl.profile.KnownProfiles;
import com.arc.mpl.profile.TargetProfile;
import com.arc.mpl.project.HardwareContract;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Name resolution and strict type checks for the currently implemented MPL subset. */
public final class SemanticAnalyzer {
    private final TargetProfile profile;
    private final Deque<Map<String, Symbol>> scopes = new ArrayDeque<>();
    private final List<Diagnostic> diagnostics = new ArrayList<>();
    /** Internal function identity to signature, including lowered methods and overloads. */
    private final Map<String, FunctionSignature> functions = new LinkedHashMap<>();
    /** Source-visible top-level name to overload set. */
    private final Map<String, List<FunctionSignature>> functionOverloads = new LinkedHashMap<>();
    private final Map<FunctionDeclaration, FunctionSignature> declarationFunctions = new IdentityHashMap<>();
    private Map<String, Long> topLevelFunctionCounts = Map.of();
    private final Map<String, ClassInfo> classes = new LinkedHashMap<>();
    private final Map<String, Set<String>> callGraph = new HashMap<>();
    private final Map<String, Set<String>> directGlobalDependencies = new HashMap<>();
    private final List<TopLevelCall> topLevelCalls = new ArrayList<>();
    private final Set<String> initializedGlobals = new HashSet<>();
    private final Map<FunctionParameterShapeKey, Integer> aggregateParameterSizes = new LinkedHashMap<>();
    private final Map<String, Integer> aggregateReturnSizes = new LinkedHashMap<>();
    private final Set<String> aggregateShapeErrors = new HashSet<>();
    private final Deque<ArrayBoundsProof> arrayBoundsProofs = new ArrayDeque<>();
    private final ObjectReceiverEscapeAnalyzer objectReceiverEscapeAnalyzer = new ObjectReceiverEscapeAnalyzer();
    private final OwnedObjectFactoryAnalyzer ownedObjectFactoryAnalyzer = new OwnedObjectFactoryAnalyzer();
    private final Deque<List<PooledOwner>> pooledOwnerScopes = new ArrayDeque<>();
    private Path file;
    private Map<String, String> messages = Map.of();
    private Map<String, HardwareContract.LinkDeclaration> hardwareLinks = Map.of();
    private Map<String, HardwareContract.Resource> hardwareResources = Map.of();
    private int unitIterationDepth;
    private int nextManagedQueryId;
    private int nextObjectAllocationId;
    private int nextStringAllocationId;
    private int loopDepth;
    /** Length-changing list operations are accepted only on a linear path whose capacity is provable. */
    private int aggregateControlDepth;
    private String activeUnitBinding;
    private String activeBuildingBinding;
    private String activeBuildingType;
    private String currentFunction;
    private String currentClass;
    private MplType currentReturnType;
    private boolean analyzingTopLevel;
    private ObjectAllocationContext objectAllocationContext;
    private boolean borrowedObjectUse;
    private Expression allowedOwnedFactoryCall;
    private boolean currentReturnsOwnedObject;
    private MethodInfo currentMethod;
    private String inferringClass;
    private TypeRelations typeRelations = TypeRelations.empty();

    /** Uses the v146 baseline when semantic analysis is invoked outside a compiler request. */
    public SemanticAnalyzer() {
        this(KnownProfiles.find("v146").orElseThrow());
    }

    public SemanticAnalyzer(TargetProfile profile) {
        this.profile = java.util.Objects.requireNonNull(profile, "profile");
    }

    public SemanticResult analyze(Program program, Path sourceFile) {
        return analyze(program, sourceFile, Map.of());
    }

    public SemanticResult analyze(Program program, Path sourceFile, Map<String, String> messages) {
        List<HardwareContract.LinkDeclaration> links = messages.entrySet().stream()
            .map(entry -> new HardwareContract.LinkDeclaration(entry.getKey(), "Message", entry.getValue())).toList();
        return analyze(program, sourceFile, new HardwareContract(links, messages));
    }

    /** Analyzes source with the full typed hardware contract, never treating links as numeric variables. */
    public SemanticResult analyze(Program program, Path sourceFile, HardwareContract hardware) {
        scopes.clear();
        scopes.push(new HashMap<>());
        diagnostics.clear();
        file = sourceFile;
        this.messages = Map.copyOf(hardware.messages());
        Map<String, HardwareContract.LinkDeclaration> links = new LinkedHashMap<>();
        for (HardwareContract.LinkDeclaration link : hardware.links()) {
            if (links.put(link.mplName(), link) != null) {
                throw new IllegalArgumentException("重复的硬件常量：" + link.mplName());
            }
        }
        hardwareLinks = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(links));
        hardwareResources = Map.copyOf(hardware.resources());
        functions.clear();
        functionOverloads.clear();
        declarationFunctions.clear();
        topLevelFunctionCounts = Map.of();
        classes.clear();
        callGraph.clear();
        directGlobalDependencies.clear();
        topLevelCalls.clear();
        initializedGlobals.clear();
        aggregateParameterSizes.clear();
        aggregateReturnSizes.clear();
        aggregateShapeErrors.clear();
        arrayBoundsProofs.clear();
        pooledOwnerScopes.clear();
        pooledOwnerScopes.push(new ArrayList<>());
        unitIterationDepth = 0;
        nextManagedQueryId = 0;
        nextObjectAllocationId = 1;
        nextStringAllocationId = 1;
        loopDepth = 0;
        activeUnitBinding = null;
        activeBuildingBinding = null;
        activeBuildingType = null;
        currentFunction = null;
        currentClass = null;
        currentReturnType = null;
        analyzingTopLevel = true;
        objectAllocationContext = ObjectAllocationContext.DISALLOWED;
        borrowedObjectUse = false;
        allowedOwnedFactoryCall = null;
        currentReturnsOwnedObject = false;
        currentMethod = null;
        inferringClass = null;
        typeRelations = TypeRelations.empty();

        if (!program.imports().isEmpty() || !program.exports().isEmpty()) {
            SourceSpan span = !program.imports().isEmpty()
                ? program.imports().get(0).span() : program.exports().get(0).span();
            error("MPL1400", "模块声明必须先经过 ProjectProgramLoader 链接", span);
            return new SemanticResult(Optional.empty(), diagnostics);
        }

        for (ClassDeclaration declaration : program.classes()) registerClassName(declaration);
        resolveClassHierarchy();
        for (ClassInfo type : classHierarchyOrder()) registerClassMembers(type.declaration());
        inferImplicitMethodReturnTypes();
        validateInferredOverrides();
        topLevelFunctionCounts = program.functions().stream().collect(java.util.stream.Collectors.groupingBy(
            FunctionDeclaration::name, LinkedHashMap::new, java.util.stream.Collectors.counting()));
        for (FunctionDeclaration function : program.functions()) registerFunction(function);
        inferImplicitFunctionReturnTypes(program.functions());
        inferFunctionAggregateShapes(program);

        List<HirStatement> statements = new ArrayList<>();
        try {
            for (Statement statement : program.statements()) {
                statements.add(analyzeStatement(statement));
            }
        } finally {
            analyzingTopLevel = false;
        }
        List<HirFunction> analyzedFunctions = new ArrayList<>();
        for (ClassInfo type : classes.values()) {
            for (MethodInfo method : type.methods().values()) analyzedFunctions.add(analyzeMethod(type, method));
            for (MethodInfo constructor : type.constructors().values()) analyzedFunctions.add(analyzeMethod(type, constructor));
        }
        analyzedFunctions.addAll(program.functions().stream().map(this::analyzeFunction).toList());
        rejectRecursiveFunctions();
        validateTopLevelCalls();
        Set<String> exported = program.exports().stream().map(com.arc.mpl.ast.ExportDeclaration::name)
            .collect(java.util.stream.Collectors.toSet());
        List<HirClass> hirClasses = classes.values().stream().map(type -> type.toHir(exported.contains(type.name()))).toList();
        return new SemanticResult(diagnostics.isEmpty()
            ? Optional.of(new HirProgram(hirClasses, analyzedFunctions, statements)) : Optional.empty(), diagnostics);
    }

    private void registerClassName(ClassDeclaration declaration) {
        if (!declaration.name().matches("[A-Z][A-Za-z0-9]*") && !declaration.name().startsWith("__module_")) {
            error("MPL3701", "类名必须使用大驼峰命名：" + declaration.name(), declaration.span());
        }
        if (hardwareResources.containsKey(declaration.name()) || hardwareLinks.containsKey(declaration.name())
            || classes.putIfAbsent(declaration.name(),
            new ClassInfo(declaration.name(), declaration)) != null) {
            error("MPL3701", "类已声明：" + declaration.name(), declaration.span());
        }
    }

    private void resolveClassHierarchy() {
        for (ClassInfo type : classes.values()) {
            String parentName = type.declaration().superClass().orElse(null);
            if (parentName == null) continue;
            ClassInfo parent = classes.get(parentName);
            if (parent == null) {
                error("MPL3710", "类 " + type.name() + " 继承了未声明的父类：" + parentName,
                    type.declaration().span());
                continue;
            }
            type.parent(parent);
        }

        Map<String, Integer> states = new HashMap<>();
        for (ClassInfo type : classes.values()) validateInheritanceAcyclic(type, states, new ArrayDeque<>());

        Map<String, Optional<String>> parents = new LinkedHashMap<>();
        for (ClassInfo type : classes.values()) {
            parents.put(type.name(), Optional.ofNullable(type.parent()).map(ClassInfo::name));
        }
        typeRelations = new TypeRelations(parents);
    }

    private void validateInheritanceAcyclic(ClassInfo type, Map<String, Integer> states, Deque<String> path) {
        if (states.getOrDefault(type.name(), 0) == 2) return;
        if (states.getOrDefault(type.name(), 0) == 1) {
            error("MPL3711", "类继承图存在循环：" + String.join(" -> ", path) + " -> " + type.name(),
                type.declaration().span());
            if (!path.isEmpty()) classes.get(path.getLast()).parent(null);
            return;
        }
        states.put(type.name(), 1);
        path.addLast(type.name());
        if (type.parent() != null) validateInheritanceAcyclic(type.parent(), states, path);
        path.removeLast();
        states.put(type.name(), 2);
    }

    private List<ClassInfo> classHierarchyOrder() {
        return classes.values().stream()
            .sorted(java.util.Comparator.comparingInt(this::classDepth).thenComparing(ClassInfo::name))
            .toList();
    }

    private int classDepth(ClassInfo type) {
        int depth = 0;
        Set<String> seen = new HashSet<>();
        ClassInfo current = type.parent();
        while (current != null && seen.add(current.name())) {
            depth++;
            current = current.parent();
        }
        return depth;
    }

    private void registerClassMembers(ClassDeclaration declaration) {
        ClassInfo type = classes.get(declaration.name());
        if (type == null || type.declaration() != declaration) return;
        for (ClassFieldDeclaration source : declaration.fields()) {
            MplType fieldType = parseType(source.typeName(), source.span());
            if (!supportedObjectField(fieldType)) {
                error("MPL3702", "第一版对象字段只支持标量与标量元组：" + source.name(), source.span());
            }
            FieldInfo inherited = lookupField(type.parent(), source.name(), false);
            if (inherited != null && !inherited.type().equals(fieldType)) {
                error("MPL3712", "字段 " + type.name() + "." + source.name()
                    + " 与父类字段类型不兼容：" + display(fieldType) + " / " + display(inherited.type()), source.span());
            }
            if (inherited != null && inherited.publicAccess()
                && source.access() != com.arc.mpl.ast.AccessModifier.PUBLIC) {
                error("MPL3712", "继承字段不能收窄访问权限：" + type.name() + "." + source.name(), source.span());
            }
            FieldInfo field = new FieldInfo(type.name(), source.name(), fieldType,
                source.access() == com.arc.mpl.ast.AccessModifier.PUBLIC, source.span());
            if (type.fields().putIfAbsent(source.name(), field) != null) {
                error("MPL3701", "类 " + type.name() + " 的字段重复：" + source.name(), source.span());
            }
        }
        Map<String, Long> counts = declaration.methods().stream().collect(java.util.stream.Collectors.groupingBy(
            method -> method.function().name(), LinkedHashMap::new, java.util.stream.Collectors.counting()));
        for (ClassMethodDeclaration source : declaration.methods()) registerMethod(type, source, counts);
        if (type.constructors().isEmpty()) {
            error("MPL3703", "类 " + type.name() + " 必须声明一个同名构造器", declaration.span());
        }
    }

    private void registerMethod(ClassInfo type, ClassMethodDeclaration source, Map<String, Long> counts) {
        FunctionDeclaration function = source.function();
        boolean constructor = function.name().equals(type.name());
        if (constructor && function.returnType().isPresent()) {
            error("MPL3703", "构造器不能声明返回类型：" + type.name(), function.span());
        }
        MplType returnType = constructor ? ValueType.VOID
            : function.returnType().map(value -> parseType(value, function.span())).orElse(ValueType.ERROR);
        List<MplType> sourceParameters = function.parameters().stream()
            .map(parameter -> parseType(parameter.typeName(), parameter.span())).toList();
        if (sourceParameters.stream().anyMatch(parameterType -> !supportedMethodAggregateAbi(parameterType))
            || !supportedMethodAggregateAbi(returnType)) {
            error("MPL3602", "方法 ABI 只支持标量及 Int/Float/Bool 元组", function.span());
        }
        int overloadIndex = constructor ? type.constructors().size()
            : (int) type.methods().keySet().stream().filter(key -> key.sourceName().equals(function.name())).count();
        String internalName = "__mpl_class_" + type.name() + "_" + function.name()
            + (counts.getOrDefault(function.name(), 0L) > 1 ? "_" + overloadIndex : "");
        MethodInfo method = new MethodInfo(type.name(), function.name(), internalName, function, sourceParameters, returnType,
            source.access() == com.arc.mpl.ast.AccessModifier.PUBLIC, constructor,
            objectReceiverEscapeAnalyzer.receiverEscapes(function.body()));
        if (constructor) {
            if (type.constructors().putIfAbsent(sourceParameters, method) != null) {
                error("MPL3713", "构造器签名重复：" + callableDisplay(type.name(), sourceParameters), function.span());
                return;
            }
        } else {
            if (type.methods().putIfAbsent(method.key(), method) != null) {
                error("MPL3713", "方法签名重复：" + callableDisplay(type.name() + "." + function.name(),
                    sourceParameters), function.span());
                return;
            }
        }
        List<MplType> hiddenParameters = new ArrayList<>();
        hiddenParameters.add(new ObjectType(type.name(), false));
        hiddenParameters.addAll(sourceParameters);
        functions.put(internalName, new FunctionSignature(function.name(), internalName, function,
            hiddenParameters, returnType, false));
        callGraph.put(internalName, new HashSet<>());
        directGlobalDependencies.put(internalName, new HashSet<>());
    }

    private void inferImplicitMethodReturnTypes() {
        List<MethodInfo> methods = classes.values().stream()
            .flatMap(type -> type.methods().values().stream()).toList();
        boolean changed;
        int remainingPasses = methods.size() + 1;
        do {
            changed = false;
            for (MethodInfo method : methods) {
                if (method.declaration().returnType().isPresent()) continue;
                ClassInfo owner = classes.get(method.ownerClass());
                MethodInfo current = owner == null ? null : owner.methods().get(method.key());
                if (current == null || current.returnType() != ValueType.ERROR) continue;
                String previousClass = inferringClass;
                inferringClass = current.ownerClass();
                MplType inferred;
                try {
                    inferred = inferFunctionReturnType(current.declaration(), current.parameterTypes());
                } finally {
                    inferringClass = previousClass;
                }
                if (inferred == ValueType.ERROR) continue;
                replaceMethodInfo(owner, current, inferred);
                changed = true;
            }
        } while (changed && --remainingPasses > 0);

        for (MethodInfo method : methods) {
            if (method.declaration().returnType().isPresent()) continue;
            ClassInfo owner = classes.get(method.ownerClass());
            MethodInfo current = owner == null ? null : owner.methods().get(method.key());
            if (current != null && current.returnType() == ValueType.ERROR) {
                error("MPL3503", "无法推导方法 " + current.ownerClass() + "." + current.sourceName()
                    + " 的返回类型；请显式标注类型", current.declaration().span());
            }
        }
    }

    private void replaceMethodInfo(ClassInfo owner, MethodInfo previous, MplType returnType) {
        if (!supportedMethodAggregateAbi(returnType)) {
            error("MPL3602", "方法 ABI 只支持标量及 Int/Float/Bool 元组", previous.declaration().span());
            return;
        }
        MethodInfo replacement = new MethodInfo(previous.ownerClass(), previous.sourceName(), previous.internalName(),
            previous.declaration(), previous.parameterTypes(), returnType, previous.publicAccess(), previous.constructor(),
            previous.receiverEscapes());
        owner.methods().put(replacement.key(), replacement);
        List<MplType> hiddenParameters = new ArrayList<>();
        hiddenParameters.add(new ObjectType(replacement.ownerClass(), false));
        hiddenParameters.addAll(replacement.parameterTypes());
        functions.put(replacement.internalName(), new FunctionSignature(replacement.sourceName(), replacement.internalName(),
            replacement.declaration(), hiddenParameters, replacement.returnType(), false));
    }

    private void validateInferredOverrides() {
        for (ClassInfo type : classes.values()) {
            for (MethodInfo method : type.methods().values()) {
                MethodInfo overridden = findInheritedMethod(type.parent(), method.key());
                if (overridden != null) validateOverride(method, overridden);
            }
        }
    }

    private void validateOverride(MethodInfo method, MethodInfo overridden) {
        if (!overridden.publicAccess()) return;
        if (!method.publicAccess()) {
            error("MPL3714", "覆盖方法不能收窄访问权限：" + method.ownerClass() + "." + method.sourceName(),
                method.declaration().span());
        }
        if (!canAssign(overridden.returnType(), method.returnType())) {
            error("MPL3714", "覆盖方法返回类型不兼容：" + display(method.returnType())
                + " 不能覆盖 " + display(overridden.returnType()), method.declaration().span());
        }
    }

    private boolean supportedObjectField(MplType type) {
        if (type == ValueType.INT || type == ValueType.FLOAT || type == ValueType.BOOL || type == ValueType.STRING) return true;
        return type instanceof TupleType tuple && tuple.elementTypes().stream().allMatch(element ->
            element == ValueType.INT || element == ValueType.FLOAT || element == ValueType.BOOL || element == ValueType.STRING);
    }

    private boolean supportedMethodAggregateAbi(MplType type) {
        if (type == ValueType.ERROR || type == ValueType.VOID) return true;
        if (type == ValueType.INT || type == ValueType.FLOAT || type == ValueType.BOOL
            || type == ValueType.STRING) return true;
        if (type instanceof ObjectType || type instanceof UnitType || type instanceof BuildingType) return true;
        return type instanceof TupleType tuple && tuple.elementTypes().stream().allMatch(element ->
            element == ValueType.INT || element == ValueType.FLOAT || element == ValueType.BOOL);
    }

    private FieldInfo lookupField(ClassInfo type, String name, boolean inheritedAccessibleOnly) {
        ClassInfo current = type;
        boolean inherited = false;
        while (current != null) {
            FieldInfo field = current.fields().get(name);
            if (field != null && (!inheritedAccessibleOnly || !inherited || field.publicAccess())) return field;
            inherited = true;
            current = current.parent();
        }
        return null;
    }

    private MethodInfo findInheritedMethod(ClassInfo type, MethodKey key) {
        ClassInfo current = type;
        while (current != null) {
            MethodInfo method = current.methods().get(key);
            if (method != null && method.publicAccess()) return method;
            current = current.parent();
        }
        return null;
    }

    private String callableDisplay(String name, List<MplType> parameters) {
        return name + "(" + parameters.stream().map(MplType::displayName)
            .collect(java.util.stream.Collectors.joining(", ")) + ")";
    }

    private HirStatement analyzeStatement(Statement statement) {
        if (statement instanceof MilDrawStatement draw) {
            return analyzeMilDraw(draw);
        }
        if (statement instanceof VariableDeclaration declaration) {
            return analyzeDeclaration(declaration);
        }
        if (statement instanceof WhileStatement loop) {
            return analyzeWhile(loop);
        }
        if (statement instanceof DoWhileStatement loop) {
            return analyzeDoWhile(loop);
        }
        if (statement instanceof IfStatement branch) {
            return analyzeIf(branch);
        }
        if (statement instanceof ForStatement loop) {
            return analyzeFor(loop);
        }
        if (statement instanceof ForEachStatement loop) {
            return analyzeForEach(loop);
        }
        if (statement instanceof BlockStatement block) {
            return new HirBlock(analyzeBlock(block));
        }
        if (statement instanceof BreakStatement jump) {
            if (loopDepth == 0) error("MPL3401", "break 只能出现在循环内", jump.span());
            return withLoopCleanup(new HirBreak());
        }
        if (statement instanceof ContinueStatement jump) {
            if (loopDepth == 0) error("MPL3402", "continue 只能出现在循环内", jump.span());
            return withLoopCleanup(new HirContinue());
        }
        if (statement instanceof ReturnStatement returned) {
            return analyzeReturn(returned);
        }
        ExpressionStatement expressionStatement = (ExpressionStatement) statement;
        return analyzeExpressionStatement(expressionStatement.expression());
    }

    private void registerFunction(FunctionDeclaration function) {
        List<MplType> parameters = function.parameters().stream()
            .map(parameter -> parseType(parameter.typeName(), parameter.span())).toList();
        if (parameters.contains(ValueType.VOID)) {
            error("MPL3503", "函数参数不能使用 Void 类型", function.span());
        }
        MplType returnType = function.returnType().map(value -> parseType(value, function.span())).orElse(ValueType.ERROR);
        if (parameters.stream().anyMatch(this::unsupportedTopLevelFunctionAbi)
            || unsupportedTopLevelFunctionAbi(returnType)) {
            error("MPL3602", "函数 ABI 当前支持标量、Int/Float/Bool 元组及固定形状的数值/Bool 数组；其他集合和对象聚合尚不支持",
                function.span());
        }
        boolean returnsOwnedObject = returnType instanceof ObjectType object && !object.nullable()
            && ownedObjectFactoryAnalyzer.returnsFreshObject(function.body());
        List<FunctionSignature> overloads = functionOverloads.computeIfAbsent(function.name(), ignored -> new ArrayList<>());
        if (hardwareLinks.containsKey(function.name()) || classes.containsKey(function.name())) {
            error("MPL3501", "函数名称与现有符号冲突：" + function.name(), function.span());
        }
        if (overloads.stream().anyMatch(existing -> existing.parameterTypes().equals(parameters))) {
            error("MPL3510", "函数签名重复：" + callableDisplay(function.name(), parameters), function.span());
            return;
        }
        String internalName = topLevelFunctionCounts.getOrDefault(function.name(), 1L) == 1L
            ? function.name() : "__mpl_overload_" + function.name() + "_" + overloads.size();
        FunctionSignature signature = new FunctionSignature(function.name(), internalName, function,
            parameters, returnType, returnsOwnedObject);
        overloads.add(signature);
        functions.put(internalName, signature);
        declarationFunctions.put(function, signature);
        callGraph.putIfAbsent(internalName, new HashSet<>());
        directGlobalDependencies.putIfAbsent(internalName, new HashSet<>());
    }

    /**
     * Kotlin-style return inference for top-level functions.  Signatures are
     * registered first, then inferred to a fixed point, so a function may use
     * a later declaration whose own result can already be determined.
     * Unresolvable paths deliberately remain a compile-time error instead of
     * becoming mlog's untyped numeric value.
     */
    private void inferImplicitFunctionReturnTypes(List<FunctionDeclaration> declarations) {
        boolean changed;
        int remainingPasses = declarations.size() + 1;
        do {
            changed = false;
            for (FunctionDeclaration declaration : declarations) {
                if (declaration.returnType().isPresent()) continue;
                FunctionSignature current = declarationFunctions.get(declaration);
                if (current == null || current.returnType() != ValueType.ERROR) continue;
                MplType inferred = inferFunctionReturnType(declaration, current.parameterTypes());
                if (inferred == ValueType.ERROR) continue;
                replaceFunctionSignature(current, inferred);
                changed = true;
            }
        } while (changed && --remainingPasses > 0);

        for (FunctionDeclaration declaration : declarations) {
            if (declaration.returnType().isPresent()) continue;
            FunctionSignature signature = declarationFunctions.get(declaration);
            if (signature != null && signature.returnType() == ValueType.ERROR) {
                error("MPL3503", "无法推导函数 " + declaration.name()
                    + " 的返回类型；请显式标注类型", declaration.span());
            }
        }
    }

    /**
     * Infers the fixed layout used by array parameters and results without
     * making the length part of MPL's public {@code T[]} type identity.
     */
    private void inferFunctionAggregateShapes(Program program) {
        boolean hasArrayAbi = false;
        for (FunctionSignature function : functions.values()) {
            for (int index = 0; index < function.parameterTypes().size(); index++) {
                if (!isFunctionArray(function.parameterTypes().get(index))) continue;
                aggregateParameterSizes.put(new FunctionParameterShapeKey(function.internalName(), index), 0);
                hasArrayAbi = true;
            }
            if (isFunctionArray(function.returnType())) {
                aggregateReturnSizes.put(function.internalName(), 0);
                hasArrayAbi = true;
            }
        }
        if (!hasArrayAbi) return;

        int maximumRounds = Math.max(4, (program.functions().size() + 1) * 4);
        for (int round = 0; round < maximumRounds; round++) {
            ShapeEnvironment globals = new ShapeEnvironment();
            boolean changed = shapeStatements(program.statements(), globals, null);
            for (FunctionDeclaration declaration : program.functions()) {
                FunctionSignature signature = declarationFunctions.get(declaration);
                if (signature == null) continue;
                ShapeEnvironment environment = globals.copy();
                for (int index = 0; index < declaration.parameters().size(); index++) {
                    FunctionParameter parameter = declaration.parameters().get(index);
                    MplType type = signature.parameterTypes().get(index);
                    environment.types.put(parameter.name(), type);
                    if (isFunctionArray(type)) {
                        environment.aggregateSizes.put(parameter.name(), aggregateParameterSize(signature, index));
                    }
                }
                changed |= shapeStatements(declaration.body().statements(), environment, signature);
            }
            if (!changed) break;
            if (round == maximumRounds - 1) {
                error("MPL3602", "数组函数 ABI 的编译期形状推导未收敛", program.functions().get(0).span());
            }
        }

        for (FunctionSignature function : functions.values()) {
            for (int index = 0; index < function.parameterTypes().size(); index++) {
                if (isFunctionArray(function.parameterTypes().get(index)) && aggregateParameterSize(function, index) == 0) {
                    error("MPL3602", "无法推导函数 " + function.sourceName() + " 的数组参数 "
                        + (index + 1) + " 的固定长度；它必须至少有一个可证明的调用点", function.declaration().span());
                }
            }
            if (isFunctionArray(function.returnType()) && aggregateReturnSize(function) == 0) {
                error("MPL3602", "无法推导函数 " + function.sourceName() + " 的数组返回长度",
                    function.declaration().span());
            }
        }
    }

    private boolean shapeStatements(List<Statement> statements, ShapeEnvironment environment,
                                    FunctionSignature current) {
        boolean changed = false;
        for (Statement statement : statements) {
            if (statement instanceof VariableDeclaration declaration) {
                ShapeResult initializer = shapeExpression(declaration.initializer(), environment);
                changed |= initializer.changed();
                MplType type = declaration.declaredType()
                    .map(value -> parseType(value, declaration.span()))
                    .orElseGet(() -> inferExpressionType(declaration.initializer(), environment.types));
                environment.types.put(declaration.name(), type);
                if (isFunctionArray(type)) environment.aggregateSizes.put(declaration.name(), initializer.size());
            } else if (statement instanceof ExpressionStatement expression) {
                changed |= shapeExpression(expression.expression(), environment).changed();
            } else if (statement instanceof ReturnStatement returned) {
                if (returned.value().isPresent()) {
                    ShapeResult value = shapeExpression(returned.value().orElseThrow(), environment);
                    changed |= value.changed();
                    if (current != null && isFunctionArray(current.returnType()) && value.size() > 0) {
                        changed |= mergeAggregateReturnSize(current, value.size(), returned.span());
                    }
                }
            } else if (statement instanceof BlockStatement block) {
                changed |= shapeStatements(block.statements(), environment.copy(), current);
            } else if (statement instanceof IfStatement branch) {
                changed |= shapeExpression(branch.condition(), environment).changed();
                changed |= shapeStatements(branch.thenBlock().statements(), environment.copy(), current);
                if (branch.elseBranch().isPresent()) {
                    changed |= shapeStatements(List.of(branch.elseBranch().orElseThrow()), environment.copy(), current);
                }
            } else if (statement instanceof WhileStatement loop) {
                changed |= shapeExpression(loop.condition(), environment).changed();
                changed |= shapeStatements(loop.body().statements(), environment.copy(), current);
            } else if (statement instanceof DoWhileStatement loop) {
                changed |= shapeStatements(loop.body().statements(), environment.copy(), current);
                changed |= shapeExpression(loop.condition(), environment).changed();
            } else if (statement instanceof ForStatement loop) {
                ShapeEnvironment body = environment.copy();
                if (loop.declarationInitializer().isPresent()) {
                    changed |= shapeStatements(List.of(loop.declarationInitializer().orElseThrow()), body, current);
                }
                if (loop.expressionInitializer().isPresent()) {
                    changed |= shapeExpression(loop.expressionInitializer().orElseThrow(), body).changed();
                }
                if (loop.condition().isPresent()) changed |= shapeExpression(loop.condition().orElseThrow(), body).changed();
                changed |= shapeStatements(loop.body().statements(), body, current);
                if (loop.update().isPresent()) changed |= shapeExpression(loop.update().orElseThrow(), body).changed();
            } else if (statement instanceof ForEachStatement loop) {
                changed |= shapeExpression(loop.iterable(), environment).changed();
                changed |= shapeStatements(loop.body().statements(), environment.copy(), current);
            } else if (statement instanceof com.arc.mpl.ast.MilMacroBlockStatement macro) {
                changed |= shapeExpression(macro.macro(), environment).changed();
                changed |= shapeStatements(macro.body().statements(), environment.copy(), current);
            } else if (statement instanceof com.arc.mpl.ast.MilDrawStatement draw) {
                for (Expression argument : draw.arguments()) changed |= shapeExpression(argument, environment).changed();
            }
        }
        return changed;
    }

    private ShapeResult shapeExpression(Expression expression, ShapeEnvironment environment) {
        if (expression instanceof ArrayLiteral array) {
            boolean changed = false;
            for (Expression element : array.elements()) changed |= shapeExpression(element, environment).changed();
            return new ShapeResult(array.elements().size(), changed);
        }
        if (expression instanceof Identifier identifier) {
            return new ShapeResult(environment.aggregateSizes.getOrDefault(identifier.name(), 0), false);
        }
        if (expression instanceof CallExpression call) {
            List<ShapeResult> argumentShapes = call.arguments().stream()
                .map(argument -> shapeExpression(argument, environment)).toList();
            boolean changed = argumentShapes.stream().anyMatch(ShapeResult::changed);
            if (call.callee() instanceof Identifier function) {
                List<MplType> argumentTypes = call.arguments().stream()
                    .map(argument -> inferExpressionType(argument, environment.types)).toList();
                FunctionSignature selected = selectInferredOverload(
                    functionOverloads.getOrDefault(function.name(), List.of()),
                    FunctionSignature::parameterTypes, FunctionSignature::returnType, argumentTypes);
                if (selected != null) {
                    for (int index = 0; index < selected.parameterTypes().size(); index++) {
                        if (isFunctionArray(selected.parameterTypes().get(index)) && argumentShapes.get(index).size() > 0) {
                            changed |= mergeAggregateParameterSize(selected, index, argumentShapes.get(index).size(), call.span());
                        }
                    }
                    return new ShapeResult(aggregateReturnSize(selected), changed);
                }
            }
            changed |= shapeExpression(call.callee(), environment).changed();
            return new ShapeResult(0, changed);
        }
        if (expression instanceof AssignmentExpression assignment) {
            ShapeResult value = shapeExpression(assignment.value(), environment);
            if (value.size() > 0) environment.aggregateSizes.put(assignment.target().name(), value.size());
            return value;
        }
        if (expression instanceof BinaryExpression binary) {
            ShapeResult left = shapeExpression(binary.left(), environment);
            ShapeResult right = shapeExpression(binary.right(), environment);
            return new ShapeResult(0, left.changed() || right.changed());
        }
        if (expression instanceof UnaryExpression unary) return shapeExpression(unary.operand(), environment).withoutSize();
        if (expression instanceof IndexExpression access) {
            ShapeResult target = shapeExpression(access.target(), environment);
            ShapeResult index = shapeExpression(access.index(), environment);
            return new ShapeResult(0, target.changed() || index.changed());
        }
        if (expression instanceof TupleLiteral tuple) {
            boolean changed = false;
            for (Expression element : tuple.elements()) changed |= shapeExpression(element, environment).changed();
            return new ShapeResult(0, changed);
        }
        if (expression instanceof MemberAccessExpression member) {
            return shapeExpression(member.target(), environment).withoutSize();
        }
        if (expression instanceof MemberAssignmentExpression assignment) {
            ShapeResult target = shapeExpression(assignment.target(), environment);
            ShapeResult value = shapeExpression(assignment.value(), environment);
            return new ShapeResult(0, target.changed() || value.changed());
        }
        if (expression instanceof LambdaExpression lambda) return shapeExpression(lambda.body(), environment).withoutSize();
        if (expression instanceof NewExpression allocation) {
            boolean changed = false;
            for (Expression argument : allocation.arguments()) changed |= shapeExpression(argument, environment).changed();
            return new ShapeResult(0, changed);
        }
        if (expression instanceof MethodCallExpression call) {
            boolean changed = false;
            for (Expression argument : call.arguments()) changed |= shapeExpression(argument, environment).changed();
            return new ShapeResult(0, changed);
        }
        if (expression instanceof com.arc.mpl.ast.MilMacroCallExpression call) {
            boolean changed = false;
            for (Expression argument : call.arguments()) changed |= shapeExpression(argument, environment).changed();
            return new ShapeResult(0, changed);
        }
        return new ShapeResult(0, false);
    }

    private boolean mergeAggregateParameterSize(FunctionSignature function, int index, int size, SourceSpan span) {
        FunctionParameterShapeKey key = new FunctionParameterShapeKey(function.internalName(), index);
        int previous = aggregateParameterSizes.getOrDefault(key, 0);
        if (previous == 0) {
            aggregateParameterSizes.put(key, size);
            return true;
        }
        if (previous != size) {
            String identity = function.internalName() + ":arg" + index;
            if (aggregateShapeErrors.add(identity)) {
                error("MPL3602", "函数 " + function.sourceName() + " 的数组参数 " + (index + 1)
                    + " 同时接收了长度 " + previous + " 和 " + size
                    + "；当前一个 ABI 专门化必须使用唯一固定形状", span);
            }
        }
        return false;
    }

    private boolean mergeAggregateReturnSize(FunctionSignature function, int size, SourceSpan span) {
        int previous = aggregateReturnSizes.getOrDefault(function.internalName(), 0);
        if (previous == 0) {
            aggregateReturnSizes.put(function.internalName(), size);
            return true;
        }
        if (previous != size) {
            String identity = function.internalName() + ":return";
            if (aggregateShapeErrors.add(identity)) {
                error("MPL3602", "函数 " + function.sourceName() + " 的数组返回路径具有不同长度："
                    + previous + " 与 " + size, span);
            }
        }
        return false;
    }

    private int aggregateParameterSize(FunctionSignature function, int index) {
        return aggregateParameterSizes.getOrDefault(
            new FunctionParameterShapeKey(function.internalName(), index), 0);
    }

    private int aggregateReturnSize(FunctionSignature function) {
        return aggregateReturnSizes.getOrDefault(function.internalName(), 0);
    }

    private void replaceFunctionSignature(FunctionSignature previous, MplType returnType) {
        if (unsupportedTopLevelFunctionAbi(returnType)) {
            error("MPL3602", "函数 ABI 当前支持标量、Int/Float/Bool 元组及固定形状的数值/Bool 数组；该返回类型尚不支持",
                previous.declaration().span());
            return;
        }
        boolean returnsOwnedObject = returnType instanceof ObjectType object && !object.nullable()
            && ownedObjectFactoryAnalyzer.returnsFreshObject(previous.declaration().body());
        FunctionSignature replacement = new FunctionSignature(previous.sourceName(), previous.internalName(),
            previous.declaration(), previous.parameterTypes(), returnType, returnsOwnedObject);
        functions.put(replacement.internalName(), replacement);
        declarationFunctions.put(replacement.declaration(), replacement);
        List<FunctionSignature> overloads = functionOverloads.get(replacement.sourceName());
        if (overloads != null) {
            for (int index = 0; index < overloads.size(); index++) {
                if (overloads.get(index).internalName().equals(replacement.internalName())) {
                    overloads.set(index, replacement);
                    break;
                }
            }
        }
    }

    private MplType inferFunctionReturnType(FunctionDeclaration declaration, List<MplType> parameterTypes) {
        Map<String, MplType> locals = new HashMap<>();
        if (inferringClass != null) locals.put("this", new ObjectType(inferringClass, false));
        for (int index = 0; index < declaration.parameters().size(); index++) {
            locals.put(declaration.parameters().get(index).name(), parameterTypes.get(index));
        }
        List<Optional<MplType>> returns = new ArrayList<>();
        inferReturnTypes(declaration.body().statements(), locals, returns);
        if (returns.isEmpty()) return ValueType.VOID;
        boolean emptyReturn = returns.stream().anyMatch(Optional::isEmpty);
        if (emptyReturn) return returns.stream().allMatch(Optional::isEmpty) ? ValueType.VOID : ValueType.ERROR;
        MplType result = null;
        for (Optional<MplType> returned : returns) {
            MplType candidate = returned.orElseThrow();
            if (candidate == ValueType.ERROR) return ValueType.ERROR;
            result = result == null ? candidate : commonInferenceType(result, candidate);
            if (result == ValueType.ERROR) return ValueType.ERROR;
        }
        return result == null ? ValueType.VOID : result;
    }

    private void inferReturnTypes(List<Statement> statements, Map<String, MplType> locals,
                                  List<Optional<MplType>> returns) {
        for (Statement statement : statements) {
            if (statement instanceof VariableDeclaration declaration) {
                MplType initializer = inferExpressionType(declaration.initializer(), locals);
                MplType declared = declaration.declaredType().map(type -> parseType(type, declaration.span()))
                    .orElse(initializer);
                locals.put(declaration.name(), declared);
            } else if (statement instanceof ReturnStatement returned) {
                returns.add(returned.value().map(value -> inferExpressionType(value, locals)));
            } else if (statement instanceof BlockStatement block) {
                inferReturnTypes(block.statements(), new HashMap<>(locals), returns);
            } else if (statement instanceof IfStatement branch) {
                inferReturnTypes(branch.thenBlock().statements(), new HashMap<>(locals), returns);
                branch.elseBranch().ifPresent(alternative -> inferReturnTypes(List.of(alternative), new HashMap<>(locals), returns));
            } else if (statement instanceof WhileStatement loop) {
                inferReturnTypes(loop.body().statements(), new HashMap<>(locals), returns);
            } else if (statement instanceof DoWhileStatement loop) {
                inferReturnTypes(loop.body().statements(), new HashMap<>(locals), returns);
            } else if (statement instanceof ForStatement loop) {
                Map<String, MplType> loopLocals = new HashMap<>(locals);
                loop.declarationInitializer().ifPresent(initializer -> loopLocals.put(initializer.name(),
                    initializer.declaredType().map(type -> parseType(type, initializer.span()))
                        .orElseGet(() -> inferExpressionType(initializer.initializer(), loopLocals))));
                inferReturnTypes(loop.body().statements(), loopLocals, returns);
            }
        }
    }

    private MplType inferExpressionType(Expression expression, Map<String, MplType> locals) {
        if (expression instanceof IntegerLiteral) return ValueType.INT;
        if (expression instanceof FloatLiteral) return ValueType.FLOAT;
        if (expression instanceof BooleanLiteral) return ValueType.BOOL;
        if (expression instanceof StringLiteral) return ValueType.STRING;
        if (expression instanceof NullLiteral) return ValueType.NULL;
        if (expression instanceof Identifier identifier) return locals.getOrDefault(identifier.name(), ValueType.ERROR);
        if (expression instanceof ArrayLiteral array) {
            if (array.elements().isEmpty()) return ValueType.ERROR;
            List<MplType> elementTypes = array.elements().stream()
                .map(element -> inferExpressionType(element, locals)).toList();
            MplType elementType = commonInferredElementType(elementTypes);
            return elementType == ValueType.ERROR ? ValueType.ERROR
                : new CollectionType(CollectionType.Kind.ARRAY, elementType);
        }
        if (expression instanceof TupleLiteral tuple) {
            List<MplType> elementTypes = tuple.elements().stream()
                .map(element -> inferExpressionType(element, locals)).toList();
            if (elementTypes.stream().anyMatch(type -> type == ValueType.ERROR || type == ValueType.VOID)) {
                return ValueType.ERROR;
            }
            return new TupleType(elementTypes);
        }
        if (expression instanceof IndexExpression access) {
            MplType target = inferExpressionType(access.target(), locals);
            if (target instanceof TupleType tuple && access.index() instanceof IntegerLiteral index
                && index.value() >= 0 && index.value() < tuple.elementTypes().size()) {
                return tuple.elementTypes().get((int) index.value());
            }
            if (target instanceof CollectionType collection) return collection.elementType();
            return ValueType.ERROR;
        }
        if (expression instanceof MemberAccessExpression member) {
            MplType receiver = inferExpressionType(member.target(), locals);
            if (receiver instanceof ObjectType object && !object.nullable()) {
                ClassInfo type = classes.get(object.className());
                FieldInfo field = type == null ? null : lookupField(type, member.member(), false);
                return field == null ? ValueType.ERROR : field.type();
            }
            return ValueType.ERROR;
        }
        if (expression instanceof NewExpression allocation) {
            return classes.containsKey(allocation.className()) ? new ObjectType(allocation.className(), false) : ValueType.ERROR;
        }
        if (expression instanceof UnaryExpression unary) {
            MplType operand = inferExpressionType(unary.operand(), locals);
            return "!".equals(unary.operator()) ? ValueType.BOOL : operand;
        }
        if (expression instanceof AssignmentExpression assignment) {
            MplType value = inferExpressionType(assignment.value(), locals);
            locals.put(assignment.target().name(), value);
            return value;
        }
        if (expression instanceof BinaryExpression binary) {
            MplType left = inferExpressionType(binary.left(), locals);
            MplType right = inferExpressionType(binary.right(), locals);
            return switch (binary.operator()) {
                case "==", "!=", "===", "!==", "<", "<=", ">", ">=", "&&", "||" -> ValueType.BOOL;
                case "+" -> left == ValueType.STRING || right == ValueType.STRING ? ValueType.STRING
                    : numericInferenceType(left, right);
                case "-", "*", "%" -> numericInferenceType(left, right);
                case "/" -> left == ValueType.ERROR || right == ValueType.ERROR ? ValueType.ERROR : ValueType.FLOAT;
                default -> ValueType.ERROR;
            };
        }
        if (expression instanceof CallExpression call && call.callee() instanceof Identifier function) {
            List<MplType> arguments = call.arguments().stream().map(value -> inferExpressionType(value, locals)).toList();
            FunctionSignature selected = selectInferredOverload(functionOverloads.getOrDefault(function.name(), List.of()),
                FunctionSignature::parameterTypes, FunctionSignature::returnType, arguments);
            return selected == null ? ValueType.ERROR : selected.returnType();
        }
        if (expression instanceof CallExpression call && call.callee() instanceof MemberAccessExpression member) {
            List<MplType> arguments = call.arguments().stream().map(value -> inferExpressionType(value, locals)).toList();
            if (member.target() instanceof Identifier identifier && "super".equals(identifier.name()) && inferringClass != null) {
                ClassInfo owner = classes.get(inferringClass);
                ClassInfo parent = owner == null ? null : owner.parent();
                if (parent == null) return ValueType.ERROR;
                MethodInfo selected = selectInferredOverload(methodCandidates(parent, member.member()).stream()
                        .filter(MethodInfo::publicAccess).toList(), MethodInfo::parameterTypes, MethodInfo::returnType,
                    arguments);
                return selected == null ? ValueType.ERROR : selected.returnType();
            }
            MplType receiver = inferExpressionType(member.target(), locals);
            if (!(receiver instanceof ObjectType object) || object.nullable()) return ValueType.ERROR;
            ClassInfo type = classes.get(object.className());
            if (type == null) return ValueType.ERROR;
            MethodInfo selected = selectInferredOverload(methodCandidates(type, member.member()), MethodInfo::parameterTypes,
                MethodInfo::returnType, arguments);
            return selected == null ? ValueType.ERROR : selected.returnType();
        }
        return ValueType.ERROR;
    }

    private <T> T selectInferredOverload(List<T> candidates,
                                         java.util.function.Function<T, List<MplType>> parameters,
                                         java.util.function.Function<T, MplType> returnType,
                                         List<MplType> arguments) {
        List<T> applicable = candidates.stream()
            .filter(candidate -> returnType.apply(candidate) != ValueType.ERROR)
            .filter(candidate -> parameters.apply(candidate).size() == arguments.size())
            .filter(candidate -> java.util.stream.IntStream.range(0, arguments.size())
                .allMatch(index -> canAssign(parameters.apply(candidate).get(index), arguments.get(index))))
            .toList();
        if (applicable.size() == 1) return applicable.get(0);
        List<T> mostSpecific = applicable.stream().filter(candidate -> applicable.stream().noneMatch(other ->
            other != candidate && moreSpecific(parameters.apply(other), parameters.apply(candidate)))).toList();
        return mostSpecific.size() == 1 ? mostSpecific.get(0) : null;
    }

    private MplType numericInferenceType(MplType left, MplType right) {
        if (left == ValueType.ERROR || right == ValueType.ERROR) return ValueType.ERROR;
        if (left == ValueType.FLOAT || right == ValueType.FLOAT) return ValueType.FLOAT;
        return left == ValueType.INT && right == ValueType.INT ? ValueType.INT : ValueType.ERROR;
    }

    private MplType commonInferredElementType(List<MplType> types) {
        if (types.isEmpty()) return ValueType.ERROR;
        MplType result = types.get(0);
        for (int index = 1; index < types.size(); index++) {
            result = commonInferenceType(result, types.get(index));
            if (result == ValueType.ERROR) return ValueType.ERROR;
        }
        return result;
    }

    private MplType commonInferenceType(MplType left, MplType right) {
        if (left.equals(right)) return left;
        if ((left == ValueType.INT && right == ValueType.FLOAT) || (left == ValueType.FLOAT && right == ValueType.INT)) {
            return ValueType.FLOAT;
        }
        if (left instanceof TupleType leftTuple && right instanceof TupleType rightTuple
            && leftTuple.elementTypes().size() == rightTuple.elementTypes().size()) {
            List<MplType> elements = new ArrayList<>();
            for (int index = 0; index < leftTuple.elementTypes().size(); index++) {
                MplType element = commonInferenceType(leftTuple.elementTypes().get(index),
                    rightTuple.elementTypes().get(index));
                if (element == ValueType.ERROR) return ValueType.ERROR;
                elements.add(element);
            }
            return new TupleType(elements);
        }
        if (left instanceof ObjectType leftObject && right instanceof ObjectType rightObject) {
            String commonClass = commonObjectSupertype(leftObject.className(), rightObject.className());
            if (commonClass != null) return new ObjectType(commonClass, leftObject.nullable() || rightObject.nullable());
        }
        if (left == ValueType.NULL && right instanceof ObjectType object) return new ObjectType(object.className(), true);
        if (right == ValueType.NULL && left instanceof ObjectType object) return new ObjectType(object.className(), true);
        return ValueType.ERROR;
    }

    private String commonObjectSupertype(String left, String right) {
        Set<String> leftAncestors = new LinkedHashSet<>();
        ClassInfo current = classes.get(left);
        while (current != null && leftAncestors.add(current.name())) current = current.parent();
        current = classes.get(right);
        while (current != null) {
            if (leftAncestors.contains(current.name())) return current.name();
            current = current.parent();
        }
        return null;
    }

    private HirFunction analyzeMethod(ClassInfo type, MethodInfo method) {
        FunctionDeclaration function = method.declaration();
        String previousFunction = currentFunction;
        String previousClass = currentClass;
        MplType previousReturnType = currentReturnType;
        boolean previousReturnsOwnedObject = currentReturnsOwnedObject;
        MethodInfo previousMethod = currentMethod;
        currentFunction = method.internalName();
        currentClass = type.name();
        currentReturnType = method.returnType();
        currentReturnsOwnedObject = false;
        currentMethod = method;
        scopes.push(new HashMap<>());
        try {
            ObjectType thisType = new ObjectType(type.name(), false);
            declare("this", new Symbol(thisType, false, null, null, null, null, false, false, function.span()), function.span());
            List<HirFunctionParameter> parameters = new ArrayList<>();
            parameters.add(new HirFunctionParameter("this", thisType));
            for (int index = 0; index < function.parameters().size(); index++) {
                FunctionParameter parameter = function.parameters().get(index);
                MplType parameterType = method.parameterTypes().get(index);
                Integer aggregateSize = parameterType instanceof TupleType tuple
                    ? tuple.elementTypes().size() : null;
                declare(parameter.name(), new Symbol(parameterType, false,
                    parameterType == ValueType.STRING ? profile.maxMessageUtf16CodeUnits() : null,
                    aggregateSize, null, null, false, false,
                    parameter.span()), parameter.span());
                parameters.add(new HirFunctionParameter(parameter.name(), parameterType,
                    aggregateSize == null ? 0 : aggregateSize));
            }
            if (method.constructor()) validateConstructorContract(type, function);
            List<HirStatement> body = analyzeBlock(function.body());
            if (method.returnType() != ValueType.VOID && !guaranteesReturn(body)) {
                error("MPL3504", "方法 " + type.name() + "." + method.sourceName()
                    + " 并非所有路径都返回 " + display(method.returnType()), function.span());
            }
            int aggregateReturnSize = method.returnType() instanceof TupleType tuple
                ? tuple.elementTypes().size() : 0;
            return new HirFunction(method.internalName(), method.sourceName(), parameters, method.returnType(),
                aggregateReturnSize, body);
        } finally {
            scopes.pop();
            currentFunction = previousFunction;
            currentClass = previousClass;
            currentReturnType = previousReturnType;
            currentReturnsOwnedObject = previousReturnsOwnedObject;
            currentMethod = previousMethod;
        }
    }

    private void validateConstructorContract(ClassInfo type, FunctionDeclaration constructor) {
        boolean startsWithSuper = !constructor.body().statements().isEmpty()
            && isSuperConstructorStatement(constructor.body().statements().get(0));
        long superCalls = constructor.body().statements().stream().filter(this::isSuperConstructorStatement).count();
        if (type.parent() == null && superCalls > 0) {
            error("MPL3715", "无父类的构造器不能调用 super(...)：" + type.name(), constructor.span());
        }
        if (type.parent() != null && (!startsWithSuper || superCalls != 1)) {
            error("MPL3715", "派生类构造器必须以且只能以一次 super(...) 开头：" + type.name(), constructor.span());
        }
        validateConstructorInitialization(type, constructor);
    }

    private boolean isSuperConstructorStatement(Statement statement) {
        return statement instanceof ExpressionStatement expression
            && expression.expression() instanceof CallExpression call
            && call.callee() instanceof Identifier identifier
            && "super".equals(identifier.name());
    }

    private void validateConstructorInitialization(ClassInfo type, FunctionDeclaration constructor) {
        Set<String> inherited = type.parent() == null ? Set.of() : type.parent().effectiveFields().keySet();
        Set<String> assigned = definitelyAssignedFields(constructor.body().statements(), inherited, type);
        Set<String> missing = new java.util.TreeSet<>(type.fields().keySet());
        missing.removeAll(assigned);
        if (!missing.isEmpty()) {
            error("MPL3704", "构造器 " + type.name() + " 未在所有路径初始化字段："
                + String.join("、", missing), constructor.span());
        }
    }

    private Set<String> definitelyAssignedFields(List<Statement> statements, Set<String> incoming, ClassInfo type) {
        Set<String> assigned = new HashSet<>(incoming);
        for (Statement statement : statements) {
            if (statement instanceof ExpressionStatement expression
                && expression.expression() instanceof MemberAssignmentExpression assignment
                && assignment.target() instanceof Identifier target && "this".equals(target.name())
                && type.effectiveFields().containsKey(assignment.member())) {
                if (!"=".equals(assignment.operator()) && !assigned.contains(assignment.member())) {
                    error("MPL3704", "字段 " + assignment.member() + " 在初始化前被读取", assignment.span());
                }
                validateConstructorReads(assignment.value(), assigned, type);
                if ("=".equals(assignment.operator())) assigned.add(assignment.member());
                continue;
            }
            if (statement instanceof ExpressionStatement expression) {
                validateConstructorReads(expression.expression(), assigned, type);
                continue;
            }
            if (statement instanceof VariableDeclaration declaration) {
                validateConstructorReads(declaration.initializer(), assigned, type);
                continue;
            }
            if (statement instanceof BlockStatement block) {
                assigned = new HashSet<>(definitelyAssignedFields(block.statements(), assigned, type));
                continue;
            }
            if (statement instanceof IfStatement branch) {
                validateConstructorReads(branch.condition(), assigned, type);
                Set<String> branchInput = Set.copyOf(assigned);
                Set<String> thenAssigned = definitelyAssignedFields(branch.thenBlock().statements(), branchInput, type);
                Set<String> elseAssigned = branch.elseBranch()
                    .map(alternative -> definitelyAssignedFields(List.of(alternative), branchInput, type))
                    .orElse(branchInput);
                assigned = new HashSet<>(thenAssigned);
                assigned.retainAll(elseAssigned);
                continue;
            }
            if (statement instanceof DoWhileStatement loop) {
                assigned = new HashSet<>(definitelyAssignedFields(loop.body().statements(), assigned, type));
                validateConstructorReads(loop.condition(), assigned, type);
                continue;
            }
            if (statement instanceof WhileStatement loop) {
                validateConstructorReads(loop.condition(), assigned, type);
                definitelyAssignedFields(loop.body().statements(), assigned, type);
                continue;
            }
            if (statement instanceof ForStatement loop) {
                if (loop.declarationInitializer().isPresent()) {
                    validateConstructorReads(loop.declarationInitializer().orElseThrow().initializer(), assigned, type);
                }
                if (loop.expressionInitializer().isPresent()) {
                    validateConstructorReads(loop.expressionInitializer().orElseThrow(), assigned, type);
                }
                if (loop.condition().isPresent()) {
                    validateConstructorReads(loop.condition().orElseThrow(), assigned, type);
                }
                definitelyAssignedFields(loop.body().statements(), assigned, type);
                if (loop.update().isPresent()) validateConstructorReads(loop.update().orElseThrow(), assigned, type);
                continue;
            }
            if (statement instanceof ForEachStatement loop) {
                validateConstructorReads(loop.iterable(), assigned, type);
                definitelyAssignedFields(loop.body().statements(), assigned, type);
                continue;
            }
            if (statement instanceof ReturnStatement returned) {
                if (returned.value().isPresent()) {
                    validateConstructorReads(returned.value().orElseThrow(), assigned, type);
                }
                Set<String> missing = new java.util.TreeSet<>(type.fields().keySet());
                missing.removeAll(assigned);
                if (!missing.isEmpty()) {
                    error("MPL3704", "构造器提前返回前未初始化字段：" + String.join("、", missing), statement.span());
                }
            }
        }
        return Set.copyOf(assigned);
    }

    private void validateConstructorReads(Expression expression, Set<String> assigned, ClassInfo type) {
        if (expression instanceof MemberAccessExpression member) {
            if (member.target() instanceof Identifier target && "this".equals(target.name())
                && type.effectiveFields().containsKey(member.member()) && !assigned.contains(member.member())) {
                error("MPL3704", "字段 " + member.member() + " 在初始化前被读取", member.span());
            }
            validateConstructorReads(member.target(), assigned, type);
            return;
        }
        if (expression instanceof MemberAssignmentExpression assignment) {
            validateConstructorReads(assignment.target(), assigned, type);
            validateConstructorReads(assignment.value(), assigned, type);
            return;
        }
        if (expression instanceof AssignmentExpression assignment) {
            validateConstructorReads(assignment.value(), assigned, type);
            return;
        }
        if (expression instanceof BinaryExpression binary) {
            validateConstructorReads(binary.left(), assigned, type);
            validateConstructorReads(binary.right(), assigned, type);
            return;
        }
        if (expression instanceof UnaryExpression unary) {
            validateConstructorReads(unary.operand(), assigned, type);
            return;
        }
        if (expression instanceof CallExpression call) {
            validateConstructorReads(call.callee(), assigned, type);
            call.arguments().forEach(value -> validateConstructorReads(value, assigned, type));
            return;
        }
        if (expression instanceof MethodCallExpression call) {
            call.arguments().forEach(value -> validateConstructorReads(value, assigned, type));
            return;
        }
        if (expression instanceof LambdaExpression lambda) {
            validateConstructorReads(lambda.body(), assigned, type);
            return;
        }
        if (expression instanceof NewExpression allocation) {
            allocation.arguments().forEach(value -> validateConstructorReads(value, assigned, type));
            return;
        }
        if (expression instanceof IndexExpression access) {
            validateConstructorReads(access.target(), assigned, type);
            validateConstructorReads(access.index(), assigned, type);
            return;
        }
        if (expression instanceof ArrayLiteral array) {
            array.elements().forEach(value -> validateConstructorReads(value, assigned, type));
            return;
        }
        if (expression instanceof TupleLiteral tuple) {
            tuple.elements().forEach(value -> validateConstructorReads(value, assigned, type));
        }
    }

    private HirFunction analyzeFunction(FunctionDeclaration function) {
        FunctionSignature signature = declarationFunctions.get(function);
        if (signature == null || signature.declaration() != function) {
            return new HirFunction(function.name(), List.of(), ValueType.ERROR, List.of());
        }
        String previousFunction = currentFunction;
        MplType previousReturnType = currentReturnType;
        boolean previousReturnsOwnedObject = currentReturnsOwnedObject;
        currentFunction = signature.internalName();
        currentReturnType = signature.returnType();
        currentReturnsOwnedObject = signature.returnsOwnedObject();
        scopes.push(new HashMap<>());
        try {
            List<HirFunctionParameter> parameters = new ArrayList<>();
            for (int index = 0; index < function.parameters().size(); index++) {
                FunctionParameter parameter = function.parameters().get(index);
                MplType type = signature.parameterTypes().get(index);
                Integer aggregateSize = type instanceof TupleType tuple ? tuple.elementTypes().size()
                    : isFunctionArray(type) ? aggregateParameterSize(signature, index) : null;
                declare(parameter.name(), new Symbol(type, false,
                    type == ValueType.STRING ? profile.maxMessageUtf16CodeUnits() : null,
                    aggregateSize, aggregateSize, null, null, false, false, false, parameter.span()), parameter.span());
                parameters.add(new HirFunctionParameter(parameter.name(), type,
                    isFunctionArray(type) ? aggregateParameterSize(signature, index) : 0));
            }
            List<HirStatement> body = analyzeBlock(function.body());
            if (signature.returnType() != ValueType.VOID && !guaranteesReturn(body)) {
                error("MPL3504", "函数 " + function.name() + " 并非所有路径都返回 "
                    + display(signature.returnType()), function.span());
            }
            return new HirFunction(signature.internalName(), signature.sourceName(), parameters, signature.returnType(),
                isFunctionArray(signature.returnType()) ? aggregateReturnSize(signature) : 0, body);
        } finally {
            scopes.pop();
            currentFunction = previousFunction;
            currentReturnType = previousReturnType;
            currentReturnsOwnedObject = previousReturnsOwnedObject;
        }
    }

    private HirStatement analyzeReturn(ReturnStatement returned) {
        if (currentFunction == null) {
            error("MPL3502", "return 只能出现在函数内", returned.span());
            return new HirReturn(Optional.empty());
        }
        Optional<HirExpression> value;
        if (returned.value().orElse(null) instanceof NewExpression && currentReturnsOwnedObject) {
            value = returned.value().map(expression -> analyzeWithObjectAllocationContext(
                ObjectAllocationContext.POOLED_RETURN, () -> analyzeExpression(expression)));
        } else {
            value = returned.value().map(this::analyzeExpression);
        }
        if (currentReturnType == ValueType.VOID && value.isPresent()) {
            error("MPL3503", "无返回值函数不能 return 表达式", returned.span());
        } else if (currentReturnType != ValueType.VOID && value.isEmpty()) {
            error("MPL3503", "函数 " + currentFunction + " 必须返回 " + display(currentReturnType), returned.span());
        } else if (value.isPresent() && !canAssign(currentReturnType, value.orElseThrow().type())) {
            error("MPL3503", "函数 " + currentFunction + " 不能返回 " + display(value.orElseThrow().type()), returned.span());
        }
        return new HirReturn(value, activeOwnerReleases(owner -> !owner.global()));
    }

    private boolean guaranteesReturn(List<HirStatement> statements) {
        for (HirStatement statement : statements) {
            if (statement instanceof HirReturn) return true;
            if (statement instanceof HirBlock block && guaranteesReturn(block.statements())) return true;
            if (statement instanceof HirIf branch && branch.elseBody().isPresent()
                && guaranteesReturn(branch.thenBody()) && guaranteesReturn(branch.elseBody().orElseThrow())) return true;
        }
        return false;
    }

    private void rejectRecursiveFunctions() {
        Map<String, Integer> states = new HashMap<>();
        for (String function : functions.keySet()) visitFunction(function, states, new ArrayDeque<>());
    }

    private void visitFunction(String function, Map<String, Integer> states, Deque<String> path) {
        if (states.getOrDefault(function, 0) == 2) return;
        if (states.getOrDefault(function, 0) == 1) {
            FunctionSignature signature = functions.get(function);
            error("MPL3505", "函数调用图存在递归环：" + String.join(" -> ", path) + " -> " + function,
                signature.declaration().span());
            return;
        }
        states.put(function, 1);
        path.addLast(function);
        for (String target : callGraph.getOrDefault(function, java.util.Set.of())) visitFunction(target, states, path);
        path.removeLast();
        states.put(function, 2);
    }

    private List<HirStatement> analyzeBlock(BlockStatement block) {
        scopes.push(new HashMap<>());
        List<PooledOwner> owners = new ArrayList<>();
        pooledOwnerScopes.push(owners);
        try {
            List<HirStatement> statements = new ArrayList<>();
            for (Statement statement : block.statements()) {
                statements.add(analyzeStatement(statement));
            }
            appendOwnerReleases(statements, owners);
            return List.copyOf(statements);
        } finally {
            pooledOwnerScopes.pop();
            scopes.pop();
        }
    }

    private HirStatement withLoopCleanup(HirStatement jump) {
        List<HirObjectRelease> releases = activeOwnerReleases(owner -> owner.loopDepth() == loopDepth);
        if (releases.isEmpty()) return jump;
        List<HirStatement> statements = new ArrayList<>(releases);
        statements.add(jump);
        return new HirBlock(statements);
    }

    private List<HirObjectRelease> activeOwnerReleases(java.util.function.Predicate<PooledOwner> predicate) {
        List<HirObjectRelease> releases = new ArrayList<>();
        for (List<PooledOwner> scope : pooledOwnerScopes) {
            for (int index = scope.size() - 1; index >= 0; index--) {
                PooledOwner owner = scope.get(index);
                if (predicate.test(owner)) releases.add(new HirObjectRelease(owner.variable(), owner.className()));
            }
        }
        return List.copyOf(releases);
    }

    private void appendOwnerReleases(List<HirStatement> statements, List<PooledOwner> owners) {
        for (int index = owners.size() - 1; index >= 0; index--) {
            PooledOwner owner = owners.get(index);
            if (!owner.global()) statements.add(new HirObjectRelease(owner.variable(), owner.className()));
        }
    }

    private HirStatement analyzeWhile(WhileStatement loop) {
        HirExpression condition = analyzeExpression(loop.condition());
        requireBool(condition.type(), loop.condition().span(), "while 条件");
        return new HirWhile(condition, analyzeLoopBlock(loop.body()));
    }

    private HirStatement analyzeDoWhile(DoWhileStatement loop) {
        List<HirStatement> body = analyzeLoopBlock(loop.body());
        HirExpression condition = analyzeExpression(loop.condition());
        requireBool(condition.type(), loop.condition().span(), "do-while 条件");
        return new HirDoWhile(body, condition);
    }

    private HirStatement analyzeIf(IfStatement branch) {
        HirExpression condition = analyzeExpression(branch.condition());
        requireBool(condition.type(), branch.condition().span(), "if 条件");
        aggregateControlDepth++;
        try {
            List<HirStatement> consequence = analyzeWithNarrowing(
                nonNullNarrowing(branch.condition(), true), () -> analyzeBlock(branch.thenBlock()));
            Optional<List<HirStatement>> alternative = branch.elseBranch().map(value -> analyzeWithNarrowing(
                nonNullNarrowing(branch.condition(), false), () -> analyzeAlternative(value)));
            return new HirIf(condition, consequence, alternative);
        } finally {
            aggregateControlDepth--;
        }
    }

    private Map<String, Symbol> nonNullNarrowing(Expression condition, boolean conditionResult) {
        if (!(condition instanceof BinaryExpression binary)
            || (!"==".equals(binary.operator()) && !"!=".equals(binary.operator()))) {
            return Map.of();
        }
        Identifier identifier;
        if (binary.left() instanceof Identifier left && binary.right() instanceof NullLiteral) {
            identifier = left;
        } else if (binary.right() instanceof Identifier right && binary.left() instanceof NullLiteral) {
            identifier = right;
        } else {
            return Map.of();
        }
        boolean nonNullBranch = "!=".equals(binary.operator()) == conditionResult;
        if (!nonNullBranch) return Map.of();
        Symbol symbol = lookup(identifier.name());
        if (symbol == null || symbol.mutable()) return Map.of();
        if (symbol.type() instanceof UnitType unit && unit.nullable()) {
            return Map.of(identifier.name(), symbol.withType(unit.nonNullable()));
        }
        if (symbol.type() instanceof BuildingType building && building.nullable()) {
            return Map.of(identifier.name(), symbol.withType(building.nonNullable()));
        }
        if (symbol.type() instanceof ObjectType object && object.nullable()) {
            return Map.of(identifier.name(), symbol.withType(new ObjectType(object.className(), false)));
        }
        return Map.of();
    }

    private <T> T analyzeWithNarrowing(Map<String, Symbol> narrowing, java.util.function.Supplier<T> analysis) {
        if (narrowing.isEmpty()) return analysis.get();
        scopes.push(new HashMap<>(narrowing));
        try {
            return analysis.get();
        } finally {
            scopes.pop();
        }
    }

    private List<HirStatement> analyzeAlternative(Statement alternative) {
        if (alternative instanceof BlockStatement block) return analyzeBlock(block);
        return List.of(analyzeStatement(alternative));
    }

    private List<HirStatement> analyzeLoopBlock(BlockStatement block) {
        loopDepth++;
        aggregateControlDepth++;
        try {
            return analyzeBlock(block);
        } finally {
            aggregateControlDepth--;
            loopDepth--;
        }
    }

    private HirStatement analyzeFor(ForStatement loop) {
        scopes.push(new HashMap<>());
        List<PooledOwner> owners = new ArrayList<>();
        pooledOwnerScopes.push(owners);
        try {
            Optional<HirVariableDeclaration> declarationInitializer = loop.declarationInitializer()
                .map(value -> (HirVariableDeclaration) analyzeDeclaration(value));
            Optional<HirExpression> expressionInitializer = loop.expressionInitializer().map(this::analyzeExpression);
            Optional<ArrayBoundsProof> boundsProof = countingArrayBoundsProof(loop);
            HirExpression condition = loop.condition().map(this::analyzeExpression)
                .orElseGet(() -> new HirConstant("1", ValueType.BOOL));
            loop.condition().ifPresent(value -> requireBool(condition.type(), value.span(), "for 条件"));
            Optional<HirExpression> update = loop.update().map(this::analyzeExpression);
            boundsProof.ifPresent(arrayBoundsProofs::push);
            List<HirStatement> body;
            try {
                body = analyzeLoopBlock(loop.body());
            } finally {
                boundsProof.ifPresent(ignored -> arrayBoundsProofs.pop());
            }
            HirFor analyzed = new HirFor(declarationInitializer, expressionInitializer, condition, update, body);
            if (owners.isEmpty()) return analyzed;
            List<HirStatement> statements = new ArrayList<>();
            statements.add(analyzed);
            appendOwnerReleases(statements, owners);
            return new HirBlock(statements);
        } finally {
            pooledOwnerScopes.pop();
            scopes.pop();
        }
    }

    /** Recognizes the first statically safe dynamic-index form: {@code for (var i = 0; i < array.size; i += 1)}. */
    private Optional<ArrayBoundsProof> countingArrayBoundsProof(ForStatement loop) {
        if (loop.declarationInitializer().isEmpty() || loop.condition().isEmpty() || loop.update().isEmpty()) {
            return Optional.empty();
        }
        VariableDeclaration declaration = loop.declarationInitializer().orElseThrow();
        if (!declaration.mutable() || !(declaration.initializer() instanceof IntegerLiteral start) || start.value() != 0) {
            return Optional.empty();
        }
        Symbol indexSymbol = lookup(declaration.name());
        if (indexSymbol == null || indexSymbol.type() != ValueType.INT) return Optional.empty();
        if (!(loop.condition().orElseThrow() instanceof BinaryExpression condition)
            || !"<".equals(condition.operator())
            || !(condition.left() instanceof Identifier index)
            || !declaration.name().equals(index.name())
            || !(condition.right() instanceof MemberAccessExpression size)
            || !"size".equals(size.member())
            || !(size.target() instanceof Identifier array)) {
            return Optional.empty();
        }
        Symbol arraySymbol = lookup(array.name());
        if (arraySymbol == null || !(arraySymbol.type() instanceof CollectionType collection)
            || (collection.kind() != CollectionType.Kind.ARRAY && collection.kind() != CollectionType.Kind.MUTABLE_LIST)
            || arraySymbol.staticAggregateSize() == null) {
            return Optional.empty();
        }
        if (!(loop.update().orElseThrow() instanceof AssignmentExpression update)
            || !declaration.name().equals(update.target().name())
            || !"+=".equals(update.operator())
            || !(update.value() instanceof IntegerLiteral step) || step.value() != 1) {
            return Optional.empty();
        }
        return Optional.of(new ArrayBoundsProof(declaration.name(), array.name()));
    }

    private HirStatement analyzeForEach(ForEachStatement loop) {
        HirUnitQuery savedQuery = resolveSavedUnitQuery(loop.iterable(), loop.name());
        if (savedQuery != null) return analyzeUnitForEach(loop, savedQuery);
        Optional<UnitQuery> query = parseUnitQuery(loop.iterable());
        if (query.isPresent()) {
            return analyzeUnitForEach(loop, query.orElseThrow());
        }
        HirBuildingQuery savedBuildingQuery = resolveSavedBuildingQuery(loop.iterable(), loop.name());
        if (savedBuildingQuery != null) return analyzeBuildingForEach(loop, savedBuildingQuery);
        Optional<BuildingQuery> buildingQuery = parseBuildingQuery(loop.iterable());
        if (buildingQuery.isPresent()) return analyzeBuildingForEach(loop, buildingQuery.orElseThrow());
        return analyzeAggregateForEach(loop);
    }

    private HirStatement analyzeUnitForEach(ForEachStatement loop, UnitQuery query) {
        if (currentFunction != null) error("MPL3508", "第一版函数不支持 Set<Unit<T>> 遍历", loop.span());
        if (unitIterationDepth > 0) {
            error("MPL3306", "第一版不支持嵌套 Unit 遍历", loop.span());
        }

        String previousBinding = activeUnitBinding;
        activeUnitBinding = loop.name();
        unitIterationDepth++;
        loopDepth++;
        scopes.push(new HashMap<>());
        try {
            declare(loop.name(), new Symbol(ValueType.UNIT, false, null, null, null, null, false, false, loop.span()), loop.span());
            List<HirExpression> filters = new ArrayList<>();
            for (Expression filter : query.filters()) {
                filters.add(analyzeUnitFilter(filter, loop.name()));
            }
            List<HirStatement> body = analyzeBlock(loop.body());
            TargetProfile.UnitType type = query.type();
            return new HirUnitIteration(
                loop.name(),
                query.typeName(),
                type.mlogName(),
                filters,
                query.managedLimit(),
                query.managedId(),
                body);
        } finally {
            scopes.pop();
            unitIterationDepth--;
            loopDepth--;
            activeUnitBinding = previousBinding;
        }
    }

    private HirStatement analyzeUnitForEach(ForEachStatement loop, HirUnitQuery query) {
        if (currentFunction != null) error("MPL3508", "第一版函数不支持 Set<Unit<T>> 遍历", loop.span());
        if (unitIterationDepth > 0) error("MPL3306", "第一版不支持嵌套 Unit 遍历", loop.span());

        String previousBinding = activeUnitBinding;
        activeUnitBinding = loop.name();
        unitIterationDepth++;
        loopDepth++;
        scopes.push(new HashMap<>());
        try {
            declare(loop.name(), new Symbol(ValueType.UNIT, false, null, null, null, null, false, false, loop.span()), loop.span());
            List<HirExpression> filters = query.filters().stream()
                .map(filter -> renameUnitBinding(filter, query.bindingName(), loop.name())).toList();
            return new HirUnitIteration(loop.name(), query.unitType(), query.mlogType(), filters,
                query.managedLimit(), query.managedId(), analyzeBlock(loop.body()));
        } finally {
            scopes.pop();
            unitIterationDepth--;
            loopDepth--;
            activeUnitBinding = previousBinding;
        }
    }

    private HirUnitQuery declaredUnitQuery(Expression expression) {
        if (!(expression instanceof Identifier identifier)) return null;
        Symbol symbol = lookup(identifier.name());
        return symbol == null ? null : symbol.unitQuery();
    }

    private HirUnitQuery resolveSavedUnitQuery(Expression expression, String bindingName) {
        List<CallExpression> modifiers = new ArrayList<>();
        Expression current = expression;
        while (current instanceof CallExpression call && call.callee() instanceof MemberAccessExpression member
            && ("where".equals(member.member()) || "take".equals(member.member()))) {
            modifiers.add(call);
            current = member.target();
        }
        HirUnitQuery base = declaredUnitQuery(current);
        if (base == null) return null;

        List<HirExpression> filters = new ArrayList<>(base.filters().stream()
            .map(filter -> renameUnitBinding(filter, base.bindingName(), bindingName)).toList());
        int managedLimit = base.managedLimit();
        int managedId = base.managedId();
        String previousBinding = activeUnitBinding;
        activeUnitBinding = bindingName;
        try {
            for (int index = modifiers.size() - 1; index >= 0; index--) {
                CallExpression modifier = modifiers.get(index);
                MemberAccessExpression member = (MemberAccessExpression) modifier.callee();
                if ("where".equals(member.member())) {
                    if (managedLimit != 0) {
                        error("MPL3307", "Set<Unit<T>> 的 where(...) 必须位于 take(n) 之前", modifier.span());
                        continue;
                    }
                    if (modifier.arguments().size() != 1) {
                        error("MPL3302", "Set<Unit<T>> 的 where(...) 需要恰好一个过滤 lambda", modifier.span());
                        continue;
                    }
                    filters.add(analyzeUnitFilter(modifier.arguments().get(0), bindingName));
                    continue;
                }
                if (managedLimit != 0) {
                    error("MPL3307", "一个 Set<Unit<T>> 查询只能调用一次 take(n)", modifier.span());
                    continue;
                }
                if (modifier.arguments().size() != 1 || !(modifier.arguments().get(0) instanceof IntegerLiteral literal)
                    || literal.value() <= 0 || literal.value() > Integer.MAX_VALUE) {
                    error("MPL3307", "Set<Unit<T>> 的 take(n) 只接受 1 到 2147483647 的 Int 字面量", modifier.span());
                    continue;
                }
                managedLimit = (int) literal.value();
                managedId = nextManagedQueryId++;
            }
        } finally {
            activeUnitBinding = previousBinding;
        }
        return new HirUnitQuery(bindingName, base.unitType(), base.mlogType(), filters, managedLimit, managedId);
    }

    private HirStatement analyzeBuildingForEach(ForEachStatement loop, BuildingQuery query) {
        String previousBinding = activeBuildingBinding;
        String previousType = activeBuildingType;
        activeBuildingBinding = loop.name();
        activeBuildingType = query.typeName();
        loopDepth++;
        scopes.push(new HashMap<>());
        try {
            declare(loop.name(), new Symbol(ValueType.BUILDING, false, null, null, null, null, false, false, loop.span()), loop.span());
            List<HirHardwareLink> buildings = linkedBuildings(query.typeName());
            List<HirExpression> filters = query.filters().stream()
                .map(filter -> analyzeBuildingFilter(filter, loop.name(), query.typeName())).toList();
            String mlogType = profile.buildingType(query.typeName()).orElseThrow().mlogName();
            return new HirBuildingIteration(loop.name(), query.typeName(), mlogType, buildings, filters,
                analyzeBlock(loop.body()));
        } finally {
            scopes.pop();
            loopDepth--;
            activeBuildingBinding = previousBinding;
            activeBuildingType = previousType;
        }
    }

    private HirStatement analyzeBuildingForEach(ForEachStatement loop, HirBuildingQuery query) {
        String previousBinding = activeBuildingBinding;
        String previousType = activeBuildingType;
        activeBuildingBinding = loop.name();
        activeBuildingType = query.buildingType();
        loopDepth++;
        scopes.push(new HashMap<>());
        try {
            declare(loop.name(), new Symbol(ValueType.BUILDING, false, null, null, null, null, false, false, loop.span()), loop.span());
            List<HirExpression> filters = query.filters().stream()
                .map(filter -> renameBuildingBinding(filter, query.bindingName(), loop.name())).toList();
            return new HirBuildingIteration(loop.name(), query.buildingType(), query.mlogType(), query.buildings(), filters,
                analyzeBlock(loop.body()));
        } finally {
            scopes.pop();
            loopDepth--;
            activeBuildingBinding = previousBinding;
            activeBuildingType = previousType;
        }
    }

    private List<HirHardwareLink> linkedBuildings(String buildingType) {
        return hardwareLinks.values().stream()
            .filter(link -> buildingType.equals(link.mplType()))
            .map(link -> new HirHardwareLink(link.mplName(), link.gameAlias(), link.mplType()))
            .toList();
    }

    private HirStatement analyzeAggregateForEach(ForEachStatement loop) {
        HirExpression iterable = analyzeExpression(loop.iterable());
        if (!(iterable instanceof HirVariable source) || !isAggregate(iterable.type())) {
            error("MPL3601", "for 遍历目标必须是已声明的元组、数组、List、Set 或 Unit 查询", loop.iterable().span());
            return new HirBlock(analyzeBlock(loop.body()));
        }
        Integer size = staticAggregateSize(iterable);
        MplType elementType = aggregateIterationElementType(iterable.type(), loop.iterable().span());
        if (size == null || elementType == ValueType.ERROR) {
            return new HirBlock(analyzeBlock(loop.body()));
        }

        loopDepth++;
        scopes.push(new HashMap<>());
        try {
            declare(loop.name(), new Symbol(elementType, false, null, null, null, null, false, false, loop.span()), loop.span());
            return new HirAggregateIteration(loop.name(), source, elementType, size, analyzeBlock(loop.body()));
        } finally {
            scopes.pop();
            loopDepth--;
        }
    }

    private Optional<UnitQuery> parseUnitQuery(Expression iterable) {
        List<Expression> filters = new ArrayList<>();
        List<CallExpression> modifiers = new ArrayList<>();
        Expression current = iterable;

        while (current instanceof CallExpression call
            && call.callee() instanceof MemberAccessExpression member) {
            if (!"where".equals(member.member()) && !"take".equals(member.member())) {
                break;
            }
            modifiers.add(call);
            current = member.target();
        }

        if (!(current instanceof CallExpression call)
            || !(call.callee() instanceof MemberAccessExpression member)
            || !(member.target() instanceof Identifier namespace)
            || !"Unit".equals(namespace.name())) {
            return Optional.empty();
        }
        if (!member.member().startsWith("getAll") || member.member().length() == "getAll".length()) {
            error("MPL3302", "Unit 查询必须形如 Unit.getAllDagger()", member.span());
            return Optional.empty();
        }

        String typeName = member.member().substring("getAll".length());
        Optional<TargetProfile.UnitType> type = profile.unitType(typeName);
        if (type.isEmpty() || !type.orElseThrow().logicControllable()) {
            error("MPL3302", "当前 target 不支持 Unit.getAll" + typeName + "()", member.span());
            return Optional.empty();
        }
        if (call.arguments().size() > 1) {
            error("MPL3302", "Unit.getAll类型(...) 最多接受一个过滤 lambda", call.span());
            return Optional.empty();
        }
        if (call.arguments().size() == 1) filters.add(0, call.arguments().get(0));

        int managedLimit = 0;
        for (int index = modifiers.size() - 1; index >= 0; index--) {
            CallExpression modifier = modifiers.get(index);
            MemberAccessExpression modifierMember = (MemberAccessExpression) modifier.callee();
            if ("where".equals(modifierMember.member())) {
                if (managedLimit != 0) {
                error("MPL3307", "Set<Unit<T>>.take(n) 必须放在所有 .where(...) 之后", modifier.span());
                    return Optional.empty();
                }
                if (modifier.arguments().size() != 1) {
                    error("MPL3302", "Set<Unit<T>>.where(...) 需要恰好一个过滤 lambda", modifier.span());
                    return Optional.empty();
                }
                filters.add(modifier.arguments().get(0));
                continue;
            }

            if (managedLimit != 0) {
                error("MPL3307", "一个 Set<Unit<T>> 查询只能调用一次 .take(n)", modifier.span());
                return Optional.empty();
            }
            if (modifier.arguments().size() != 1 || !(modifier.arguments().get(0) instanceof IntegerLiteral literal)) {
                error("MPL3307", "Set<Unit<T>>.take(n) 只接受正 Int 字面量", modifier.span());
                return Optional.empty();
            }
            if (literal.value() <= 0 || literal.value() > Integer.MAX_VALUE) {
                error("MPL3307", "Set<Unit<T>>.take(n) 的 n 必须位于 1 到 2147483647", literal.span());
                return Optional.empty();
            }
            managedLimit = (int) literal.value();
        }

        int managedId = managedLimit > 0 ? nextManagedQueryId++ : -1;
        return Optional.of(new UnitQuery(typeName, type.orElseThrow(), List.copyOf(filters), managedLimit, managedId));
    }

    private Optional<BuildingQuery> parseBuildingQuery(Expression iterable) {
        List<Expression> filters = new ArrayList<>();
        List<CallExpression> modifiers = new ArrayList<>();
        Expression current = iterable;

        while (current instanceof CallExpression call
            && call.callee() instanceof MemberAccessExpression member
            && "where".equals(member.member())) {
            modifiers.add(call);
            current = member.target();
        }
        if (!(current instanceof CallExpression queryCall)
            || !(queryCall.callee() instanceof MemberAccessExpression member)
            || !(member.target() instanceof Identifier namespace)
            || !"Building".equals(namespace.name())) {
            return Optional.empty();
        }
        if (!member.member().startsWith("getAll") || member.member().length() == "getAll".length()) {
            error("MPL3201", "Building 查询必须形如 Building.getAllDuo()", member.span());
            return Optional.empty();
        }
        if (queryCall.arguments().size() > 1) {
            error("MPL3201", "Building.getAll类型(...) 最多接受一个过滤 lambda", queryCall.span());
            return Optional.empty();
        }
        String typeName = member.member().substring("getAll".length());
        if (profile.buildingType(typeName).isEmpty()) {
            error("MPL3201", "当前 target 不支持 Building.getAll" + typeName + "()", member.span());
            return Optional.empty();
        }
        if (queryCall.arguments().size() == 1) filters.add(queryCall.arguments().get(0));
        for (int index = modifiers.size() - 1; index >= 0; index--) {
            CallExpression modifier = modifiers.get(index);
            if (modifier.arguments().size() != 1) {
                error("MPL3201", "Building 查询的 .where(...) 需要恰好一个过滤 lambda", modifier.span());
                return Optional.empty();
            }
            filters.add(modifier.arguments().get(0));
        }
        return Optional.of(new BuildingQuery(typeName, List.copyOf(filters)));
    }

    private HirBuildingQuery declaredBuildingQuery(Expression expression) {
        if (!(expression instanceof Identifier identifier)) return null;
        Symbol symbol = lookup(identifier.name());
        return symbol == null ? null : symbol.buildingQuery();
    }

    private HirBuildingQuery resolveSavedBuildingQuery(Expression expression, String bindingName) {
        List<CallExpression> modifiers = new ArrayList<>();
        Expression current = expression;
        while (current instanceof CallExpression call && call.callee() instanceof MemberAccessExpression member
            && "where".equals(member.member())) {
            modifiers.add(call);
            current = member.target();
        }
        HirBuildingQuery base = declaredBuildingQuery(current);
        if (base == null) return null;

        List<HirExpression> filters = new ArrayList<>(base.filters().stream()
            .map(filter -> renameBuildingBinding(filter, base.bindingName(), bindingName)).toList());
        for (int index = modifiers.size() - 1; index >= 0; index--) {
            CallExpression modifier = modifiers.get(index);
            if (modifier.arguments().size() != 1) {
                error("MPL3201", "Set<Building<T>>.where(...) 需要恰好一个过滤 lambda", modifier.span());
                continue;
            }
            filters.add(analyzeBuildingFilter(modifier.arguments().get(0), bindingName, base.buildingType()));
        }
        return new HirBuildingQuery(bindingName, base.buildingType(), base.mlogType(), base.buildings(), filters);
    }

    private HirBuildingQuery analyzeBuildingQuery(BuildingQuery query, String bindingName) {
        String previousBinding = activeBuildingBinding;
        String previousType = activeBuildingType;
        activeBuildingBinding = bindingName;
        activeBuildingType = query.typeName();
        try {
            List<HirExpression> filters = query.filters().stream()
                .map(filter -> analyzeBuildingFilter(filter, bindingName, query.typeName())).toList();
            String mlogType = profile.buildingType(query.typeName()).orElseThrow().mlogName();
            return new HirBuildingQuery(bindingName, query.typeName(), mlogType, linkedBuildings(query.typeName()), filters);
        } finally {
            activeBuildingBinding = previousBinding;
            activeBuildingType = previousType;
        }
    }

    private HirBuildingQuery resolveBuildingQuery(Expression expression, String bindingName) {
        HirBuildingQuery saved = resolveSavedBuildingQuery(expression, bindingName);
        if (saved != null) return saved;
        return parseBuildingQuery(expression).map(query -> analyzeBuildingQuery(query, bindingName)).orElse(null);
    }

    private HirExpression analyzeBuildingQueryGet(HirBuildingQuery query, Expression sourceIndex, SourceSpan span) {
        HirExpression index = analyzeExpression(sourceIndex);
        if (index.type() != ValueType.INT && index.type() != ValueType.ERROR) {
            error("MPL3210", "Set<Building<T>>.get(index) 的 index 必须是 Int", sourceIndex.span());
        }
        return new HirBuildingQueryGet(query, index);
    }

    /** Validates a side-effect-free predicate over one statically expanded linked building. */
    private HirExpression analyzeBuildingFilter(Expression source, String bindingName, String buildingType) {
        String parameter = "_";
        Expression predicate = source;
        if (source instanceof LambdaExpression lambda) {
            parameter = lambda.parameter();
            predicate = lambda.body();
        }
        String previousBinding = activeBuildingBinding;
        String previousType = activeBuildingType;
        activeBuildingBinding = parameter;
        activeBuildingType = buildingType;
        scopes.push(new HashMap<>());
        try {
            // Query parameters shadow an enclosing iteration binding just like Unit filter parameters.
            scopes.peek().put(parameter,
                new Symbol(ValueType.BUILDING, false, null, null, null, null, false, false, source.span()));
            HirExpression result = analyzeExpression(predicate);
            if (result.type() != ValueType.BOOL) {
                error("MPL3201", "Building 查询的 .where(...) 过滤条件必须是 Bool", predicate.span());
            }
            if (!(source instanceof LambdaExpression) && !referencesBuildingBinding(result, parameter)) {
                error("MPL3201", "Building 查询的过滤条件必须是 lambda，或使用引用 _ 的简写", source.span());
            }
            if (!isPureBuildingFilter(result, parameter)) {
                error("MPL3201", "Building 查询的 .where(...) 只能读取当前建筑字段与 val 标量", source.span());
            }
            return renameBuildingBinding(result, parameter, bindingName);
        } finally {
            scopes.pop();
            activeBuildingBinding = previousBinding;
            activeBuildingType = previousType;
        }
    }

    private boolean referencesBuildingBinding(HirExpression expression, String bindingName) {
        if (expression instanceof HirVariable variable) {
            return variable.type() == ValueType.BUILDING && bindingName.equals(variable.name());
        }
        if (expression instanceof HirMemberAccess member) {
            return referencesBuildingBinding(member.target(), bindingName);
        }
        if (expression instanceof HirIntrinsicCall call) {
            return call.arguments().stream().anyMatch(argument -> referencesBuildingBinding(argument, bindingName));
        }
        if (expression instanceof HirUnary unary) return referencesBuildingBinding(unary.operand(), bindingName);
        if (expression instanceof HirBinary binary) {
            return referencesBuildingBinding(binary.left(), bindingName)
                || referencesBuildingBinding(binary.right(), bindingName);
        }
        return false;
    }

    private boolean isPureBuildingFilter(HirExpression expression, String bindingName) {
        if (expression instanceof HirConstant || expression instanceof HirText) return true;
        if (expression instanceof HirVariable variable) {
            if (variable.type() == ValueType.BUILDING) return bindingName.equals(variable.name());
            return isImmutableFilterScalar(variable.name());
        }
        if (expression instanceof HirMemberAccess member) return isPureBuildingFilter(member.target(), bindingName);
        if (expression instanceof HirIntrinsicCall call) {
            return "Math".equals(call.namespace())
                && call.arguments().stream().allMatch(argument -> isPureBuildingFilter(argument, bindingName));
        }
        if (expression instanceof HirUnary unary) return isPureBuildingFilter(unary.operand(), bindingName);
        if (expression instanceof HirBinary binary) {
            return isPureBuildingFilter(binary.left(), bindingName) && isPureBuildingFilter(binary.right(), bindingName);
        }
        return false;
    }

    /** Rebinds the lambda parameter to the iteration variable used by target lowering. */
    private HirExpression renameBuildingBinding(HirExpression expression, String sourceName, String targetName) {
        if (expression instanceof HirVariable variable) {
            return variable.type() == ValueType.BUILDING && sourceName.equals(variable.name())
                ? new HirVariable(targetName, ValueType.BUILDING) : variable;
        }
        if (expression instanceof HirMemberAccess member) {
            return new HirMemberAccess(renameBuildingBinding(member.target(), sourceName, targetName), member.member(), member.type());
        }
        if (expression instanceof HirUnary unary) {
            return new HirUnary(unary.operator(), renameBuildingBinding(unary.operand(), sourceName, targetName), unary.type());
        }
        if (expression instanceof HirBinary binary) {
            return new HirBinary(renameBuildingBinding(binary.left(), sourceName, targetName), binary.operator(),
                renameBuildingBinding(binary.right(), sourceName, targetName), binary.type());
        }
        if (expression instanceof HirIntrinsicCall call) {
            return new HirIntrinsicCall(call.namespace(), call.name(), call.arguments().stream()
                .map(argument -> renameBuildingBinding(argument, sourceName, targetName)).toList(), call.type());
        }
        return expression;
    }

    private HirExpression analyzeUnitFilter(Expression source, String bindingName) {
        String parameter = "_";
        Expression predicate = source;
        if (source instanceof LambdaExpression lambda) {
            parameter = lambda.parameter();
            predicate = lambda.body();
        }

        scopes.push(new HashMap<>());
        try {
            // Lambda parameters deliberately shadow the enclosing loop binding.
            scopes.peek().put(parameter, new Symbol(ValueType.UNIT, false, null, null, null, null, false, false, source.span()));
            HirExpression result = analyzeExpression(predicate);
            if (result.type() != ValueType.BOOL) {
                error("MPL3303", "Set<Unit<T>>.where(...) 的过滤条件必须是 Bool", predicate.span());
            }
            if (!isPureUnitFilter(result, parameter)) {
                error("MPL3303", "Set<Unit<T>>.where(...) 只能读取当前单位属性与 val 标量", predicate.span());
            }
            return renameUnitBinding(result, parameter, bindingName);
        } finally {
            scopes.pop();
        }
    }

    private HirUnitQuery analyzeUnitQuery(UnitQuery query, String bindingName) {
        String previousBinding = activeUnitBinding;
        activeUnitBinding = bindingName;
        try {
            List<HirExpression> filters = query.filters().stream()
                .map(filter -> analyzeUnitFilter(filter, bindingName)).toList();
            return new HirUnitQuery(bindingName, query.typeName(), query.type().mlogName(), filters,
                query.managedLimit(), query.managedId());
        } finally {
            activeUnitBinding = previousBinding;
        }
    }

    private HirUnitQuery resolveUnitQuery(Expression expression, String bindingName) {
        HirUnitQuery saved = resolveSavedUnitQuery(expression, bindingName);
        if (saved != null) return saved;
        return parseUnitQuery(expression).map(query -> analyzeUnitQuery(query, bindingName)).orElse(null);
    }

    private HirExpression analyzeUnitQueryGet(HirUnitQuery query, Expression sourceIndex, SourceSpan span) {
        HirExpression index = analyzeExpression(sourceIndex);
        if (index.type() != ValueType.INT && index.type() != ValueType.ERROR) {
            error("MPL3309", "Set<Unit<T>>.get(index) 的 index 必须是 Int", sourceIndex.span());
        }
        if (currentFunction != null) {
            error("MPL3508", "第一版函数不能扫描 Set<Unit<T>>.get(index)", span);
        }
        if (unitIterationDepth > 0) {
            error("MPL3306", "Set<Unit<T>>.get(index) 不能嵌套在 Unit 遍历中", span);
        }
        return new HirUnitQueryGet(query, index);
    }

    private HirExpression renameUnitBinding(HirExpression expression, String sourceName, String targetName) {
        if (expression instanceof HirVariable variable && variable.type() == ValueType.UNIT
            && variable.name().equals(sourceName)) {
            return new HirVariable(targetName, ValueType.UNIT);
        }
        if (expression instanceof HirMemberAccess member) {
            return new HirMemberAccess(renameUnitBinding(member.target(), sourceName, targetName), member.member(), member.type());
        }
        if (expression instanceof HirUnary unary) {
            return new HirUnary(unary.operator(), renameUnitBinding(unary.operand(), sourceName, targetName), unary.type());
        }
        if (expression instanceof HirBinary binary) {
            return new HirBinary(renameUnitBinding(binary.left(), sourceName, targetName), binary.operator(),
                renameUnitBinding(binary.right(), sourceName, targetName), binary.type());
        }
        if (expression instanceof HirIntrinsicCall call) {
            return new HirIntrinsicCall(call.namespace(), call.name(), call.arguments().stream()
                .map(argument -> renameUnitBinding(argument, sourceName, targetName)).toList(), call.type());
        }
        return expression;
    }

    private boolean isPureUnitFilter(HirExpression expression, String bindingName) {
        if (expression instanceof HirConstant || expression instanceof HirText) return true;
        if (expression instanceof HirVariable variable) {
            if (variable.type() == ValueType.UNIT) return bindingName.equals(variable.name());
            return isImmutableFilterScalar(variable.name());
        }
        if (expression instanceof HirMemberAccess member) {
            return isPureUnitFilter(member.target(), bindingName);
        }
        if (expression instanceof HirIntrinsicCall call) {
            return "Math".equals(call.namespace())
                && call.arguments().stream().allMatch(argument -> isPureUnitFilter(argument, bindingName));
        }
        if (expression instanceof HirUnary unary) return isPureUnitFilter(unary.operand(), bindingName);
        if (expression instanceof HirBinary binary) {
            return isPureUnitFilter(binary.left(), bindingName) && isPureUnitFilter(binary.right(), bindingName);
        }
        return false;
    }

    private boolean isImmutableFilterScalar(String variableName) {
        Symbol symbol = lookup(variableName);
        if (symbol == null || symbol.mutable()) return false;
        return symbol.type() == ValueType.INT || symbol.type() == ValueType.FLOAT
            || symbol.type() == ValueType.BOOL || symbol.type() == ValueType.STRING;
    }

    private HirStatement analyzeExpressionStatement(Expression expression) {
        if (expression instanceof CallExpression call) {
            HirStatement special = analyzeStatementCall(call);
            if (special != null) return special;
        }
        if (expression instanceof MethodCallExpression call) {
            return analyzeLegacyMethodCall(call);
        }
        HirExpression value = analyzeExpression(expression);
        if (value.type() == ValueType.BUILDING) {
            error("MPL3201", "硬件常量只能读取字段或调用控制方法，不能作为普通表达式", expression.span());
        }
        if (isUnitSet(value.type())) {
            error("MPL3301", "Set<Unit<T>> 查询只能保存为 val、读取 size 或作为 for 遍历目标", expression.span());
        }
        if (isBuildingSet(value.type())) {
            error("MPL3201", "Set<Building<T>> 查询只能保存为 val、读取 size/get 或作为 for 遍历目标",
                expression.span());
        }
        return new HirExpressionStatement(value);
    }

    private HirStatement analyzeStatementCall(CallExpression call) {
        if (!(call.callee() instanceof MemberAccessExpression member)
            || !(member.target() instanceof Identifier target)) {
            return null;
        }
        if (unavailablePackageHardware(target.name())) {
            error("MPL1414", "包代码不能访问未通过 with 注入的根项目硬件", call.span());
            return new HirExpressionStatement(new HirConstant("0", ValueType.ERROR));
        }
        HardwareContract.Resource hardware = hardwareResources.get(target.name());
        if (hardware != null) {
            if ("Display".equals(hardware.mplType())) {
                return analyzeDisplayCall(hardware, member.member(), call.arguments(), call.span());
            }
            HardwareContract.LinkDeclaration direct = hardware.physicalLinks().get(0);
            if ("Message".equals(hardware.mplType()) && "print".equals(member.member())) {
                return analyzePrintCall(direct.gameAlias(), call.arguments());
            }
            return analyzeBuildingControl(direct, member.member(), call.arguments(), call.span());
        }

        Symbol targetSymbol = lookup(target.name());
        if (targetSymbol != null && targetSymbol.type() instanceof CollectionType collection) {
            if ("set".equals(member.member())) {
                return analyzeCollectionSet(target.name(), targetSymbol, collection, call.arguments(), call.span());
            }
            if ("add".equals(member.member())) {
                return analyzeMutableListAdd(target.name(), targetSymbol, collection, call.arguments(), call.span());
            }
            if ("clear".equals(member.member())) {
                return analyzeMutableListClear(target.name(), targetSymbol, collection, call.arguments(), call.span());
            }
            if ("removeAt".equals(member.member())) {
                return analyzeMutableListRemoveAt(target.name(), targetSymbol, collection, call.arguments(), call.span());
            }
        }
        if (targetSymbol != null && targetSymbol.type() == ValueType.BUILDING
            && target.name().equals(activeBuildingBinding) && activeBuildingType != null) {
            return analyzeBuildingControl(new HirVariable(target.name(), ValueType.BUILDING), activeBuildingType,
                target.name(), member.member(), call.arguments(), call.span());
        }
        if (targetSymbol != null && targetSymbol.type() == ValueType.UNIT) {
            return analyzeUnitControl(target.name(), false, member.member(), call.arguments(), call.span());
        }
        if (targetSymbol != null && targetSymbol.type() instanceof UnitType unit) {
            if (unit.nullable()) {
                error("MPL3308", "可空 " + unit.displayName() + " 必须先通过 != null 检查", call.span());
                return new HirExpressionStatement(new HirConstant("0", ValueType.ERROR));
            }
            if (currentFunction != null || unitIterationDepth > 0) {
                error("MPL3306", "第一版不能在函数或 Unit 遍历体中重绑已保存的 UnitRef", call.span());
            }
            return analyzeUnitControl(target.name(), true, member.member(), call.arguments(), call.span());
        }
        if (targetSymbol != null && targetSymbol.type() instanceof BuildingType building) {
            if (building.nullable()) {
                error("MPL3211", "可空 " + building.displayName() + " 必须先通过 != null 检查", call.span());
                return new HirExpressionStatement(new HirConstant("0", ValueType.ERROR));
            }
            return analyzeBuildingControl(new HirVariable(target.name(), building), building.buildingType(), target.name(),
                member.member(), call.arguments(), call.span());
        }
        return null;
    }

    private HirStatement analyzeDisplayCall(HardwareContract.Resource display, String method,
                                            List<Expression> sourceArguments, SourceSpan span) {
        if ("flush".equals(method)) {
            error("MPL3203", "MPL 不提供 Display.flush()；绘制刷新由编译器 runtime 自动管理", span);
            return new HirExpressionStatement(new HirConstant("0", ValueType.ERROR));
        }
        HirDraw.Command command = switch (method) {
            case "clear" -> HirDraw.Command.CLEAR;
            case "fill", "stroke" -> HirDraw.Command.COLOR;
            case "fillRect" -> HirDraw.Command.RECT;
            case "strokeRect" -> HirDraw.Command.LINE_RECT;
            case "line" -> HirDraw.Command.LINE;
            default -> null;
        };
        if (command == null) {
            error("MPL3203", "Display 不支持绘制方法：" + method, span);
            return new HirExpressionStatement(new HirConstant("0", ValueType.ERROR));
        }
        if (command == HirDraw.Command.CLEAR || command == HirDraw.Command.COLOR) {
            if (sourceArguments.size() != 1) error("MPL3203", "Display." + method + "(...) 需要一个 Color 参数", span);
            List<HirExpression> color = sourceArguments.size() == 1
                ? analyzeColor(sourceArguments.get(0)) : List.<HirExpression>of(new HirConstant("0", ValueType.ERROR),
                    new HirConstant("0", ValueType.ERROR), new HirConstant("0", ValueType.ERROR),
                    new HirConstant("0", ValueType.ERROR));
            return new HirDraw(display.mplName(), command, color.subList(0, command.argumentCount()));
        }
        if (sourceArguments.size() != command.argumentCount()) {
            error("MPL3203", "Display." + method + "(...) 的参数数量不匹配", span);
        }
        List<HirExpression> arguments = new ArrayList<>();
        for (Expression source : sourceArguments) {
            HirExpression argument = analyzeExpression(source);
            requireNumeric(argument.type(), source.span(), "Display." + method + " 参数");
            arguments.add(argument);
        }
        while (arguments.size() < command.argumentCount()) arguments.add(new HirConstant("0", ValueType.ERROR));
        return new HirDraw(display.mplName(), command,
            arguments.subList(0, command.argumentCount()));
    }

    /** Type-checks the public MIL draw macro without exposing raw draw commands in MPL. */
    private HirStatement analyzeMilDraw(MilDrawStatement draw) {
        HardwareContract.LinkDeclaration display = hardwareLinks.get(draw.hardwareName());
        if (display == null || !"Display".equals(display.mplType())) {
            error("MIL3201", "@io.draw 的目标必须是硬件声明中的 Display", draw.span());
            return new HirExpressionStatement(new HirConstant("0", ValueType.ERROR));
        }
        HirDraw.Command command = switch (draw.command()) {
            case "clear" -> HirDraw.Command.CLEAR;
            case "color" -> HirDraw.Command.COLOR;
            case "rect" -> HirDraw.Command.RECT;
            case "lineRect" -> HirDraw.Command.LINE_RECT;
            case "line" -> HirDraw.Command.LINE;
            default -> null;
        };
        if (command == null) {
            error("MIL3201", "target 不支持绘制命令：" + draw.command(), draw.span());
            return new HirExpressionStatement(new HirConstant("0", ValueType.ERROR));
        }
        if (draw.arguments().size() != command.argumentCount()) {
            error("MIL3201", "绘制命令 " + draw.command() + " 需要 " + command.argumentCount()
                + " 个参数，实际为 " + draw.arguments().size(), draw.span());
        }
        List<HirExpression> arguments = new ArrayList<>();
        for (Expression source : draw.arguments()) {
            HirExpression argument = analyzeExpression(source);
            requireNumeric(argument.type(), source.span(), "@io.draw 参数");
            arguments.add(argument);
        }
        while (arguments.size() < command.argumentCount()) arguments.add(new HirConstant("0", ValueType.ERROR));
        return new HirDraw(display.gameAlias(), command, arguments.subList(0, command.argumentCount()));
    }

    private List<HirExpression> analyzeColor(Expression source) {
        if (source instanceof MemberAccessExpression member && member.target() instanceof Identifier namespace
            && "Color".equals(namespace.name())) {
            return switch (member.member()) {
                case "black" -> color(0, 0, 0, 255);
                case "white" -> color(255, 255, 255, 255);
                case "red" -> color(255, 0, 0, 255);
                case "green" -> color(0, 255, 0, 255);
                default -> invalidColor(source, "Color 不支持常量：" + member.member());
            };
        }
        if (source instanceof CallExpression call && call.callee() instanceof MemberAccessExpression member
            && member.target() instanceof Identifier namespace && "Color".equals(namespace.name()) && "rgb".equals(member.member())) {
            if (call.arguments().size() != 3 && call.arguments().size() != 4) {
                return invalidColor(source, "Color.rgb(...) 需要 3 或 4 个 Int 参数");
            }
            List<HirExpression> values = new ArrayList<>();
            for (Expression argument : call.arguments()) {
                if (!(argument instanceof IntegerLiteral literal) || literal.value() < 0 || literal.value() > 255) {
                    error("MPL3203", "当前 Color.rgb(...) 仅接受 0 到 255 的 Int 字面量", argument.span());
                }
                HirExpression value = analyzeExpression(argument);
                if (value.type() != ValueType.INT) error("MPL3203", "Color.rgb(...) 参数必须是 Int", argument.span());
                values.add(value);
            }
            if (values.size() == 3) values.add(new HirConstant("255", ValueType.INT));
            return List.copyOf(values);
        }
        return invalidColor(source, "Color 参数必须是 Color 常量或 Color.rgb(...)");
    }

    private List<HirExpression> color(int red, int green, int blue, int alpha) {
        return List.of(new HirConstant(Integer.toString(red), ValueType.INT), new HirConstant(Integer.toString(green), ValueType.INT),
            new HirConstant(Integer.toString(blue), ValueType.INT), new HirConstant(Integer.toString(alpha), ValueType.INT));
    }

    private List<HirExpression> invalidColor(Expression source, String message) {
        error("MPL3203", message, source.span());
        return color(0, 0, 0, 0);
    }

    private HirStatement analyzeBuildingControl(HardwareContract.LinkDeclaration link, String method,
                                                List<Expression> sourceArguments, SourceSpan span) {
        return analyzeBuildingControl(new HirHardwareLink(link.mplName(), link.gameAlias(), link.mplType()), link.mplType(),
            link.mplName(), method, sourceArguments, span);
    }

    private HirStatement analyzeBuildingControl(HirExpression target, String buildingType, String targetName, String method,
                                                List<Expression> sourceArguments, SourceSpan span) {
        TargetProfile.BuildingType building = profile.buildingType(buildingType).orElse(null);
        TargetProfile.BuildingAction action = building == null ? null : building.actions().get(method);
        if (action == null) {
            error("MPL3201", "建筑 " + targetName + "（" + buildingType + "）不支持控制方法：" + method, span);
            return new HirExpressionStatement(new HirConstant("0", ValueType.ERROR));
        }
        if (sourceArguments.size() != action.parameterTypes().size()) {
            error("MPL3201", "硬件控制 " + method + " 的参数数量不匹配", span);
        }
        List<HirExpression> arguments = new ArrayList<>();
        for (int index = 0; index < sourceArguments.size(); index++) {
            Expression sourceArgument = sourceArguments.get(index);
            HirExpression argument = analyzeExpression(sourceArgument);
            if (index < action.parameterTypes().size()
                && !canAssign(action.parameterTypes().get(index), argument.type())) {
                error("MPL3201", "硬件控制 " + method + " 的参数类型不匹配", sourceArgument.span());
            }
            arguments.add(argument);
        }
        String control = action.target().substring("control ".length());
        return new HirBuildingControl(target, control, arguments);
    }

    private HirStatement analyzeLegacyMethodCall(MethodCallExpression call) {
        if (unavailablePackageHardware(call.target())) {
            error("MPL1414", "包代码不能访问未通过 with 注入的根项目硬件", call.span());
            return new HirExpressionStatement(new HirConstant("0", ValueType.ERROR));
        }
        String linkName = messages.get(call.target());
        if (linkName != null && "print".equals(call.method())) {
            return analyzePrintCall(linkName, call.arguments());
        }
        error("MPL3201", "当前阶段不支持该成员调用", call.span());
        return new HirExpressionStatement(new HirConstant("0", ValueType.ERROR));
    }

    private HirStatement analyzeCollectionSet(String target, Symbol symbol, CollectionType type,
                                         List<Expression> arguments, SourceSpan span) {
        if (type.kind() != CollectionType.Kind.ARRAY && type.kind() != CollectionType.Kind.MUTABLE_LIST) {
            error("MPL3601", "只有 Array 或 MutableList 支持 set(index, value)", span);
            return new HirExpressionStatement(new HirConstant("0", ValueType.ERROR));
        }
        if (!symbol.mutable()) error("MPL3104", "不能修改 val " + type.displayName() + "：" + target, span);
        if (arguments.size() != 2) {
            error("MPL3601", "Array.set(index, value) 需要两个参数", span);
            return new HirExpressionStatement(new HirConstant("0", ValueType.ERROR));
        }
        HirExpression value = analyzeExpression(arguments.get(1));
        if (!canAssign(type.elementType(), value.type())) {
            error("MPL3103", "不能将 " + display(value.type()) + " 写入 " + type.displayName(), arguments.get(1).span());
        }
        Expression sourceIndex = arguments.get(0);
        if (sourceIndex instanceof IntegerLiteral) {
            return staticAggregateIndex(sourceIndex, symbol.staticAggregateSize())
                .<HirStatement>map(valueAt -> new HirCollectionSet(target, valueAt, value))
                .orElseGet(() -> new HirExpressionStatement(new HirConstant("0", ValueType.ERROR)));
        }
        HirExpression index = analyzeExpression(sourceIndex);
        if (index.type() != ValueType.INT) {
            error("MPL3601", type.displayName() + ".set(...) 下标必须是 Int", sourceIndex.span());
            return new HirExpressionStatement(new HirConstant("0", ValueType.ERROR));
        }
        if (!supportsDynamicArrayElement(type.elementType())) {
            error("MPL3601", "动态容器下标当前只支持 Int、Float 或 Bool 元素", span);
            return new HirExpressionStatement(new HirConstant("0", ValueType.ERROR));
        }
        if (!hasArrayBoundsProof(target, sourceIndex)) {
            error("MPL3601", boundsProofMessage(type), sourceIndex.span());
            return new HirExpressionStatement(new HirConstant("0", ValueType.ERROR));
        }
        return new HirDynamicCollectionSet(target, index, value);
    }

    private HirStatement analyzeMutableListAdd(String target, Symbol symbol, CollectionType type,
                                               List<Expression> arguments, SourceSpan span) {
        if (type.kind() != CollectionType.Kind.MUTABLE_LIST) {
            error("MPL3601", "只有 MutableList 支持 add(value)", span);
            return new HirExpressionStatement(new HirConstant("0", ValueType.ERROR));
        }
        if (!symbol.mutable()) error("MPL3104", "不能修改 val MutableList：" + target, span);
        if (arguments.size() != 1) {
            error("MPL3601", "MutableList.add(value) 需要一个参数", span);
            return new HirExpressionStatement(new HirConstant("0", ValueType.ERROR));
        }
        HirExpression value = analyzeExpression(arguments.get(0));
        if (!canAssign(type.elementType(), value.type())) {
            error("MPL3103", "不能将 " + display(value.type()) + " 写入 " + type.displayName(), arguments.get(0).span());
        }
        if (aggregateControlDepth > 0 || symbol.staticAggregateSize() == null || symbol.aggregateCapacity() == null) {
            error("MPL3601", "MutableList.add 需要编译器可证明的线性容量；不能位于条件或循环中", span);
            return new HirExpressionStatement(new HirConstant("0", ValueType.ERROR));
        }
        if (symbol.staticAggregateSize() >= symbol.aggregateCapacity()) {
            error("MPL3601", "MutableList.add 超出已声明容量 " + symbol.aggregateCapacity(), span);
            return new HirExpressionStatement(new HirConstant("0", ValueType.ERROR));
        }
        updateSymbol(target, valueAt -> valueAt.withAggregateSize(valueAt.staticAggregateSize() + 1));
        return new HirMutableListAdd(target, value);
    }

    private HirStatement analyzeMutableListClear(String target, Symbol symbol, CollectionType type,
                                                 List<Expression> arguments, SourceSpan span) {
        if (type.kind() != CollectionType.Kind.MUTABLE_LIST) {
            error("MPL3601", "只有 MutableList 支持 clear()", span);
            return new HirExpressionStatement(new HirConstant("0", ValueType.ERROR));
        }
        if (!symbol.mutable()) error("MPL3104", "不能修改 val MutableList：" + target, span);
        if (!arguments.isEmpty()) error("MPL3601", "MutableList.clear() 不接受参数", span);
        if (aggregateControlDepth > 0) {
            error("MPL3601", "MutableList.clear 不能位于条件或循环中", span);
            return new HirExpressionStatement(new HirConstant("0", ValueType.ERROR));
        }
        updateSymbol(target, valueAt -> valueAt.withAggregateSize(0));
        return new HirMutableListClear(target);
    }

    private HirStatement analyzeMutableListRemoveAt(String target, Symbol symbol, CollectionType type,
                                                    List<Expression> arguments, SourceSpan span) {
        if (type.kind() != CollectionType.Kind.MUTABLE_LIST) {
            error("MPL3601", "只有 MutableList 支持 removeAt(index)", span);
            return new HirExpressionStatement(new HirConstant("0", ValueType.ERROR));
        }
        if (!symbol.mutable()) error("MPL3104", "不能修改 val MutableList：" + target, span);
        if (arguments.size() != 1 || !(arguments.get(0) instanceof IntegerLiteral literal)
            || literal.value() < 0 || literal.value() > Integer.MAX_VALUE) {
            error("MPL3601", "MutableList.removeAt(index) 需要非负 Int 字面量下标", span);
            return new HirExpressionStatement(new HirConstant("0", ValueType.ERROR));
        }
        if (aggregateControlDepth > 0) {
            error("MPL3601", "MutableList.removeAt 不能位于条件或循环中", span);
            return new HirExpressionStatement(new HirConstant("0", ValueType.ERROR));
        }
        int index = (int) literal.value();
        if (symbol.staticAggregateSize() == null || index >= symbol.staticAggregateSize()) {
            error("MPL3601", "MutableList.removeAt 下标越界：" + index, span);
            return new HirExpressionStatement(new HirConstant("0", ValueType.ERROR));
        }
        updateSymbol(target, valueAt -> valueAt.withAggregateSize(valueAt.staticAggregateSize() - 1));
        return new HirMutableListRemoveAt(target, index);
    }

    private HirStatement analyzePrintCall(String linkName, List<Expression> sourceArguments) {
        List<HirExpression> arguments = new ArrayList<>();
        for (Expression argument : sourceArguments) {
            HirExpression value = analyzePrintValue(argument);
            if (value.type() != ValueType.INT && value.type() != ValueType.FLOAT && value.type() != ValueType.BOOL
                && value.type() != ValueType.STRING
                && !(value instanceof HirText)) {
                error("MPL3202", "print 参数必须是数值、Bool 或字符串字面量", argument.span());
            }
            arguments.add(value);
        }
        int staticLength = arguments.stream().mapToInt(this::stringMaxLength).sum();
        if (staticLength > profile.maxMessageUtf16CodeUnits()) {
            error("MPL3202", "print 的静态文本上界为 " + staticLength + " 个 UTF-16 代码单元，超过 target "
                + profile.id() + " 的 " + profile.maxMessageUtf16CodeUnits() + " 个上限", sourceArguments.get(0).span());
        }
        return new HirPrintStatement(linkName, arguments);
    }

    /**
     * A print call is the one String-concatenation context that does not need
     * a String value at runtime. The target emitter appends each leaf in order.
     */
    private HirExpression analyzePrintValue(Expression source) {
        if (source instanceof BinaryExpression binary && "+".equals(binary.operator())) {
            HirExpression left = analyzePrintValue(binary.left());
            HirExpression right = analyzePrintValue(binary.right());
            if (left.type() == ValueType.STRING && right.type() == ValueType.STRING) {
                if (left instanceof HirText leftText && right instanceof HirText rightText) {
                    return new HirText(leftText.value() + rightText.value());
                }
                return new HirBinary(left, "+", right, ValueType.STRING);
            }
            error("MPL3103", "print 中的 String 拼接两侧都必须是 String", binary.span());
            return new HirConstant("0", ValueType.ERROR);
        }
        return analyzeExpression(source);
    }

    private HirStatement analyzeUnitControl(String sourceBinding, boolean storedReference, String command,
                                            List<Expression> sourceArguments, SourceSpan span) {
        Optional<TargetProfile.UnitAction> action = profile.unitAction(command);
        if (action.isEmpty()) {
            error("MPL3305", "当前 target 的 Unit 不支持控制动作：" + command, span);
            return new HirExpressionStatement(new HirConstant("0", ValueType.ERROR));
        }
        if (sourceArguments.size() != action.orElseThrow().parameterTypes().size()) {
            error("MPL3305", "Unit." + command + "(...) 参数数量不匹配", span);
        }
        List<HirExpression> arguments = new ArrayList<>();
        for (int index = 0; index < sourceArguments.size(); index++) {
            Expression argument = sourceArguments.get(index);
            HirExpression value = analyzeExpression(argument);
            if (index < action.orElseThrow().parameterTypes().size()
                && !canAssign(action.orElseThrow().parameterTypes().get(index), value.type())) {
                error("MPL3305", "Unit." + command + " 参数类型不匹配", argument.span());
            }
            arguments.add(value);
        }
        String binding = storedReference || activeUnitBinding == null ? sourceBinding : activeUnitBinding;
        return new HirUnitControl(binding, storedReference, command, arguments);
    }

    private HirStatement analyzeDeclaration(VariableDeclaration declaration) {
        MplType declaredType = declaration.declaredType().map(value -> parseType(value, declaration.span())).orElse(null);
        HirUnitQuery unitQuery = resolveSavedUnitQuery(declaration.initializer(), "_");
        if (unitQuery == null) {
            unitQuery = parseUnitQuery(declaration.initializer())
                .map(query -> analyzeUnitQuery(query, "_"))
                .orElse(null);
        }
        HirBuildingQuery buildingQuery = unitQuery == null
            ? resolveSavedBuildingQuery(declaration.initializer(), "_") : null;
        if (unitQuery == null && buildingQuery == null) {
            buildingQuery = parseBuildingQuery(declaration.initializer())
                .map(query -> analyzeBuildingQuery(query, "_"))
                .orElse(null);
        }
        boolean staticAllocationContext = analyzingTopLevel && currentFunction == null && scopes.size() == 1;
        FunctionSignature ownedFactory = ownedFactory(declaration.initializer());
        ObjectAllocationContext previousAllocationContext = objectAllocationContext;
        ObjectAllocationContext declarationAllocationContext = staticAllocationContext
            ? ObjectAllocationContext.STATIC
            : !declaration.mutable() && declaration.initializer() instanceof NewExpression
                ? ObjectAllocationContext.REUSABLE_LOCAL
                : ObjectAllocationContext.DISALLOWED;
        HirExpression initializer;
        Expression previousAllowedOwnedFactoryCall = allowedOwnedFactoryCall;
        try {
            objectAllocationContext = declarationAllocationContext;
            allowedOwnedFactoryCall = ownedFactory == null ? previousAllowedOwnedFactoryCall : declaration.initializer();
            initializer = unitQuery != null ? unitQuery
                : buildingQuery != null ? buildingQuery
                : analyzeInitializer(declaration.initializer(), declaredType);
        } finally {
            objectAllocationContext = previousAllocationContext;
            allowedOwnedFactoryCall = previousAllowedOwnedFactoryCall;
        }
        if (initializer.type() == ValueType.BUILDING) {
            error("MPL3201", "硬件常量不能赋给普通变量；请直接读取字段或调用控制方法", declaration.initializer().span());
        }
        MplType type = declaredType == null ? initializer.type() : declaredType;
        if (declaredType == null && initializer.type() == ValueType.NULL) {
            error("MPL3103", "不能仅从 null 推导变量类型；请显式声明可空对象类型", declaration.span());
            type = ValueType.ERROR;
        }
        if (type == ValueType.VOID) error("MPL3103", "变量不能使用 Void 类型", declaration.span());
        if (!canAssign(type, initializer.type())) {
            error("MPL3103", "不能将 " + display(initializer.type()) + " 赋给 " + display(type), declaration.initializer().span());
        }
        boolean fixedArrayCopy = type instanceof CollectionType collection
            && collection.kind() == CollectionType.Kind.ARRAY
            && staticAggregateSize(initializer) != null;
        if (isAggregate(type) && unitQuery == null && buildingQuery == null && !(initializer instanceof HirArrayLiteral)
            && !(initializer instanceof HirTupleLiteral) && !(initializer instanceof HirCollectionLiteral)
            && !(initializer instanceof HirMutableListLiteral)
            && !(type instanceof TupleType && supportedTupleFunctionAbi(type)) && !fixedArrayCopy) {
            error("MPL3601", "当前阶段不支持复制聚合值；请在声明处使用字面量或集合工厂", declaration.initializer().span());
        }
        if (unitQuery != null && declaration.mutable()) {
            error("MPL3301", "Set<Unit<T>> 是不可变的惰性查询描述符，只能使用 val 声明", declaration.span());
        }
        if (unitQuery != null && currentFunction != null) {
            error("MPL3508", "第一版函数不能声明 Set<Unit<T>> 查询", declaration.span());
        }
        if (isUnitSet(type) && unitQuery == null) {
            error("MPL3301", "Set<Unit<T>> 变量必须由 Unit.getAll类型(...) 查询初始化", declaration.initializer().span());
        }
        if (buildingQuery != null && declaration.mutable()) {
            error("MPL3201", "Set<Building<T>> 查询是不可变的惰性查询描述符，只能使用 val 声明", declaration.span());
        }
        if (isBuildingSet(type) && buildingQuery == null) {
            error("MPL3201", "Set<Building<T>> 变量必须由 Building.getAll类型(...) 查询初始化",
                declaration.initializer().span());
        }
        if (type instanceof BuildingType && declaration.mutable()) {
            error("MPL3212", "保存的 Building<T> 引用具有稳定链接身份，只能使用 val 声明", declaration.span());
        }
        if (type instanceof BuildingType && !(initializer instanceof HirBuildingQueryGet)) {
            error("MPL3212", "Building<T> 引用必须由 Set<Building<T>>.get(index) 初始化",
                declaration.initializer().span());
        }
        boolean global = currentFunction == null && scopes.size() == 1;
        boolean ownsPooledObject = ownedFactory != null;
        if (ownsPooledObject && declaration.mutable()) {
            error("MPL3708", "对象工厂结果必须由 val 直接接收，以维持唯一所有权", declaration.span());
        }
        if (ownsPooledObject && (!(type instanceof ObjectType object) || object.nullable())) {
            error("MPL3708", "对象池所有者必须是不可空对象 val", declaration.span());
        }
        boolean reusableLocalObject = declarationAllocationContext == ObjectAllocationContext.REUSABLE_LOCAL
            && initializer instanceof HirNewObject;
        int initialStringMax = type == ValueType.STRING ? stringMaxLength(initializer) : 0;
        int stringCapacity = type == ValueType.STRING
            ? declaration.mutable() ? profile.maxMessageUtf16CodeUnits() : initialStringMax
            : 0;
        if (type == ValueType.STRING && stringCapacity > profile.maxMessageUtf16CodeUnits()) {
            error("MPL3103", "String 的静态上界为 " + stringCapacity + " 个 UTF-16 代码单元，超过 target "
                + profile.id() + " 的 " + profile.maxMessageUtf16CodeUnits() + " 个上限", declaration.span());
        }
        boolean declared = declare(declaration.name(),
            new Symbol(type, declaration.mutable(), type == ValueType.STRING ? initialStringMax : null,
                staticAggregateSize(initializer), aggregateCapacity(initializer), unitQuery,
                buildingQuery, reusableLocalObject, ownsPooledObject, global, declaration.span()),
            declaration.span());
        if (declared && ownsPooledObject && type instanceof ObjectType object) {
            pooledOwnerScopes.peek().add(new PooledOwner(declaration.name(), object.className(), loopDepth, global));
        }
        if (declared && global) {
            // The initializer has already been analyzed, so calls inside it
            // deliberately do not observe this variable as initialized.
            initializedGlobals.add(declaration.name());
        }
        return new HirVariableDeclaration(declaration.name(), type, declaration.mutable(), initializer,
            ownsPooledObject, stringCapacity);
    }

    private FunctionSignature ownedFactory(Expression expression) {
        if (!(expression instanceof CallExpression call) || !(call.callee() instanceof Identifier identifier)) return null;
        List<FunctionSignature> candidates = functionOverloads.getOrDefault(identifier.name(), List.of()).stream()
            .filter(signature -> signature.parameterTypes().size() == call.arguments().size()).toList();
        if (candidates.isEmpty() || candidates.stream().anyMatch(signature -> !signature.returnsOwnedObject())) return null;
        return candidates.get(0);
    }

    /** Resolves empty aggregate literals from an explicit declaration type. */
    private HirExpression analyzeInitializer(Expression initializer, MplType expectedType) {
        if (expectedType instanceof CollectionType collection && initializer instanceof ArrayLiteral array
            && array.elements().isEmpty() && collection.kind() == CollectionType.Kind.ARRAY) {
            return new HirArrayLiteral(List.of(), collection);
        }
        if (expectedType instanceof CollectionType collection && initializer instanceof CallExpression call
            && call.arguments().isEmpty() && collectionFactory(call.callee()).filter(collection.kind()::equals).isPresent()) {
            return new HirCollectionLiteral(List.of(), collection);
        }
        if (expectedType instanceof CollectionType collection && collection.kind() == CollectionType.Kind.MUTABLE_LIST
            && initializer instanceof CallExpression call && mutableListCapacityFactory(call.callee())) {
            if (call.arguments().size() != 1 || !(call.arguments().get(0) instanceof IntegerLiteral capacity)
                || capacity.value() < 1 || capacity.value() > Integer.MAX_VALUE) {
                error("MPL3601", "MutableList.withCapacity(capacity) 需要大于 0 的 Int 字面量容量", call.span());
                return new HirConstant("0", ValueType.ERROR);
            }
            return new HirMutableListLiteral(List.of(), (int) capacity.value(), collection);
        }
        return analyzeExpression(initializer);
    }

    private HirExpression analyzeExpression(Expression expression) {
        if (expression instanceof IntegerLiteral integer) {
            return new HirConstant(Long.toString(integer.value()), ValueType.INT);
        }
        if (expression instanceof FloatLiteral decimal) {
            if (!Double.isFinite(decimal.value())) {
                error("MPL3103", "Float 字面量必须是有限值", decimal.span());
                return new HirConstant("0.0", ValueType.ERROR);
            }
            return new HirConstant(Double.toString(decimal.value()), ValueType.FLOAT);
        }
        if (expression instanceof StringLiteral text) {
            if (hasUnpairedSurrogate(text.value())) {
                error("MPL3103", "String 不能包含未配对的 UTF-16 代理代码单元", text.span());
            }
            if (text.value().length() > profile.maxMessageUtf16CodeUnits()) {
                error("MPL3103", "String 字面量包含 " + text.value().length() + " 个 UTF-16 代码单元，超过 target "
                    + profile.id() + " 的 " + profile.maxMessageUtf16CodeUnits() + " 个上限", text.span());
            }
            return new HirText(text.value());
        }
        if (expression instanceof ArrayLiteral array) return analyzeArrayLiteral(array);
        if (expression instanceof TupleLiteral tuple) return analyzeTupleLiteral(tuple);
        if (expression instanceof BooleanLiteral bool) {
            return new HirConstant(bool.value() ? "1" : "0", ValueType.BOOL);
        }
        if (expression instanceof NullLiteral) return new HirConstant("null", ValueType.NULL);
        if (expression instanceof NewExpression allocation) return analyzeNew(allocation);
        if (expression instanceof Identifier identifier) {
            HardwareContract.LinkDeclaration hardware = hardwareLinks.get(identifier.name());
            if (hardware != null) return new HirHardwareLink(hardware.mplName(), hardware.gameAlias(), hardware.mplType());
            HardwareContract.Resource resource = hardwareResources.get(identifier.name());
            if (resource != null) return new HirVariable(resource.mplName(), ValueType.BUILDING);
            Symbol symbol = lookup(identifier.name());
            if (symbol == null) {
                error("MPL3102", "未声明的变量：" + identifier.name(), identifier.span());
                return new HirVariable(identifier.name(), ValueType.ERROR);
            }
            if (currentFunction != null && symbol.type() instanceof UnitType) {
                error("MPL3508", "第一版函数不能访问已保存的 UnitRef", identifier.span());
            }
            if ((symbol.reusableLocalObject() || symbol.ownsPooledObject()) && !borrowedObjectUse) {
                error("MPL3708", "受管对象 " + identifier.name()
                    + " 不能建立别名或转移已有引用；只能访问字段、比较身份或调用不泄露接收者的方法",
                    identifier.span());
            }
            recordGlobalAccess(identifier.name(), symbol, identifier.span());
            return new HirVariable(identifier.name(), symbol.type());
        }
        if (expression instanceof IndexExpression access) {
            return analyzeIndexAccess(access);
        }
        if (expression instanceof MemberAccessExpression member) {
            return analyzeMemberAccess(member);
        }
        if (expression instanceof CallExpression call) {
            return analyzeCallExpression(call);
        }
        if (expression instanceof LambdaExpression lambda) {
            error("MPL3303", "lambda 只能作为 Unit.getAll类型(...) 或 .where(...) 的参数", lambda.span());
            return new HirConstant("0", ValueType.ERROR);
        }
        if (expression instanceof MethodCallExpression call) {
            error("MPL3201", "硬件调用只能作为独立语句", call.span());
            return new HirConstant("0", ValueType.ERROR);
        }
        if (expression instanceof UnaryExpression unary) {
            if ("-".equals(unary.operator()) && unary.operand() instanceof IntegerLiteral literal) {
                // Preserve a signed literal until the optimizer applies Int saturation.
                // Normalizing its positive half would make Int.min unrepresentable.
                return new HirConstant(Long.toString(-literal.value()), ValueType.INT);
            }
            HirExpression operand = analyzeExpression(unary.operand());
            ValueType type = switch (unary.operator()) {
                case "+", "-" -> requireNumeric(operand.type(), unary.span(), "一元运算符 " + unary.operator());
                case "!" -> requireBool(operand.type(), unary.span(), "一元运算符 !");
                default -> ValueType.ERROR;
            };
            return new HirUnary(unary.operator(), operand, type);
        }
        if (expression instanceof BinaryExpression binary) {
            boolean identityOrNullComparison = "===".equals(binary.operator()) || "!==".equals(binary.operator())
                || "==".equals(binary.operator()) || "!=".equals(binary.operator());
            HirExpression left = identityOrNullComparison
                ? analyzeBorrowedObjectExpression(binary.left()) : analyzeExpression(binary.left());
            HirExpression right = identityOrNullComparison
                ? analyzeBorrowedObjectExpression(binary.right()) : analyzeExpression(binary.right());
            if ("+".equals(binary.operator())
                && (left.type() == ValueType.STRING || right.type() == ValueType.STRING)) {
                if (left.type() != ValueType.STRING || right.type() != ValueType.STRING) {
                    error("MPL3103", "String 拼接两侧都必须是 String", binary.span());
                    return new HirConstant("0", ValueType.ERROR);
                }
                if (left instanceof HirText leftText && right instanceof HirText rightText) {
                    return new HirText(leftText.value() + rightText.value());
                }
                int maxCodeUnits;
                try {
                    maxCodeUnits = Math.addExact(stringMaxLength(left), stringMaxLength(right));
                } catch (ArithmeticException exception) {
                    maxCodeUnits = Integer.MAX_VALUE;
                }
                if (maxCodeUnits > profile.maxMessageUtf16CodeUnits()) {
                    error("MPL3103", "String 拼接的静态上界为 " + maxCodeUnits + " 个 UTF-16 代码单元，超过 target "
                        + profile.id() + " 的 " + profile.maxMessageUtf16CodeUnits() + " 个上限", binary.span());
                }
                return new HirStringConcat(nextStringAllocationId++, left, right, maxCodeUnits);
            }
            if (("==".equals(binary.operator()) || "!=".equals(binary.operator()))
                && left.type() == ValueType.STRING && right.type() == ValueType.STRING) {
                return new HirStringComparison(left, right, "==".equals(binary.operator()));
            }
            ValueType type = binaryType(binary.operator(), left.type(), right.type(), binary.span());
            return new HirBinary(left, binary.operator(), right, type);
        }
        if (expression instanceof MemberAssignmentExpression assignment) {
            return analyzeObjectFieldAssignment(assignment);
        }
        AssignmentExpression assignment = (AssignmentExpression) expression;
        Symbol target = lookup(assignment.target().name());
        HirExpression value = analyzeExpression(assignment.value());
        if (target == null) {
            error("MPL3102", "未声明的变量：" + assignment.target().name(), assignment.target().span());
            return new HirAssignment(assignment.target().name(), assignment.operator(), value, ValueType.ERROR);
        }
        recordGlobalAccess(assignment.target().name(), target, assignment.target().span());
        if (!target.mutable()) {
            error("MPL3104", "不能给 val 重新赋值：" + assignment.target().name(), assignment.target().span());
        }
        if (arrayBoundsProofs.stream().anyMatch(proof -> proof.index().equals(assignment.target().name()))) {
            error("MPL3601", "不能在已证明边界的 for 循环体内修改下标变量：" + assignment.target().name(), assignment.span());
        }
        if ("=".equals(assignment.operator())) {
            if (isAggregate(target.type())) {
                error("MPL3601", "当前阶段不能整体重新赋值聚合值；可变 Array 请使用 set(index, value)", assignment.span());
            }
            if (!canAssign(target.type(), value.type())) {
                error("MPL3103", "不能将 " + display(value.type()) + " 赋给 " + display(target.type()), assignment.value().span());
            }
            if (target.type() == ValueType.STRING) {
                int assignedMax = stringMaxLength(value);
                if (assignedMax > profile.maxMessageUtf16CodeUnits()) {
                    error("MPL3103", "String 赋值的静态上界超过 target 容量 "
                        + profile.maxMessageUtf16CodeUnits(), assignment.span());
                }
                if (loopDepth > 0 && referencesVariable(value, assignment.target().name())) {
                    error("MPL3103", "循环中的 String 自引用赋值无法证明长度上界", assignment.span());
                }
                updateStringMax(assignment.target().name(), Math.max(target.staticStringCodeUnits(), assignedMax));
            }
        } else {
            if (target.type() == ValueType.STRING) {
                error("MPL3103", "String 暂不支持复合赋值；请使用 message = message + suffix", assignment.span());
                return new HirAssignment(assignment.target().name(), assignment.operator(), value, ValueType.ERROR);
            }
            ValueType result = binaryType(assignment.operator().substring(0, 1), target.type(), value.type(), assignment.span());
            if (!canAssign(target.type(), result)) {
                error("MPL3103", "复合赋值结果不能赋给 " + display(target.type()), assignment.span());
            }
        }
        return new HirAssignment(assignment.target().name(), assignment.operator(), value, target.type());
    }

    private HirExpression analyzeMemberAccess(MemberAccessExpression member) {
        if (member.target() instanceof Identifier identifier && "Clock".equals(identifier.name())) {
            return clockIntrinsic(member.member(), List.of(), member.span());
        }
        if (member.target() instanceof Identifier identifier) {
            if (unavailablePackageHardware(identifier.name())) {
                error("MPL1414", "包代码不能访问未通过 with 注入的根项目硬件", member.span());
                return new HirConstant("0", ValueType.ERROR);
            }
            HardwareContract.Resource resource = hardwareResources.get(identifier.name());
            if (resource != null && "Display".equals(resource.mplType())
                && ("width".equals(member.member()) || "height".equals(member.member()))) {
                HardwareContract.DisplayLayout layout = resource.display().orElse(null);
                if (layout == null) {
                    error("MPL3203", "Display 编译期尺寸未知；请在 link(...) 中声明 width/height", member.span());
                    return new HirConstant("0", ValueType.ERROR);
                }
                int value = "width".equals(member.member()) ? layout.width() : layout.height();
                return new HirConstant(Integer.toString(value), ValueType.INT);
            }
            HardwareContract.LinkDeclaration hardware = hardwareLinks.get(identifier.name());
            if (hardware != null) {
                TargetProfile.BuildingType building = profile.buildingType(hardware.mplType()).orElse(null);
                ValueType type = building == null ? null : building.propertyTypes().get(member.member());
                if (type == null) {
                    error("MPL3201", "硬件 " + hardware.mplName() + "（" + hardware.mplType()
                        + "）不支持只读属性：" + member.member(), member.span());
                    return new HirConstant("0", ValueType.ERROR);
                }
                return new HirMemberAccess(new HirHardwareLink(hardware.mplName(), hardware.gameAlias(), hardware.mplType()),
                    member.member(), type);
            }
        }
        if ("size".equals(member.member())) {
            HirUnitQuery query = resolveSavedUnitQuery(member.target(), "_");
            if (query == null) {
                query = parseUnitQuery(member.target()).map(value -> analyzeUnitQuery(value, "_")).orElse(null);
            }
            if (query != null) {
                if (currentFunction != null) {
                    error("MPL3508", "第一版函数不能扫描 Set<Unit<T>>.size", member.span());
                    return new HirConstant("0", ValueType.ERROR);
                }
                if (unitIterationDepth > 0) {
                    error("MPL3306", "Set<Unit<T>>.size 不能嵌套在 Unit 遍历中", member.span());
                    return new HirConstant("0", ValueType.ERROR);
                }
                return new HirUnitQuerySize(query);
            }
            HirBuildingQuery buildingQuery = resolveBuildingQuery(member.target(), "_");
            if (buildingQuery != null) return new HirBuildingQuerySize(buildingQuery);
        }
        HirExpression target = analyzeBorrowedObjectExpression(member.target());
        if (target.type() == ValueType.STRING && "length".equals(member.member())) {
            return new HirStringLength(target);
        }
        if (target.type() instanceof ObjectType object) {
            if (object.nullable()) {
                error("MPL3706", "可空 " + object.displayName() + " 必须先通过 != null 检查", member.span());
                return new HirObjectFieldRead(target, object.className(), member.member(), ValueType.ERROR);
            }
            ClassInfo type = classes.get(object.className());
            FieldInfo field = type == null ? null : lookupField(type, member.member(), true);
            if (field == null) {
                error("MPL3705", "类 " + object.className() + " 没有字段：" + member.member(), member.span());
                return new HirObjectFieldRead(target, object.className(), member.member(), ValueType.ERROR);
            }
            if (!field.publicAccess() && !field.ownerClass().equals(currentClass)) {
                error("MPL3707", "字段 " + field.ownerClass() + "." + member.member() + " 是 private", member.span());
            }
            return new HirObjectFieldRead(target, field.ownerClass(), member.member(), field.type());
        }
        if ("size".equals(member.member()) && target.type() instanceof CollectionType collection
            && collection.kind() == CollectionType.Kind.MUTABLE_LIST) {
            return new HirMemberAccess(target, "size", ValueType.INT);
        }
        if ("size".equals(member.member()) && isAggregate(target.type())) {
            Integer size = staticAggregateSize(target);
            if (size == null) {
                error("MPL3601", "当前聚合值缺少可静态证明的长度", member.span());
                return new HirConstant("0", ValueType.ERROR);
            }
            return new HirConstant(Integer.toString(size), ValueType.INT);
        }
        if (target.type() == ValueType.UNIT) {
            if ("flag".equals(member.member())) {
                error("MPL3304", "Unit.flag 是编译器私有运行时属性，MPL 不允许访问", member.span());
                return new HirMemberAccess(target, member.member(), ValueType.ERROR);
            }
            Optional<ValueType> type = profile.unitPropertyType(member.member());
            if (type.isEmpty()) {
                error("MPL3304", "当前 target 的 Unit 不支持只读属性：" + member.member(), member.span());
                return new HirMemberAccess(target, member.member(), ValueType.ERROR);
            }
            return new HirMemberAccess(target, member.member(), type.orElseThrow());
        }
        if (target.type() instanceof UnitType unit) {
            if (unit.nullable()) {
                error("MPL3308", "可空 " + unit.displayName() + " 必须先通过 != null 检查", member.span());
                return new HirMemberAccess(target, member.member(), ValueType.ERROR);
            }
            if (currentFunction != null || unitIterationDepth > 0) {
                error("MPL3306", "第一版不能在函数或 Unit 遍历体中重绑已保存的 UnitRef", member.span());
            }
            if ("flag".equals(member.member())) {
                error("MPL3304", "Unit.flag 是编译器私有运行时属性，MPL 不允许访问", member.span());
                return new HirMemberAccess(target, member.member(), ValueType.ERROR);
            }
            Optional<ValueType> type = profile.unitPropertyType(member.member());
            if (type.isEmpty()) {
                error("MPL3304", "当前 target 的 Unit 不支持只读属性：" + member.member(), member.span());
                return new HirMemberAccess(target, member.member(), ValueType.ERROR);
            }
            return new HirMemberAccess(target, member.member(), type.orElseThrow());
        }
        if (target.type() instanceof BuildingType buildingReference) {
            if (buildingReference.nullable()) {
                error("MPL3211", "可空 " + buildingReference.displayName() + " 必须先通过 != null 检查", member.span());
                return new HirMemberAccess(target, member.member(), ValueType.ERROR);
            }
            TargetProfile.BuildingType building = profile.buildingType(buildingReference.buildingType()).orElseThrow();
            ValueType type = building.propertyTypes().get(member.member());
            if (type == null) {
                error("MPL3201", "建筑 " + buildingReference.buildingType() + " 不支持只读属性：" + member.member(),
                    member.span());
                return new HirMemberAccess(target, member.member(), ValueType.ERROR);
            }
            return new HirMemberAccess(target, member.member(), type);
        }
        if (target.type() == ValueType.BUILDING && target instanceof HirVariable variable
            && variable.name().equals(activeBuildingBinding) && activeBuildingType != null) {
            TargetProfile.BuildingType building = profile.buildingType(activeBuildingType).orElseThrow();
            ValueType type = building.propertyTypes().get(member.member());
            if (type == null) {
                error("MPL3201", "建筑 " + activeBuildingType + " 不支持只读属性：" + member.member(), member.span());
                return new HirMemberAccess(target, member.member(), ValueType.ERROR);
            }
            return new HirMemberAccess(target, member.member(), type);
        }
        error("MPL3201", "当前阶段不支持该成员访问", member.span());
        return new HirConstant("0", ValueType.ERROR);
    }

    private boolean unavailablePackageHardware(String name) {
        return name.startsWith("__package_hardware_unavailable_");
    }

    private HirExpression analyzeCallExpression(CallExpression call) {
        if (call.callee() instanceof Identifier identifier && "super".equals(identifier.name())) {
            return analyzeSuperConstructorCall(call.arguments(), call.span());
        }
        if (call.callee() instanceof MemberAccessExpression member
            && member.target() instanceof Identifier identifier && "super".equals(identifier.name())) {
            return analyzeSuperMethodCall(member.member(), call.arguments(), call.span());
        }
        Optional<CollectionType.Kind> factory = collectionFactory(call.callee());
        if (factory.isPresent()) return analyzeCollectionFactory(factory.orElseThrow(), call.arguments(), call.span());
        if (mutableListCapacityFactory(call.callee())) {
            error("MPL3601", "MutableList.withCapacity(capacity) 需要在显式 MutableList<T> 声明中使用", call.span());
            return new HirConstant("0", ValueType.ERROR);
        }
        if (call.callee() instanceof MemberAccessExpression member && "get".equals(member.member())
            && call.arguments().size() == 1) {
            HirUnitQuery query = resolveUnitQuery(member.target(), "_");
            if (query != null) return analyzeUnitQueryGet(query, call.arguments().get(0), call.span());
            HirBuildingQuery buildingQuery = resolveBuildingQuery(member.target(), "_");
            if (buildingQuery != null) return analyzeBuildingQueryGet(buildingQuery, call.arguments().get(0), call.span());
            return analyzeIndexAccess(member.target(), call.arguments().get(0), call.span());
        }
        if (call.callee() instanceof MemberAccessExpression member && "get".equals(member.member())) {
            HirUnitQuery query = resolveUnitQuery(member.target(), "_");
            if (query != null) {
                error("MPL3309", "Set<Unit<T>>.get(index) 需要恰好一个 Int 参数", call.span());
                return new HirConstant("null", ValueType.ERROR);
            }
            HirBuildingQuery buildingQuery = resolveBuildingQuery(member.target(), "_");
            if (buildingQuery != null) {
                error("MPL3210", "Set<Building<T>>.get(index) 需要恰好一个 Int 参数", call.span());
                return new HirConstant("null", ValueType.ERROR);
            }
        }
        if (call.callee() instanceof MemberAccessExpression member && "contains".equals(member.member())
            && call.arguments().size() == 1) {
            return analyzeCollectionContains(member.target(), call.arguments().get(0), call.span());
        }
        if (call.callee() instanceof MemberAccessExpression member
            && isPotentialObjectReceiver(member.target())) {
            HirExpression target = analyzeBorrowedObjectExpression(member.target());
            if (target.type() instanceof ObjectType object) {
                return analyzeObjectMethodCall(target, member.target(), object, member.member(), call.arguments(), call.span());
            }
        }
        if (call.callee() instanceof Identifier functionName) {
            List<FunctionSignature> overloads = functionOverloads.get(functionName.name());
            List<HirExpression> rawArguments = analyzeRawArguments(call.arguments());
            if (overloads == null || overloads.isEmpty()) {
                error("MPL3501", "未声明的函数：" + functionName.name(), call.span());
                return new HirConstant("0", ValueType.ERROR);
            }
            FunctionSignature signature = selectOverload("函数 " + functionName.name(), overloads,
                FunctionSignature::parameterTypes, rawArguments, call.span());
            if (signature == null) return new HirConstant("0", ValueType.ERROR);
            if (signature.returnsOwnedObject() && call != allowedOwnedFactoryCall) {
                error("MPL3708", "对象工厂 " + functionName.name()
                    + "() 的结果必须直接初始化一个不可空 val", call.span());
            }
            List<HirExpression> arguments = adaptArguments(rawArguments, signature.parameterTypes());
            recordCall(signature.internalName(), call.span());
            return new HirFunctionCall(signature.internalName(), arguments, signature.returnType(),
                signature.returnType() == ValueType.STRING ? nextStringAllocationId++ : 0,
                isFunctionArray(signature.returnType()) ? aggregateReturnSize(signature) : 0);
        }
        if (call.callee() instanceof MemberAccessExpression member
            && member.target() instanceof Identifier namespace) {
            if ("Math".equals(namespace.name())) return mathIntrinsic(member.member(), call.arguments(), call.span());
            if ("Clock".equals(namespace.name())) return clockIntrinsic(member.member(), call.arguments(), call.span());
            if ("Int".equals(namespace.name())) return intIntrinsic(member.member(), call.arguments(), call.span());
        }
        if (call.callee() instanceof MemberAccessExpression member
            && member.target() instanceof Identifier namespace
            && "Unit".equals(namespace.name())
            && member.member().startsWith("getAll")) {
            error("MPL3301", "Unit.getAll类型() 只能用作 for 的遍历目标", call.span());
            return new HirConstant("0", ValueType.ERROR);
        }
        if (call.callee() instanceof MemberAccessExpression member
            && member.target() instanceof Identifier identifier
            && hardwareLinks.containsKey(identifier.name())) {
            error("MPL3201", "硬件控制方法只能作为独立语句调用", call.span());
            return new HirConstant("0", ValueType.ERROR);
        }
        error("MPL3201", "当前阶段不支持该调用表达式", call.span());
        return new HirConstant("0", ValueType.ERROR);
    }

    private HirExpression analyzeSuperConstructorCall(List<Expression> sourceArguments, SourceSpan span) {
        List<HirExpression> rawArguments = analyzeRawArguments(sourceArguments);
        ClassInfo type = currentClass == null ? null : classes.get(currentClass);
        if (currentMethod == null || !currentMethod.constructor() || type == null || type.parent() == null) {
            error("MPL3715", "super(...) 只能出现在派生类构造器中", span);
            return new HirConstant("0", ValueType.ERROR);
        }
        ClassInfo parent = type.parent();
        MethodInfo constructor = selectOverload("父类构造器 " + parent.name(),
            new ArrayList<>(parent.constructors().values()), MethodInfo::parameterTypes, rawArguments, span);
        if (constructor == null) return new HirConstant("0", ValueType.ERROR);
        if (!constructor.publicAccess()) {
            error("MPL3707", "父类构造器 " + parent.name() + " 是 private", span);
        }
        recordCall(constructor.internalName(), span);
        return new HirMethodCall(new HirVariable("this", new ObjectType(type.name(), false)), "super",
            adaptArguments(rawArguments, constructor.parameterTypes()),
            List.of(new HirMethodCall.DispatchTarget(parent.name(), constructor.internalName())),
            HirMethodCall.InvocationKind.SUPER_CONSTRUCTOR, ValueType.VOID, 0);
    }

    private HirExpression analyzeSuperMethodCall(String methodName, List<Expression> sourceArguments, SourceSpan span) {
        List<HirExpression> rawArguments = analyzeRawArguments(sourceArguments);
        ClassInfo type = currentClass == null ? null : classes.get(currentClass);
        if (currentMethod == null || type == null || type.parent() == null) {
            error("MPL3715", "super." + methodName + "(...) 只能出现在派生类方法中", span);
            return new HirConstant("0", ValueType.ERROR);
        }
        ClassInfo parent = type.parent();
        List<MethodInfo> candidates = methodCandidates(parent, methodName).stream()
            .filter(MethodInfo::publicAccess).toList();
        if (candidates.isEmpty()) {
            error("MPL3705", "父类 " + parent.name() + " 没有可访问方法：" + methodName, span);
            return new HirConstant("0", ValueType.ERROR);
        }
        MethodInfo method = selectOverload("父类方法 " + parent.name() + "." + methodName, candidates,
            MethodInfo::parameterTypes, rawArguments, span);
        if (method == null) return new HirConstant("0", ValueType.ERROR);
        recordCall(method.internalName(), span);
        return new HirMethodCall(new HirVariable("this", new ObjectType(type.name(), false)), methodName,
            adaptArguments(rawArguments, method.parameterTypes()),
            List.of(new HirMethodCall.DispatchTarget(method.ownerClass(), method.internalName())),
            HirMethodCall.InvocationKind.SUPER_METHOD, method.returnType(),
            method.returnType() == ValueType.STRING ? nextStringAllocationId++ : 0);
    }

    private boolean isPotentialObjectReceiver(Expression expression) {
        if (!(expression instanceof Identifier identifier)) return true;
        Symbol symbol = lookup(identifier.name());
        return symbol != null && symbol.type() instanceof ObjectType;
    }

    private boolean isRestrictedLocalObject(Expression expression) {
        if (!(expression instanceof Identifier identifier)) return false;
        Symbol symbol = lookup(identifier.name());
        return symbol != null && (symbol.reusableLocalObject() || symbol.ownsPooledObject());
    }

    private HirExpression analyzeBorrowedObjectExpression(Expression expression) {
        if (!(expression instanceof Identifier)) return analyzeExpression(expression);
        boolean previous = borrowedObjectUse;
        borrowedObjectUse = true;
        try {
            return analyzeExpression(expression);
        } finally {
            borrowedObjectUse = previous;
        }
    }

    private <T> T analyzeWithObjectAllocationContext(ObjectAllocationContext context,
                                                     java.util.function.Supplier<T> analysis) {
        ObjectAllocationContext previous = objectAllocationContext;
        objectAllocationContext = context;
        try {
            return analysis.get();
        } finally {
            objectAllocationContext = previous;
        }
    }

    private HirExpression analyzeNew(NewExpression allocation) {
        ClassInfo type = classes.get(allocation.className());
        if (type == null) {
            error("MPL3701", "未声明的类：" + allocation.className(), allocation.span());
            allocation.arguments().forEach(this::analyzeExpression);
            return new HirConstant("0", ValueType.ERROR);
        }
        if (objectAllocationContext == ObjectAllocationContext.DISALLOWED) {
            error("MPL3708", "new 只能用于顶层静态值，或直接初始化不逃逸的局部 val", allocation.span());
        }
        ObjectAllocationContext argumentContext = objectAllocationContext == ObjectAllocationContext.REUSABLE_LOCAL
            || objectAllocationContext == ObjectAllocationContext.POOLED_RETURN
            ? ObjectAllocationContext.DISALLOWED : objectAllocationContext;
        List<HirExpression> rawArguments = analyzeWithObjectAllocationContext(argumentContext,
            () -> analyzeRawArguments(allocation.arguments()));
        MethodInfo constructor = selectOverload("构造器 " + type.name(),
            new ArrayList<>(type.constructors().values()), MethodInfo::parameterTypes, rawArguments, allocation.span());
        if (constructor == null) return new HirConstant("0", ValueType.ERROR);
        if (!constructor.publicAccess() && !type.name().equals(currentClass)) {
            error("MPL3707", "构造器 " + type.name() + " 是 private", allocation.span());
        }
        if ((objectAllocationContext == ObjectAllocationContext.REUSABLE_LOCAL
            || objectAllocationContext == ObjectAllocationContext.POOLED_RETURN) && constructor.receiverEscapes()) {
            error("MPL3708", "构造器 " + type.name() + " 会泄露 this，不能用于编译器管理的对象生命周期",
                allocation.span());
        }
        if (objectAllocationContext == ObjectAllocationContext.POOLED_RETURN) {
            for (FieldInfo field : type.effectiveFields().values()) {
                if (!supportedPooledObjectField(field.type())) {
                    error("MPL3708", "对象池字段只支持 Int、Float、Bool 及其单层元组："
                        + type.name() + "." + field.name(), allocation.span());
                }
            }
        }
        List<HirExpression> arguments = adaptArguments(rawArguments, constructor.parameterTypes());
        recordCall(constructor.internalName(), allocation.span());
        HirNewObject.AllocationKind allocationKind = objectAllocationContext == ObjectAllocationContext.POOLED_RETURN
            ? HirNewObject.AllocationKind.POOLED : HirNewObject.AllocationKind.FIXED;
        return new HirNewObject(nextObjectAllocationId++, type.name(), constructor.internalName(), arguments,
            new ObjectType(type.name(), false), allocationKind);
    }

    private boolean supportedPooledObjectField(MplType type) {
        if (type == ValueType.INT || type == ValueType.FLOAT || type == ValueType.BOOL) return true;
        return type instanceof TupleType tuple && tuple.elementTypes().stream()
            .allMatch(element -> element == ValueType.INT || element == ValueType.FLOAT || element == ValueType.BOOL);
    }

    private HirExpression analyzeObjectMethodCall(HirExpression target, Expression sourceTarget, ObjectType object, String methodName,
                                                  List<Expression> sourceArguments, SourceSpan span) {
        if (object.nullable()) {
            error("MPL3706", "可空 " + object.displayName() + " 必须先通过 != null 检查", span);
            sourceArguments.forEach(this::analyzeExpression);
            return new HirConstant("0", ValueType.ERROR);
        }
        ClassInfo type = classes.get(object.className());
        List<HirExpression> rawArguments = analyzeRawArguments(sourceArguments);
        List<MethodInfo> candidates = type == null ? List.of() : methodCandidates(type, methodName);
        if (candidates.isEmpty()) {
            error("MPL3705", "类 " + object.className() + " 没有实例方法：" + methodName, span);
            return new HirConstant("0", ValueType.ERROR);
        }
        MethodInfo method = selectOverload("方法 " + object.className() + "." + methodName, candidates,
            MethodInfo::parameterTypes, rawArguments, span);
        if (method == null) return new HirConstant("0", ValueType.ERROR);
        if (!method.publicAccess() && !method.ownerClass().equals(currentClass)) {
            error("MPL3707", "方法 " + method.ownerClass() + "." + methodName + " 是 private", span);
        }
        if (isRestrictedLocalObject(sourceTarget) && method.receiverEscapes()) {
            error("MPL3708", "方法 " + object.className() + "." + methodName
                + " 会泄露接收者，不能对编译器管理的对象调用", span);
        }
        List<HirMethodCall.DispatchTarget> dispatch = method.publicAccess()
            ? virtualDispatchTargets(type, method.key())
            : classes.values().stream().filter(runtime -> typeRelations.isSubtype(runtime.name(), type.name()))
                .map(runtime -> new HirMethodCall.DispatchTarget(runtime.name(), method.internalName())).toList();
        dispatch.stream().map(HirMethodCall.DispatchTarget::function).distinct()
            .forEach(function -> recordCall(function, span));
        return new HirMethodCall(target, methodName, adaptArguments(rawArguments, method.parameterTypes()), dispatch,
            HirMethodCall.InvocationKind.VIRTUAL, method.returnType(),
            method.returnType() == ValueType.STRING ? nextStringAllocationId++ : 0);
    }

    private List<MethodInfo> methodCandidates(ClassInfo type, String sourceName) {
        Map<MethodKey, MethodInfo> result = new LinkedHashMap<>();
        ClassInfo current = type;
        boolean inherited = false;
        while (current != null) {
            for (MethodInfo method : current.methods().values()) {
                if (!method.sourceName().equals(sourceName)) continue;
                if (inherited && !method.publicAccess()) continue;
                result.putIfAbsent(method.key(), method);
            }
            inherited = true;
            current = current.parent();
        }
        return List.copyOf(result.values());
    }

    private MethodInfo effectiveMethod(ClassInfo runtimeType, MethodKey key) {
        ClassInfo current = runtimeType;
        while (current != null) {
            MethodInfo method = current.methods().get(key);
            if (method != null && method.publicAccess()) return method;
            current = current.parent();
        }
        return null;
    }

    private List<HirMethodCall.DispatchTarget> virtualDispatchTargets(ClassInfo staticType, MethodKey key) {
        List<HirMethodCall.DispatchTarget> result = new ArrayList<>();
        for (ClassInfo runtimeType : classes.values()) {
            if (!typeRelations.isSubtype(runtimeType.name(), staticType.name())) continue;
            MethodInfo implementation = effectiveMethod(runtimeType, key);
            if (implementation != null) {
                result.add(new HirMethodCall.DispatchTarget(runtimeType.name(), implementation.internalName()));
            }
        }
        return List.copyOf(result);
    }

    private List<HirExpression> analyzeArguments(String callable, List<MplType> parameterTypes,
                                                 List<Expression> sourceArguments, SourceSpan span) {
        if (sourceArguments.size() != parameterTypes.size()) {
            error("MPL3503", callable + " 的参数数量不匹配", span);
        }
        List<HirExpression> arguments = new ArrayList<>();
        for (int index = 0; index < sourceArguments.size(); index++) {
            Expression source = sourceArguments.get(index);
            HirExpression argument = analyzeExpression(source);
            if (index < parameterTypes.size() && !canAssign(parameterTypes.get(index), argument.type())) {
                error("MPL3503", callable + " 的第 " + (index + 1) + " 个参数类型不匹配", source.span());
            }
            arguments.add(index < parameterTypes.size()
                ? snapshotStringArgument(argument, parameterTypes.get(index)) : argument);
        }
        return List.copyOf(arguments);
    }

    private List<HirExpression> analyzeRawArguments(List<Expression> sourceArguments) {
        return sourceArguments.stream().map(this::analyzeExpression).toList();
    }

    private List<HirExpression> adaptArguments(List<HirExpression> arguments, List<MplType> parameterTypes) {
        List<HirExpression> result = new ArrayList<>(arguments.size());
        for (int index = 0; index < arguments.size(); index++) {
            HirExpression argument = arguments.get(index);
            result.add(index < parameterTypes.size()
                ? snapshotStringArgument(argument, parameterTypes.get(index)) : argument);
        }
        return List.copyOf(result);
    }

    private <T> T selectOverload(String callable, List<T> candidates,
                                 java.util.function.Function<T, List<MplType>> parameters,
                                 List<HirExpression> arguments, SourceSpan span) {
        List<T> sameArity = candidates.stream()
            .filter(candidate -> parameters.apply(candidate).size() == arguments.size()).toList();
        if (sameArity.isEmpty()) {
            error("MPL3503", callable + " 没有接受 " + arguments.size() + " 个参数的重载", span);
            return null;
        }
        List<T> applicable = sameArity.stream().filter(candidate -> {
            List<MplType> types = parameters.apply(candidate);
            for (int index = 0; index < types.size(); index++) {
                if (!canAssign(types.get(index), arguments.get(index).type())) return false;
            }
            return true;
        }).toList();
        if (applicable.isEmpty()) {
            String actual = arguments.stream().map(HirExpression::type).map(MplType::displayName)
                .collect(java.util.stream.Collectors.joining(", "));
            error("MPL3503", callable + " 没有可接受参数类型 (" + actual + ") 的重载", span);
            return null;
        }
        if (applicable.size() == 1) return applicable.get(0);
        if (arguments.stream().anyMatch(argument -> argument.type() == ValueType.ERROR)) return applicable.get(0);

        List<T> mostSpecific = applicable.stream().filter(candidate -> applicable.stream().noneMatch(other ->
            other != candidate && moreSpecific(parameters.apply(other), parameters.apply(candidate)))).toList();
        if (mostSpecific.size() == 1) return mostSpecific.get(0);
        error("MPL3511", callable + " 调用存在二义性；候选为 " + mostSpecific.stream()
            .map(candidate -> callableDisplay(callable, parameters.apply(candidate)))
            .collect(java.util.stream.Collectors.joining("、")), span);
        return null;
    }

    private boolean moreSpecific(List<MplType> candidate, List<MplType> other) {
        boolean strict = false;
        for (int index = 0; index < candidate.size(); index++) {
            MplType candidateType = candidate.get(index);
            MplType otherType = other.get(index);
            if (candidateType.equals(otherType)) continue;
            if (!canAssign(otherType, candidateType)) return false;
            strict = true;
        }
        return strict;
    }

    private HirExpression snapshotStringArgument(HirExpression argument, MplType parameterType) {
        if (parameterType != ValueType.STRING || argument.type() != ValueType.STRING) return argument;
        return new HirStringSnapshot(nextStringAllocationId++, argument, stringMaxLength(argument));
    }

    private void recordCall(String function, SourceSpan span) {
        if (currentFunction != null) callGraph.get(currentFunction).add(function);
        if (analyzingTopLevel) {
            topLevelCalls.add(new TopLevelCall(function, Set.copyOf(initializedGlobals), span));
        }
    }

    private HirExpression analyzeObjectFieldAssignment(MemberAssignmentExpression assignment) {
        HirExpression target = analyzeBorrowedObjectExpression(assignment.target());
        HirExpression value = analyzeExpression(assignment.value());
        if (!(target.type() instanceof ObjectType object)) {
            error("MPL3705", "成员赋值目标必须是用户对象", assignment.target().span());
            return new HirObjectFieldAssignment(target, "<error>", assignment.member(), assignment.operator(), value,
                ValueType.ERROR);
        }
        if (object.nullable()) {
            error("MPL3706", "可空 " + object.displayName() + " 必须先通过 != null 检查", assignment.span());
        }
        ClassInfo type = classes.get(object.className());
        FieldInfo field = type == null ? null : lookupField(type, assignment.member(), true);
        if (field == null) {
            error("MPL3705", "类 " + object.className() + " 没有字段：" + assignment.member(), assignment.span());
            return new HirObjectFieldAssignment(target, object.className(), assignment.member(), assignment.operator(),
                value, ValueType.ERROR);
        }
        if (!field.publicAccess() && !field.ownerClass().equals(currentClass)) {
            error("MPL3707", "字段 " + field.ownerClass() + "." + assignment.member() + " 是 private", assignment.span());
        }
        if ("=".equals(assignment.operator())) {
            if (!canAssign(field.type(), value.type())) {
                error("MPL3103", "不能将 " + display(value.type()) + " 赋给 " + display(field.type()),
                    assignment.value().span());
            }
        } else {
            if (field.type() == ValueType.STRING) {
                error("MPL3103", "String 对象字段暂不支持复合赋值", assignment.span());
                return new HirObjectFieldAssignment(target, field.ownerClass(), assignment.member(),
                    assignment.operator(), value, ValueType.ERROR);
            }
            ValueType result = binaryType(assignment.operator().substring(0, 1), field.type(), value.type(),
                assignment.span());
            if (!canAssign(field.type(), result)) {
                error("MPL3103", "复合赋值结果不能赋给 " + display(field.type()), assignment.span());
            }
        }
        return new HirObjectFieldAssignment(target, field.ownerClass(), assignment.member(), assignment.operator(),
            value, field.type());
    }

    private HirExpression mathIntrinsic(String name, List<Expression> sourceArguments, SourceSpan span) {
        if (!"sin".equals(name) && !"cos".equals(name)) {
            error("MPL3201", "Math 目前仅支持 sin(x) 与 cos(x)", span);
            return new HirConstant("0", ValueType.ERROR);
        }
        if (sourceArguments.size() != 1) {
            error("MPL3201", "Math." + name + "(...) 需要恰好一个数值参数", span);
        }
        List<HirExpression> arguments = new ArrayList<>();
        for (Expression argument : sourceArguments) {
            HirExpression value = analyzeExpression(argument);
            requireNumeric(value.type(), argument.span(), "Math." + name + " 参数");
            arguments.add(value);
        }
        return new HirIntrinsicCall("Math", name, arguments, ValueType.FLOAT);
    }

    private HirExpression clockIntrinsic(String name, List<Expression> sourceArguments, SourceSpan span) {
        if (!sourceArguments.isEmpty()) {
            error("MPL3201", "Clock." + name + " 不接受参数", span);
        }
        ValueType type = switch (name) {
            // v146 exposes both @time and @tick as game-time doubles.  Keep the
            // exact target value rather than silently truncating @tick to Int.
            case "timeMs", "time", "timeMinutes", "timeHours", "tick" -> ValueType.FLOAT;
            default -> ValueType.ERROR;
        };
        if (type == ValueType.ERROR) {
            error("MPL3201", "Clock 目前支持 timeMs、time、timeMinutes、timeHours 与 tick", span);
            return new HirConstant("0", ValueType.ERROR);
        }
        return new HirIntrinsicCall("Clock", name, List.of(), type);
    }

    private HirExpression intIntrinsic(String name, List<Expression> sourceArguments, SourceSpan span) {
        if (!"floor".equals(name) && !"ceil".equals(name) && !"round".equals(name)) {
            error("MPL3201", "Int 目前支持 floor(x)、ceil(x) 与 round(x)", span);
            return new HirConstant("0", ValueType.ERROR);
        }
        if (sourceArguments.size() != 1) {
            error("MPL3201", "Int." + name + "(...) 需要恰好一个 Float 参数", span);
        }
        List<HirExpression> arguments = new ArrayList<>();
        for (Expression argument : sourceArguments) {
            HirExpression value = analyzeExpression(argument);
            if (value.type() != ValueType.FLOAT && value.type() != ValueType.ERROR) {
                error("MPL3103", "Int." + name + "(...) 只接受 Float 参数", argument.span());
            }
            arguments.add(value);
        }
        return new HirIntrinsicCall("Int", name, arguments, ValueType.INT);
    }

    private ValueType binaryType(String operator, MplType left, MplType right, SourceSpan span) {
        return switch (operator) {
            case "+" -> left == ValueType.STRING || right == ValueType.STRING
                ? left == ValueType.STRING && right == ValueType.STRING ? ValueType.STRING
                    : typeError("String 拼接两侧都必须是 String", span)
                : numericResult(left, right, span, "运算符 +");
            case "-", "*" -> numericResult(left, right, span, "运算符 " + operator);
            case "/" -> {
                requireNumeric(left, span, "运算符 /");
                requireNumeric(right, span, "运算符 /");
                yield left == ValueType.ERROR || right == ValueType.ERROR ? ValueType.ERROR : ValueType.FLOAT;
            }
            case "%" -> left == ValueType.INT && right == ValueType.INT ? ValueType.INT
                : typeError("运算符 % 只接受 Int", span);
            case "<", "<=", ">", ">=" -> {
                requireNumeric(left, span, "比较运算符 " + operator);
                requireNumeric(right, span, "比较运算符 " + operator);
                yield left == ValueType.ERROR || right == ValueType.ERROR ? ValueType.ERROR : ValueType.BOOL;
            }
            case "==", "!=" -> compatibleForEquality(left, right, span) ? ValueType.BOOL : ValueType.ERROR;
            case "===", "!==" -> compatibleForIdentity(left, right, span) ? ValueType.BOOL : ValueType.ERROR;
            case "&&", "||" -> {
                requireBool(left, span, "逻辑运算符 " + operator);
                requireBool(right, span, "逻辑运算符 " + operator);
                yield left == ValueType.ERROR || right == ValueType.ERROR ? ValueType.ERROR : ValueType.BOOL;
            }
            default -> typeError("暂不支持的运算符：" + operator, span);
        };
    }

    private ValueType numericResult(MplType left, MplType right, SourceSpan span, String context) {
        requireNumeric(left, span, context);
        requireNumeric(right, span, context);
        if (left == ValueType.ERROR || right == ValueType.ERROR) return ValueType.ERROR;
        return left == ValueType.FLOAT || right == ValueType.FLOAT ? ValueType.FLOAT : ValueType.INT;
    }

    private ValueType requireNumeric(MplType type, SourceSpan span, String context) {
        if (type == ValueType.INT || type == ValueType.FLOAT || type == ValueType.ERROR) {
            return (ValueType) type;
        }
        return typeError(context + " 只接受 Int 或 Float", span);
    }

    private ValueType requireBool(MplType type, SourceSpan span, String context) {
        if (type == ValueType.BOOL || type == ValueType.ERROR) return (ValueType) type;
        return typeError(context + " 只接受 Bool", span);
    }

    private boolean compatibleForEquality(MplType left, MplType right, SourceSpan span) {
        if (left == ValueType.ERROR || right == ValueType.ERROR || left == right
            || left == ValueType.INT && right == ValueType.FLOAT || left == ValueType.FLOAT && right == ValueType.INT) {
            return true;
        }
        if (left instanceof UnitType unit && right == ValueType.NULL) {
            if (unit.nullable()) return true;
            error("MPL3103", "非空 " + unit.displayName() + " 不需要与 null 比较", span);
            return false;
        }
        if (right instanceof UnitType unit && left == ValueType.NULL) {
            if (unit.nullable()) return true;
            error("MPL3103", "非空 " + unit.displayName() + " 不需要与 null 比较", span);
            return false;
        }
        if (left instanceof UnitType leftUnit && right instanceof UnitType rightUnit
            && leftUnit.unitType().equals(rightUnit.unitType())) {
            return true;
        }
        if (left instanceof BuildingType building && right == ValueType.NULL) {
            if (building.nullable()) return true;
            error("MPL3103", "非空 " + building.displayName() + " 不需要与 null 比较", span);
            return false;
        }
        if (right instanceof BuildingType building && left == ValueType.NULL) {
            if (building.nullable()) return true;
            error("MPL3103", "非空 " + building.displayName() + " 不需要与 null 比较", span);
            return false;
        }
        if (left instanceof BuildingType leftBuilding && right instanceof BuildingType rightBuilding
            && leftBuilding.buildingType().equals(rightBuilding.buildingType())) {
            return true;
        }
        if (left instanceof ObjectType object && right == ValueType.NULL) {
            if (object.nullable()) return true;
            error("MPL3103", "非空 " + object.displayName() + " 不需要与 null 比较", span);
            return false;
        }
        if (right instanceof ObjectType object && left == ValueType.NULL) {
            if (object.nullable()) return true;
            error("MPL3103", "非空 " + object.displayName() + " 不需要与 null 比较", span);
            return false;
        }
        error("MPL3103", "不能比较 " + display(left) + " 与 " + display(right), span);
        return false;
    }

    private boolean compatibleForIdentity(MplType left, MplType right, SourceSpan span) {
        if (left == ValueType.ERROR || right == ValueType.ERROR) return true;
        if (left instanceof ObjectType object && right == ValueType.NULL) return object.nullable();
        if (right instanceof ObjectType object && left == ValueType.NULL) return object.nullable();
        if (left instanceof ObjectType leftObject && right instanceof ObjectType rightObject
            && (typeRelations.isSubtype(leftObject.className(), rightObject.className())
                || typeRelations.isSubtype(rightObject.className(), leftObject.className()))) {
            return true;
        }
        error("MPL3103", "对象身份比较需要同一用户类的引用", span);
        return false;
    }

    private MplType parseType(String name, SourceSpan span) {
        if (name.startsWith("LinkedBuildingSet<")) {
            return typeError("LinkedBuildingSet<T> 已移除；请使用 Set<Building<T>>", span);
        }
        boolean nullable = name.endsWith("?");
        String nonNullableName = nullable ? name.substring(0, name.length() - 1) : name;
        if (nonNullableName.startsWith("Unit<") && nonNullableName.endsWith(">")) {
            String unitType = nonNullableName.substring("Unit<".length(), nonNullableName.length() - 1);
            if (profile.unitType(unitType).filter(TargetProfile.UnitType::logicControllable).isEmpty()) {
                return typeError("当前 target 不支持 Unit 类型：" + unitType, span);
            }
            return new UnitType(unitType, nullable);
        }
        if (nonNullableName.startsWith("Building<") && nonNullableName.endsWith(">")) {
            String buildingType = nonNullableName.substring("Building<".length(), nonNullableName.length() - 1);
            if (profile.buildingType(buildingType).isEmpty()) {
                return typeError("当前 target 不支持 Building 类型：" + buildingType, span);
            }
            return new BuildingType(buildingType, nullable);
        }
        if (classes.containsKey(nonNullableName)) return new ObjectType(nonNullableName, nullable);
        if (nullable) return typeError("可空类型必须是 Unit<T>、Building<T> 或用户类", span);
        if (name.endsWith("[]")) {
            MplType element = parseType(name.substring(0, name.length() - 2), span);
            return collectionType(CollectionType.Kind.ARRAY, element, span);
        }
        if (name.startsWith("List<") && name.endsWith(">")) {
            return collectionType(CollectionType.Kind.LIST, name.substring("List<".length(), name.length() - 1), span);
        }
        if (name.startsWith("MutableList<") && name.endsWith(">")) {
            return collectionType(CollectionType.Kind.MUTABLE_LIST,
                name.substring("MutableList<".length(), name.length() - 1), span);
        }
        if (name.startsWith("Set<") && name.endsWith(">")) {
            return collectionType(CollectionType.Kind.SET, name.substring("Set<".length(), name.length() - 1), span);
        }
        if (name.startsWith("(") && name.endsWith(")")) {
            List<String> members = splitTopLevel(name.substring(1, name.length() - 1));
            if (members.size() < 2) return typeError("元组类型至少需要两个元素", span);
            List<MplType> types = members.stream().map(member -> parseType(member, span)).toList();
            if (types.contains(ValueType.ERROR) || types.contains(ValueType.VOID)) return ValueType.ERROR;
            if (types.stream().anyMatch(this::isAggregate)) {
                return typeError("当前阶段不支持嵌套聚合类型；需要 Memory runtime", span);
            }
            return new TupleType(types);
        }
        return switch (name) {
            case "Int" -> ValueType.INT;
            case "Float" -> ValueType.FLOAT;
            case "Bool" -> ValueType.BOOL;
            case "String" -> ValueType.STRING;
            case "Void" -> ValueType.VOID;
            default -> typeError("当前阶段不支持类型：" + name, span);
        };
    }

    private ValueType typeError(String message, SourceSpan span) {
        error("MPL3103", message, span);
        return ValueType.ERROR;
    }

    private MplType collectionType(CollectionType.Kind kind, String elementName, SourceSpan span) {
        MplType element = parseType(elementName, span);
        return collectionType(kind, element, span);
    }

    private MplType collectionType(CollectionType.Kind kind, MplType element, SourceSpan span) {
        if (element == ValueType.ERROR || element == ValueType.VOID) return ValueType.ERROR;
        if (element == ValueType.NULL) {
            return typeError("当前阶段聚合类型不能存储可空值、游戏对象引用或对象查询", span);
        }
        if (element instanceof UnitType unit) {
            if (kind == CollectionType.Kind.SET && !unit.nullable()) return new CollectionType(kind, element);
            return typeError("Unit<T> 只能作为 Unit.getAll类型(...) 返回的 Set 元素类型", span);
        }
        if (element instanceof BuildingType building) {
            if (kind == CollectionType.Kind.SET && !building.nullable()) return new CollectionType(kind, element);
            return typeError("Building<T> 只能作为 Building.getAll类型(...) 返回的 Set 元素类型", span);
        }
        if (isAggregate(element)) return typeError("当前阶段不支持嵌套聚合类型；需要 Memory runtime", span);
        if (kind == CollectionType.Kind.MUTABLE_LIST && !supportsDynamicArrayElement(element)) {
            return typeError("MutableList 当前只支持 Int、Float 或 Bool 元素", span);
        }
        return new CollectionType(kind, element);
    }

    private List<String> splitTopLevel(String text) {
        List<String> result = new ArrayList<>();
        int nesting = 0;
        int start = 0;
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character == '<' || character == '(' || character == '[') nesting++;
            if (character == '>' || character == ')' || character == ']') nesting--;
            if (character == ',' && nesting == 0) {
                result.add(text.substring(start, index));
                start = index + 1;
            }
        }
        result.add(text.substring(start));
        return result;
    }

    private HirExpression analyzeArrayLiteral(ArrayLiteral array) {
        if (array.elements().isEmpty()) {
            error("MPL3601", "空数组字面量缺少可推导的元素类型", array.span());
            return new HirConstant("0", ValueType.ERROR);
        }
        List<HirExpression> elements = array.elements().stream().map(this::analyzeExpression).toList();
        MplType elementType = commonElementType(elements.stream().map(HirExpression::type).toList(), array.span());
        if (elementType == ValueType.ERROR) return new HirConstant("0", ValueType.ERROR);
        if (isAggregate(elementType)) {
            error("MPL3601", "当前阶段不支持嵌套聚合值；需要 Memory runtime", array.span());
            return new HirConstant("0", ValueType.ERROR);
        }
        return new HirArrayLiteral(elements, new CollectionType(CollectionType.Kind.ARRAY, elementType));
    }

    private HirExpression analyzeTupleLiteral(TupleLiteral tuple) {
        List<HirExpression> elements = tuple.elements().stream().map(this::analyzeExpression).toList();
        List<MplType> types = elements.stream().map(HirExpression::type).toList();
        if (types.contains(ValueType.ERROR) || types.contains(ValueType.VOID)) return new HirConstant("0", ValueType.ERROR);
        if (types.stream().anyMatch(this::isAggregate)) {
            error("MPL3601", "当前阶段不支持嵌套聚合值；需要 Memory runtime", tuple.span());
            return new HirConstant("0", ValueType.ERROR);
        }
        return new HirTupleLiteral(elements, new TupleType(types));
    }

    private MplType commonElementType(List<MplType> types, SourceSpan span) {
        MplType result = types.get(0);
        for (int index = 1; index < types.size(); index++) {
            MplType candidate = types.get(index);
            if (result.equals(candidate)) continue;
            if ((result == ValueType.INT && candidate == ValueType.FLOAT)
                || (result == ValueType.FLOAT && candidate == ValueType.INT)) {
                result = ValueType.FLOAT;
                continue;
            }
            return typeError("数组元素必须具有可统一推导的类型，不能混用 " + display(result) + " 与 "
                + display(candidate), span);
        }
        return result;
    }

    private Optional<CollectionType.Kind> collectionFactory(Expression callee) {
        if (callee instanceof MemberAccessExpression member && member.target() instanceof Identifier namespace
            && "of".equals(member.member())) {
            return switch (namespace.name()) {
                case "List" -> Optional.of(CollectionType.Kind.LIST);
                case "Set" -> Optional.of(CollectionType.Kind.SET);
                case "MutableList" -> Optional.of(CollectionType.Kind.MUTABLE_LIST);
                default -> Optional.empty();
            };
        }
        return Optional.empty();
    }

    private HirExpression analyzeCollectionFactory(CollectionType.Kind kind, List<Expression> sourceElements, SourceSpan span) {
        if (sourceElements.isEmpty()) {
            error("MPL3601", kind + " 空字面量缺少可推导的元素类型", span);
            return new HirConstant("0", ValueType.ERROR);
        }
        List<HirExpression> elements = sourceElements.stream().map(this::analyzeExpression).toList();
        MplType elementType = commonElementType(elements.stream().map(HirExpression::type).toList(), span);
        if (elementType == ValueType.ERROR) return new HirConstant("0", ValueType.ERROR);
        if (kind == CollectionType.Kind.MUTABLE_LIST && !supportsDynamicArrayElement(elementType)) {
            error("MPL3601", "MutableList 当前只支持 Int、Float 或 Bool 元素", span);
            return new HirConstant("0", ValueType.ERROR);
        }
        if (kind == CollectionType.Kind.SET && !hasStaticallyDistinctElements(elements)) {
            error("MPL3601", "第一版 Set 元素必须是互不相同的静态字面量", span);
            return new HirConstant("0", ValueType.ERROR);
        }
        CollectionType type = new CollectionType(kind, elementType);
        if (kind == CollectionType.Kind.MUTABLE_LIST) {
            return new HirMutableListLiteral(elements, elements.size(), type);
        }
        return new HirCollectionLiteral(elements, type);
    }

    private boolean mutableListCapacityFactory(Expression callee) {
        return callee instanceof MemberAccessExpression member
            && member.target() instanceof Identifier namespace
            && "MutableList".equals(namespace.name()) && "withCapacity".equals(member.member());
    }

    private boolean hasStaticallyDistinctElements(List<HirExpression> elements) {
        Set<String> values = new HashSet<>();
        for (HirExpression element : elements) {
            String key;
            if (element instanceof HirConstant constant) key = constant.type().displayName() + ":" + constant.mlogLiteral();
            else if (element instanceof HirText text) key = "String:" + text.value();
            else return false;
            if (!values.add(key)) return false;
        }
        return true;
    }

    private HirExpression analyzeCollectionContains(Expression sourceTarget, Expression sourceCandidate, SourceSpan span) {
        HirExpression target = analyzeExpression(sourceTarget);
        HirExpression candidate = analyzeExpression(sourceCandidate);
        if (!(target.type() instanceof CollectionType collection)) {
            error("MPL3601", "contains(value) 仅支持 Array、List 或 Set", span);
            return new HirConstant("0", ValueType.ERROR);
        }
        if (!canAssign(collection.elementType(), candidate.type())) {
            error("MPL3103", "contains 参数必须是 " + collection.elementType().displayName(), sourceCandidate.span());
            return new HirConstant("0", ValueType.ERROR);
        }
        Integer size = staticAggregateSize(target);
        if (size == null) {
            error("MPL3601", "contains 需要可静态确定长度的聚合值", span);
            return new HirConstant("0", ValueType.ERROR);
        }
        return new HirCollectionContains(target, candidate, size);
    }

    private HirExpression analyzeIndexAccess(IndexExpression access) {
        return analyzeIndexAccess(access.target(), access.index(), access.span());
    }

    private HirExpression analyzeIndexAccess(Expression sourceTarget, Expression sourceIndex, SourceSpan span) {
        HirExpression target = analyzeExpression(sourceTarget);
        HirExpression index = analyzeExpression(sourceIndex);
        if (index.type() != ValueType.INT) {
            error("MPL3601", "聚合下标必须是 Int", sourceIndex.span());
            return new HirConstant("0", ValueType.ERROR);
        }
        if (!(target.type() instanceof TupleType) && !(target.type() instanceof CollectionType)) {
            error("MPL3601", "下标访问仅支持元组、数组、List 或 Set", sourceTarget.span());
            return new HirConstant("0", ValueType.ERROR);
        }
        if (sourceIndex instanceof IntegerLiteral) {
            Integer size = staticAggregateSize(target);
            Optional<Integer> staticIndex = staticAggregateIndex(sourceIndex, size);
            if (staticIndex.isEmpty()) return new HirConstant("0", ValueType.ERROR);
            MplType elementType = target.type() instanceof TupleType tuple
                ? tuple.elementTypes().get(staticIndex.orElseThrow())
                : ((CollectionType) target.type()).elementType();
            return new HirIndexAccess(target, index, elementType);
        }
        if (!(target.type() instanceof CollectionType collection)
            || (collection.kind() != CollectionType.Kind.ARRAY && collection.kind() != CollectionType.Kind.MUTABLE_LIST)
            || !(sourceTarget instanceof Identifier array)
            || !(target instanceof HirVariable)) {
            error("MPL3601", "动态下标当前只支持具名 Array 或 MutableList", span);
            return new HirConstant("0", ValueType.ERROR);
        }
        MplType elementType = collection.elementType();
        if (!supportsDynamicArrayElement(elementType)) {
            error("MPL3601", "动态容器下标当前只支持 Int、Float 或 Bool 元素", span);
            return new HirConstant("0", ValueType.ERROR);
        }
        if (!hasArrayBoundsProof(array.name(), sourceIndex)) {
            error("MPL3601", boundsProofMessage(collection), sourceIndex.span());
            return new HirConstant("0", ValueType.ERROR);
        }
        return new HirDynamicIndexAccess(target, index, elementType);
    }

    private boolean supportsDynamicArrayElement(MplType type) {
        return type == ValueType.INT || type == ValueType.FLOAT || type == ValueType.BOOL;
    }

    private String boundsProofMessage(CollectionType collection) {
        return collection.kind() == CollectionType.Kind.ARRAY
            ? "无法证明动态 Array 下标在范围内；请使用从 0 到 array.size 的标准计数 for 循环"
            : "无法证明动态 MutableList 下标在范围内；请使用从 0 到 list.size 的标准计数 for 循环";
    }

    private boolean hasArrayBoundsProof(String array, Expression sourceIndex) {
        if (!(sourceIndex instanceof Identifier index)) return false;
        return arrayBoundsProofs.stream().anyMatch(proof -> proof.array().equals(array) && proof.index().equals(index.name()));
    }

    private boolean isAggregate(MplType type) {
        return type instanceof TupleType || type instanceof CollectionType;
    }

    private boolean canAssign(MplType target, MplType source) {
        return typeRelations.canAssign(target, source);
    }

    private boolean isUnitSet(MplType type) {
        return type instanceof CollectionType collection
            && collection.kind() == CollectionType.Kind.SET
            && collection.elementType() instanceof UnitType unit
            && !unit.nullable();
    }

    private boolean isBuildingSet(MplType type) {
        return type instanceof CollectionType collection
            && collection.kind() == CollectionType.Kind.SET
            && collection.elementType() instanceof BuildingType building
            && !building.nullable();
    }

    private MplType aggregateIterationElementType(MplType type, SourceSpan span) {
        if (type instanceof CollectionType collection) return collection.elementType();
        if (type instanceof TupleType tuple) {
            MplType first = tuple.elementTypes().get(0);
            if (tuple.elementTypes().stream().allMatch(first::equals)) return first;
            return typeError("只有元素类型一致的元组可以直接 for 遍历", span);
        }
        return typeError("该类型不可遍历：" + display(type), span);
    }

    private Optional<Integer> staticAggregateIndex(Expression source, Integer size) {
        if (!(source instanceof IntegerLiteral literal)) {
            error("MPL3601", "当前阶段只支持可在编译期确定的聚合下标；动态下标需要 Memory runtime", source.span());
            return Optional.empty();
        }
        if (literal.value() < 0 || literal.value() > Integer.MAX_VALUE) {
            error("MPL3601", "聚合下标超出 Int 范围", source.span());
            return Optional.empty();
        }
        int index = (int) literal.value();
        if (size == null || index >= size) {
            error("MPL3601", "聚合下标越界：" + index, source.span());
            return Optional.empty();
        }
        return Optional.of(index);
    }

    private boolean declare(String name, Symbol symbol, SourceSpan span) {
        Map<String, Symbol> current = scopes.peek();
        if (hardwareLinks.containsKey(name) || functionOverloads.containsKey(name) || classes.containsKey(name)
            || current.containsKey(name) || lookup(name) != null) {
            error("MPL3101", "变量已声明：" + name, span);
            return false;
        }
        current.put(name, symbol);
        return true;
    }

    private void updateSymbol(String name, java.util.function.UnaryOperator<Symbol> change) {
        for (Map<String, Symbol> scope : scopes) {
            Symbol current = scope.get(name);
            if (current != null) {
                scope.put(name, change.apply(current));
                return;
            }
        }
        throw new IllegalStateException("unknown variable: " + name);
    }

    private void recordGlobalAccess(String name, Symbol symbol, SourceSpan accessSpan) {
        if (currentFunction == null || !symbol.global()) return;
        directGlobalDependencies.get(currentFunction).add(name);
        SourceSpan functionSpan = functions.get(currentFunction).declaration().span();
        if (!startsBefore(symbol.declarationSpan(), functionSpan)) {
            error("MPL3506", "函数 " + currentFunction + " 不能访问在其声明后定义的顶层变量：" + name,
                accessSpan);
        }
    }

    private boolean startsBefore(SourceSpan left, SourceSpan right) {
        return left.startLine() < right.startLine()
            || left.startLine() == right.startLine() && left.startColumn() < right.startColumn();
    }

    private void validateTopLevelCalls() {
        Map<String, Set<String>> dependencies = new HashMap<>();
        for (String function : functions.keySet()) {
            dependencies.put(function, new HashSet<>(directGlobalDependencies.getOrDefault(function, Set.of())));
        }

        boolean changed;
        do {
            changed = false;
            for (String function : functions.keySet()) {
                Set<String> reachable = dependencies.get(function);
                for (String callee : callGraph.getOrDefault(function, Set.of())) {
                    changed |= reachable.addAll(dependencies.getOrDefault(callee, Set.of()));
                }
            }
        } while (changed);

        for (TopLevelCall call : topLevelCalls) {
            List<String> missing = dependencies.getOrDefault(call.function(), Set.of()).stream()
                .filter(name -> !call.initializedGlobals().contains(name))
                .sorted()
                .toList();
            if (!missing.isEmpty()) {
                error("MPL3507", "调用函数 " + call.function() + " 前尚未初始化顶层变量："
                    + String.join("、", missing), call.span());
            }
        }
    }

    private Symbol lookup(String name) {
        for (Map<String, Symbol> scope : scopes) {
            Symbol symbol = scope.get(name);
            if (symbol != null) return symbol;
        }
        return null;
    }

    private void updateStringMax(String name, int maxCodeUnits) {
        for (Map<String, Symbol> scope : scopes) {
            Symbol symbol = scope.get(name);
            if (symbol != null) {
                scope.put(name, symbol.withStringMax(maxCodeUnits));
                return;
            }
        }
    }

    private boolean referencesVariable(HirExpression expression, String name) {
        if (expression instanceof HirVariable variable) return variable.name().equals(name);
        if (expression instanceof HirStringConcat concat) {
            return referencesVariable(concat.left(), name) || referencesVariable(concat.right(), name);
        }
        if (expression instanceof HirStringLength length) return referencesVariable(length.value(), name);
        if (expression instanceof HirStringSnapshot snapshot) return referencesVariable(snapshot.value(), name);
        if (expression instanceof HirStringComparison comparison) {
            return referencesVariable(comparison.left(), name) || referencesVariable(comparison.right(), name);
        }
        if (expression instanceof HirFunctionCall call) {
            return call.arguments().stream().anyMatch(argument -> referencesVariable(argument, name));
        }
        if (expression instanceof HirObjectFieldRead read) return referencesVariable(read.target(), name);
        if (expression instanceof HirIndexAccess access) return referencesVariable(access.target(), name);
        return false;
    }

    private void error(String code, String message, SourceSpan span) {
        diagnostics.add(new Diagnostic(Severity.ERROR, code, message, Optional.ofNullable(file), Optional.of(span)));
    }

    private String display(MplType type) {
        return type.displayName();
    }

    /** Conservative UTF-16 upper bound carried by every String expression. */
    private int stringMaxLength(HirExpression expression) {
        if (expression instanceof HirText text) return text.value().length();
        if (expression instanceof HirStringConcat concat) return concat.maxCodeUnits();
        if (expression instanceof HirStringSnapshot snapshot) return snapshot.maxCodeUnits();
        if (expression instanceof HirBinary binary && binary.type() == ValueType.STRING && "+".equals(binary.operator())) {
            return Math.addExact(stringMaxLength(binary.left()), stringMaxLength(binary.right()));
        }
        if (expression instanceof HirVariable variable && variable.type() == ValueType.STRING) {
            Symbol symbol = lookup(variable.name());
            return symbol == null || symbol.staticStringCodeUnits() == null
                ? profile.maxMessageUtf16CodeUnits() : symbol.staticStringCodeUnits();
        }
        return expression.type() == ValueType.STRING ? profile.maxMessageUtf16CodeUnits() : 0;
    }

    private boolean hasUnpairedSurrogate(String value) {
        for (int index = 0; index < value.length(); index++) {
            char unit = value.charAt(index);
            if (Character.isHighSurrogate(unit)) {
                if (index + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(index + 1))) return true;
                index++;
            } else if (Character.isLowSurrogate(unit)) {
                return true;
            }
        }
        return false;
    }

    private Integer staticAggregateSize(HirExpression expression) {
        if (expression.type() instanceof TupleType tuple) return tuple.elementTypes().size();
        if (expression instanceof HirFunctionCall call && call.aggregateSize() > 0) return call.aggregateSize();
        if (expression instanceof HirArrayLiteral array) return array.elements().size();
        if (expression instanceof HirTupleLiteral tuple) return tuple.elements().size();
        if (expression instanceof HirCollectionLiteral collection) return collection.elements().size();
        if (expression instanceof HirMutableListLiteral list) return list.elements().size();
        if (expression instanceof HirVariable variable) {
            Symbol symbol = lookup(variable.name());
            return symbol == null ? null : symbol.staticAggregateSize();
        }
        return null;
    }

    private Integer aggregateCapacity(HirExpression expression) {
        if (expression instanceof HirMutableListLiteral list) return list.capacity();
        return staticAggregateSize(expression);
    }

    private boolean unsupportedTopLevelFunctionAbi(MplType type) {
        if (type == ValueType.ERROR) return false;
        if (type instanceof UnitType) return true;
        if (type instanceof CollectionType) return !isFunctionArray(type);
        return type instanceof TupleType && !supportedTupleFunctionAbi(type);
    }

    private boolean supportedTupleFunctionAbi(MplType type) {
        return type instanceof TupleType tuple && tuple.elementTypes().stream().allMatch(element ->
            element == ValueType.INT || element == ValueType.FLOAT || element == ValueType.BOOL);
    }

    private boolean isFunctionArray(MplType type) {
        return type instanceof CollectionType collection
            && collection.kind() == CollectionType.Kind.ARRAY
            && (collection.elementType() == ValueType.INT
                || collection.elementType() == ValueType.FLOAT
                || collection.elementType() == ValueType.BOOL);
    }

    private record FunctionParameterShapeKey(String function, int index) { }

    private record ShapeResult(int size, boolean changed) {
        private ShapeResult {
            if (size < 0) throw new IllegalArgumentException("聚合形状不能为负数");
        }

        private ShapeResult withoutSize() { return new ShapeResult(0, changed); }
    }

    private static final class ShapeEnvironment {
        private final Map<String, MplType> types;
        private final Map<String, Integer> aggregateSizes;

        private ShapeEnvironment() {
            this.types = new LinkedHashMap<>();
            this.aggregateSizes = new LinkedHashMap<>();
        }

        private ShapeEnvironment(Map<String, MplType> types, Map<String, Integer> aggregateSizes) {
            this.types = new LinkedHashMap<>(types);
            this.aggregateSizes = new LinkedHashMap<>(aggregateSizes);
        }

        private ShapeEnvironment copy() { return new ShapeEnvironment(types, aggregateSizes); }
    }

    private record Symbol(MplType type, boolean mutable, Integer staticStringCodeUnits, Integer staticAggregateSize,
                          Integer aggregateCapacity, HirUnitQuery unitQuery, HirBuildingQuery buildingQuery, boolean reusableLocalObject,
                          boolean ownsPooledObject,
                          boolean global,
                          SourceSpan declarationSpan) {
        private Symbol(MplType type, boolean mutable, Integer staticStringCodeUnits, Integer staticAggregateSize,
                       HirUnitQuery unitQuery, HirBuildingQuery buildingQuery, boolean reusableLocalObject,
                       boolean global, SourceSpan declarationSpan) {
            this(type, mutable, staticStringCodeUnits, staticAggregateSize, staticAggregateSize, unitQuery, buildingQuery,
                reusableLocalObject, false, global, declarationSpan);
        }

        private Symbol(MplType type, boolean mutable, Integer staticStringCodeUnits, Integer staticAggregateSize,
                       HirUnitQuery unitQuery, HirBuildingQuery buildingQuery, boolean reusableLocalObject,
                       boolean ownsPooledObject, boolean global, SourceSpan declarationSpan) {
            this(type, mutable, staticStringCodeUnits, staticAggregateSize, staticAggregateSize, unitQuery, buildingQuery,
                reusableLocalObject, ownsPooledObject, global, declarationSpan);
        }

        private Symbol withType(MplType narrowedType) {
            return new Symbol(narrowedType, mutable, staticStringCodeUnits, staticAggregateSize, aggregateCapacity, unitQuery, buildingQuery,
                reusableLocalObject, ownsPooledObject, global, declarationSpan);
        }


        private Symbol withStringMax(int maxCodeUnits) {
            return new Symbol(type, mutable, maxCodeUnits, staticAggregateSize, aggregateCapacity, unitQuery, buildingQuery,
                reusableLocalObject, ownsPooledObject, global, declarationSpan);
        }

        private Symbol withAggregateSize(Integer size) {
            return new Symbol(type, mutable, staticStringCodeUnits, size, aggregateCapacity, unitQuery, buildingQuery,
                reusableLocalObject, ownsPooledObject, global, declarationSpan);
        }
    }

    private enum ObjectAllocationContext {
        DISALLOWED,
        STATIC,
        REUSABLE_LOCAL,
        POOLED_RETURN
    }

    private record FunctionSignature(String sourceName, String internalName, FunctionDeclaration declaration,
                                     List<MplType> parameterTypes, MplType returnType,
                                     boolean returnsOwnedObject) {
        private FunctionSignature {
            java.util.Objects.requireNonNull(sourceName, "sourceName");
            java.util.Objects.requireNonNull(internalName, "internalName");
            parameterTypes = List.copyOf(parameterTypes);
        }
    }

    private record PooledOwner(String variable, String className, int loopDepth, boolean global) {
    }

    private record TopLevelCall(String function, Set<String> initializedGlobals, SourceSpan span) {
        private TopLevelCall {
            initializedGlobals = Set.copyOf(initializedGlobals);
        }
    }

    private record ArrayBoundsProof(String index, String array) {
    }

    private record UnitQuery(String typeName, TargetProfile.UnitType type, List<Expression> filters, int managedLimit,
                             int managedId) {
    }

    private record BuildingQuery(String typeName, List<Expression> filters) {
        private BuildingQuery {
            filters = List.copyOf(filters);
        }
    }

    private record FieldInfo(String ownerClass, String name, MplType type, boolean publicAccess, SourceSpan span) {
    }

    private record MethodKey(String sourceName, List<MplType> parameterTypes) {
        private MethodKey {
            parameterTypes = List.copyOf(parameterTypes);
        }
    }

    private record MethodInfo(String ownerClass, String sourceName, String internalName,
                              FunctionDeclaration declaration,
                              List<MplType> parameterTypes, MplType returnType, boolean publicAccess,
                              boolean constructor, boolean receiverEscapes) {
        private MethodInfo {
            parameterTypes = List.copyOf(parameterTypes);
        }

        private MethodKey key() {
            return new MethodKey(sourceName, parameterTypes);
        }
    }

    private static final class ClassInfo {
        private final String name;
        private final ClassDeclaration declaration;
        private final Map<String, FieldInfo> fields = new LinkedHashMap<>();
        private final Map<MethodKey, MethodInfo> methods = new LinkedHashMap<>();
        private final Map<List<MplType>, MethodInfo> constructors = new LinkedHashMap<>();
        private ClassInfo parent;

        private ClassInfo(String name, ClassDeclaration declaration) {
            this.name = name;
            this.declaration = declaration;
        }

        private String name() {
            return name;
        }

        private ClassDeclaration declaration() {
            return declaration;
        }

        private Map<String, FieldInfo> fields() {
            return fields;
        }

        private Map<MethodKey, MethodInfo> methods() {
            return methods;
        }

        private Map<List<MplType>, MethodInfo> constructors() {
            return constructors;
        }

        private ClassInfo parent() {
            return parent;
        }

        private void parent(ClassInfo value) {
            parent = value;
        }

        private Map<String, FieldInfo> effectiveFields() {
            Map<String, FieldInfo> result = parent == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(parent.effectiveFields());
            fields.forEach(result::put);
            return result;
        }

        private HirClass toHir(boolean exported) {
            List<HirClass.Field> hirFields = effectiveFields().values().stream()
                .map(field -> new HirClass.Field(field.name(), field.type(), field.publicAccess())).toList();
            List<HirClass.Method> hirMethods = java.util.stream.Stream.concat(methods.values().stream(),
                    constructors.values().stream())
                .map(method -> new HirClass.Method(method.sourceName(), method.internalName(), method.publicAccess(),
                    method.constructor())).toList();
            return new HirClass(name, Optional.ofNullable(parent).map(ClassInfo::name), exported, hirFields, hirMethods);
        }
    }
}
