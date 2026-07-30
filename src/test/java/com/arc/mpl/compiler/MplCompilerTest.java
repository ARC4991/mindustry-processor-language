package com.arc.mpl.compiler;

import com.arc.mpl.diagnostic.Severity;
import com.arc.mpl.diagnostic.DiagnosticLanguage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MplCompilerTest {
    private final MplCompiler compiler = new MplCompiler();

    @Test
    void reportsAnUnknownTargetBeforeReadingTheProject() {
        CompilationResult result = compiler.compile(new CompilationRequest(Path.of("demo"), "v999"));

        assertFalse(result.succeeded());
        assertTrue(result.profile().isEmpty());
        assertEquals("MPL1001", result.diagnostics().get(0).code());
        assertEquals(Severity.ERROR, result.diagnostics().get(0).severity());
        assertEquals("compiler.target.unsupported", result.diagnostics().get(0).messageKey().orElseThrow());
        assertEquals("不支持的 Mindustry target profile：v999",
            result.diagnostics().get(0).render(DiagnosticLanguage.ZH_CN));
    }

    @Test
    void compilesTheImplementedSubsetForAKnownTarget(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), "var total: Int = 1 + 2;\ntotal += 3;");

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146"));

        assertTrue(result.succeeded());
        assertEquals("v146", result.profile().orElseThrow().id());
        assertTrue(result.diagnostics().isEmpty());
        assertEquals("set mpl_total 3\nop add __mpl_tmp0 mpl_total 3\nop min __mpl_tmp0 __mpl_tmp0 2147483647\nop max __mpl_tmp0 __mpl_tmp0 -2147483648\nset mpl_total __mpl_tmp0\nstop\n",
            result.mlog().orElseThrow());
        assertEquals("""
            // 由 MPL 自动生成的 MIL；请通过 mpl build 重新生成，勿直接编辑。
            // 普通结构保留为 MIL；@unit.* 与 @io.* 是由 target profile 展开的受限宏。
            var total: Int = 3;
            total += 3;
            """, result.mil().orElseThrow());
    }

    @Test
    void infersTopLevelFunctionReturnsAndSerializesThemIntoMil(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), """
            fun increment(value: Int) { return value + 1; }
            fun displayValue(value: Int) { val copied = value; }
            val result = increment(41);
            displayValue(result);
            """);

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146", true));

        assertTrue(result.succeeded(), () -> result.diagnostics().toString());
        String mil = result.mil().orElseThrow();
        assertTrue(mil.contains("fun increment(value: Int): Int"), mil);
        assertTrue(mil.contains("fun displayValue(value: Int) {"), mil);
        java.nio.file.Files.writeString(project.resolve("mpl.json"), "{ \"entry\": \"src/generated.mil\" }");
        java.nio.file.Files.writeString(sourceDirectory.resolve("generated.mil"), mil);
        CompilationResult regenerated = compiler.compile(new CompilationRequest(project, "v146", true));
        assertTrue(regenerated.succeeded(), () -> regenerated.diagnostics().toString());
    }

    @Test
    void infersInstanceMethodReturnsAndSerializesThemIntoMil(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), """
            class Counter {
                public value: Int;
                public fun Counter(value: Int) { this.value = value; }
                public fun add(amount: Int) { return this.value + amount; }
                public fun halfAfterAdd(amount: Int) { return this.add(amount) / 2.0; }
            }
            val counter = new Counter(2);
            val result = counter.halfAfterAdd(4);
            """);

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146", true));

        assertTrue(result.succeeded(), () -> result.diagnostics().toString());
        String mil = result.mil().orElseThrow();
        assertTrue(mil.contains("fun add(amount: Int): Int"), mil);
        assertTrue(mil.contains("fun halfAfterAdd(amount: Int): Float"), mil);
        java.nio.file.Files.writeString(project.resolve("mpl.json"), "{ \"entry\": \"src/generated.mil\" }");
        java.nio.file.Files.writeString(sourceDirectory.resolve("generated.mil"), mil);
        CompilationResult regenerated = compiler.compile(new CompilationRequest(project, "v146", true));
        assertTrue(regenerated.succeeded(), () -> regenerated.diagnostics().toString());
    }

    @Test
    void exposesWorkerHelperEffectAnalysisAtTheCompilerBoundary(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), """
            fun add(left: Int, right: Int): Int {
                return left + right;
            }
            val result = add(2, 3);
            """);

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146"));

        assertTrue(result.succeeded(), () -> result.diagnostics().toString());
        assertTrue(result.effectAnalysis().function("add").pureNumeric());
        assertEquals(List.of("add"), result.effectAnalysis().pureNumericFunctions().stream()
            .map(com.arc.mpl.optimization.HirEffectAnalyzer.FunctionEffect::function).toList());
    }

    @Test
    void compilesPureFunctionsIntoADeployableHelperTopology(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(project.resolve("mpl.json"), """
            {
              "runtime": {
                "goal": "maxPerformance",
                "processors": { "micro": 2 },
                "memory": { "bank": 1 }
              }
            }
            """);
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), """
            fun add(left: Int, right: Int): Int {
                return left + right;
            }
            val result = add(11, 13);
            """);

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146", true));

        assertTrue(result.succeeded(), () -> result.diagnostics().toString());
        MultiShardCompilation multi = result.multiShard().orElseThrow();
        assertEquals(List.of("Main", "Worker-0"), multi.shards().stream()
            .map(MultiShardCompilation.Shard::id).toList());
        assertEquals(2, multi.topology().shards().size());
        assertEquals(16, multi.topology().physicalSlots());
        assertTrue(multi.shards().get(0).mlog().contains("mpl_mailbox_send_wait"));
        assertFalse(multi.shards().get(0).mlog().contains("mpl_function_add"));
        assertTrue(multi.shards().get(1).mlog().contains("mpl_function_add"));
        assertTrue(multi.shards().get(1).mil().contains("fun add(left: Int, right: Int): Int"));
        assertEquals(multi.shards().get(0).mlog(), result.mlog().orElseThrow());
        assertEquals(multi.topology().physicalMemoryLayout(), result.physicalMemoryLayout());
    }

    @Test
    void compilesIndependentHelpersIntoMultipleWorkerShards(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(project.resolve("mpl.json"), """
            {
              "runtime": {
                "goal": "maxPerformance",
                "processors": { "micro": 4 },
                "memory": { "bank": 1 }
              }
            }
            """);
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), """
            fun add(left: Int, right: Int): Int { return left + right; }
            fun subtract(left: Int, right: Int): Int { return left - right; }
            fun multiply(left: Int, right: Int): Int { return left * right; }
            val sum = add(11, 13);
            val difference = subtract(11, 3);
            val product = multiply(6, 7);
            """);

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146", true));

        assertTrue(result.succeeded(), () -> result.diagnostics().toString());
        MultiShardCompilation multi = result.multiShard().orElseThrow();
        assertEquals(List.of("Main", "Worker-0", "Worker-1", "Worker-2"), multi.shards().stream()
            .map(MultiShardCompilation.Shard::id).toList());
        assertEquals(38, multi.topology().physicalSlots());
        assertEquals(List.of(List.of("add"), List.of("subtract"), List.of("multiply")),
            multi.helperPlan().workers().stream().map(com.arc.mpl.project.RuntimeHelperPlan.Worker::functions).toList());
        assertTrue(multi.shards().get(1).mlog().contains("mpl_function_add"));
        assertFalse(multi.shards().get(1).mlog().contains("mpl_function_subtract"));
        assertTrue(multi.shards().get(2).mlog().contains("mpl_function_subtract"));
        assertTrue(multi.shards().get(3).mlog().contains("mpl_function_multiply"));
        assertEquals(6, multi.topology().sharedRuntime().orElseThrow().mailboxes().size());
    }

    @Test
    void minResourcesUsesHelperShardsOnlyWhenTheSingleProcessorProgramExceedsTheLimit(
        @TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        StringBuilder source = new StringBuilder();
        for (int function = 0; function < 4; function++) {
            source.append("fun helper").append(function).append("(value: Int): Int {\n")
                .append("    var result = value;\n");
            for (int statement = 0; statement < 90; statement++) source.append("    result += 1;\n");
            source.append("    return result;\n}\n")
                .append("val result").append(function).append(" = helper").append(function).append("(")
                .append(function).append(");\n");
        }
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), source);
        java.nio.file.Files.writeString(project.resolve("mpl.json"), """
            {
              "runtime": {
                "goal": "minResources",
                "processors": { "micro": 1 },
                "memory": { "bank": 1 }
              }
            }
            """);

        CompilationResult singleProcessor = compiler.compile(new CompilationRequest(project, "v146"));

        assertFalse(singleProcessor.succeeded());
        assertTrue(singleProcessor.diagnostics().stream().anyMatch(diagnostic -> diagnostic.message().contains("1000")));

        java.nio.file.Files.writeString(project.resolve("mpl.json"), """
            {
              "runtime": {
                "goal": "minResources",
                "processors": { "micro": 5 },
                "memory": { "bank": 1 }
              }
            }
            """);
        CompilationResult sharded = compiler.compile(new CompilationRequest(project, "v146"));

        assertTrue(sharded.succeeded(), () -> sharded.diagnostics().toString());
        assertEquals(List.of("Main", "Worker-0", "Worker-1", "Worker-2", "Worker-3"),
            sharded.multiShard().orElseThrow().shards().stream().map(MultiShardCompilation.Shard::id).toList());
        assertTrue(sharded.multiShard().orElseThrow().shards().stream()
            .allMatch(shard -> com.arc.mpl.codegen.MlogProgramMetrics.analyze(shard.mlog()).instructions() <= 1_000));
    }

    @Test
    void keepsNestedHelperCallsLocalToOneWorker(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(project.resolve("mpl.json"), """
            {
              "runtime": {
                "goal": "maxPerformance",
                "processors": { "micro": 3 },
                "memory": { "bank": 1 }
              }
            }
            """);
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), """
            fun increment(value: Int): Int { return value + 1; }
            fun twiceIncrement(value: Int): Int { return increment(increment(value)); }
            fun subtract(left: Int, right: Int): Int { return left - right; }
            val increased = twiceIncrement(5);
            val difference = subtract(9, 4);
            """);

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146", true));

        assertTrue(result.succeeded(), () -> result.diagnostics().toString());
        MultiShardCompilation multi = result.multiShard().orElseThrow();
        assertEquals(2, multi.helperPlan().workers().size());
        String owner = multi.helperPlan().task("increment").orElseThrow().worker();
        assertEquals(owner, multi.helperPlan().task("twiceIncrement").orElseThrow().worker());
        MultiShardCompilation.Shard worker = multi.shards().stream().filter(shard -> shard.id().equals(owner))
            .findFirst().orElseThrow();
        assertTrue(worker.mlog().contains("mpl_function_increment"));
        assertTrue(worker.mlog().contains("mpl_function_twiceIncrement"));
        assertTrue(worker.mlog().split("op add __mpl_fn0_return @counter 1", -1).length - 1 >= 2,
            worker.mlog());
    }

    @Test
    void passesExactTargetFunctionCostsIntoShardBalancing(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(project.resolve("mpl.json"), """
            {
              "runtime": {
                "goal": "maxPerformance",
                "processors": { "micro": 3 },
                "memory": { "bank": 1 }
              }
            }
            """);
        StringBuilder source = new StringBuilder("fun heavy(value: Int): Int {\n    var result = value;\n");
        for (int index = 0; index < 20; index++) source.append("    result += 1;\n");
        source.append("    return result;\n}\n")
            .append("fun lightA(value: Int): Int { return value + 1; }\n")
            .append("fun lightB(value: Int): Int { return value - 1; }\n")
            .append("val heavyResult = heavy(1);\n")
            .append("val first = lightA(2);\n")
            .append("val second = lightB(3);\n");
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), source);

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146"));

        assertTrue(result.succeeded(), () -> result.diagnostics().toString());
        MultiShardCompilation multi = result.multiShard().orElseThrow();
        assertEquals(List.of("heavy"), multi.helperPlan().workers().get(0).functions());
        assertEquals(List.of("lightA", "lightB"), multi.helperPlan().workers().get(1).functions());
        assertTrue(multi.helperPlan().workerFunctionCost("Worker-0").instructions()
            > multi.helperPlan().workerFunctionCost("Worker-1").instructions());
    }

    @Test
    void reportsAnOversizedIndivisibleHelperComponent(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(project.resolve("mpl.json"), """
            {
              "runtime": {
                "goal": "maxPerformance",
                "processors": { "micro": 2 },
                "memory": { "bank": 1 }
              }
            }
            """);
        StringBuilder source = new StringBuilder("fun oversized(value: Int): Int {\n    var result = value;\n");
        for (int index = 0; index < 400; index++) source.append("    result += 1;\n");
        source.append("    return result;\n}\nval output = oversized(1);\n");
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), source);

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146"));

        assertFalse(result.succeeded());
        com.arc.mpl.diagnostic.Diagnostic diagnostic = result.diagnostics().stream()
            .filter(value -> value.code().equals("MPL1302")).findFirst().orElseThrow();
        assertTrue(diagnostic.message().contains("Worker-0"), diagnostic.message());
        assertTrue(diagnostic.message().contains("[oversized]"), diagnostic.message());
        assertTrue(diagnostic.message().contains("ABI v2"), diagnostic.message());
    }

    @Test
    void compilesDistinctStaticObjectsAndRegeneratesParseableMil(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), """
            class Counter {
                private value: Int;
                public fun Counter(initial: Int) { this.value = initial; }
                public fun add(amount: Int): Int {
                    this.value += amount;
                    return this.value;
                }
            }

            val first = new Counter(1);
            val second = new Counter(10);
            val firstValue = first.add(2);
            val secondValue = second.add(5);
            val same = first === first;
            val different = first !== second;
            """);

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146", true));

        assertTrue(result.succeeded(), () -> result.diagnostics().toString());
        String mlog = result.mlog().orElseThrow();
        assertTrue(mlog.contains("__mpl_obj1_value"), mlog);
        assertTrue(mlog.contains("__mpl_obj2_value"), mlog);
        assertTrue(mlog.contains("set mpl_first 1"), mlog);
        assertTrue(mlog.contains("set mpl_second 2"), mlog);
        assertTrue(mlog.contains("strictEqual"), mlog);
        String mil = result.mil().orElseThrow();
        assertTrue(mil.contains("class Counter {"), mil);
        assertTrue(mil.contains("val first: Counter = new Counter(1);"), mil);
        assertTrue(mil.contains("first.add(2)"), mil);

        java.nio.file.Files.writeString(project.resolve("mpl.json"), "{ \"entry\": \"src/generated.mil\" }");
        java.nio.file.Files.writeString(sourceDirectory.resolve("generated.mil"), mil);
        CompilationResult regenerated = compiler.compile(new CompilationRequest(project, "v146", true));
        assertTrue(regenerated.succeeded(), () -> regenerated.diagnostics().toString());
    }

    @Test
    void compilesInheritanceOverloadsAndVirtualMethodDispatch(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), """
            class Animal {
                public value: Int;
                public fun Animal(value: Int) { this.value = value; }
                public fun score(amount: Int): Int { return this.value + amount; }
            }
            class Dog extends Animal {
                public bonus: Int;
                public fun Dog(value: Int, bonus: Int) { super(value); this.bonus = bonus; }
                public fun score(amount: Int): Int { return super.score(amount) + this.bonus; }
                public fun score(amount: Float): Int { return this.bonus; }
            }
            fun classify(value: Animal): Int { return 1; }
            fun classify(value: Dog): Int { return 2; }
            fun read(subject: Animal): Int { return subject.score(2); }
            val animal: Animal = new Dog(3, 4);
            val overridden = read(animal);
            val overloaded = classify(new Dog(1, 2));
            val decimal = new Dog(1, 2).score(1.5);
            """);

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146", true));

        assertTrue(result.succeeded(), () -> result.diagnostics().toString());
        String mil = result.mil().orElseThrow();
        assertTrue(mil.contains("class Dog extends Animal {"), mil);
        assertTrue(mil.contains("super(value);"), mil);
        assertTrue(mil.contains("super.score(amount)"), mil);
        assertTrue(mil.contains("fun classify(value: Animal): Int"), mil);
        assertTrue(mil.contains("fun classify(value: Dog): Int"), mil);
        String mlog = result.mlog().orElseThrow();
        assertTrue(mlog.contains("virtual_method_"), mlog);
        assertTrue(mlog.contains("__mpl_obj1_value"), mlog);
        assertTrue(mlog.contains("__mpl_obj1_bonus"), mlog);

        java.nio.file.Files.writeString(project.resolve("mpl.json"), "{ \"entry\": \"src/generated.mil\" }");
        java.nio.file.Files.writeString(sourceDirectory.resolve("generated.mil"), mil);
        CompilationResult regenerated = compiler.compile(new CompilationRequest(project, "v146", true));
        assertTrue(regenerated.succeeded(), () -> regenerated.diagnostics().toString());
    }

    @Test
    void dispatchesDerivedObjectsStoredInThePhysicalObjectPool(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), """
            class Animal {
                public value: Int;
                public fun Animal(value: Int) { this.value = value; }
                public fun score(amount: Int): Int { return this.value + amount; }
            }
            class Dog extends Animal {
                public bonus: Int;
                public fun Dog(value: Int, bonus: Int) { super(value); this.bonus = bonus; }
                public fun score(amount: Int): Int { return super.score(amount) + this.bonus; }
            }
            fun createDog(): Dog { return new Dog(3, 4); }
            fun calculate(): Int {
                val animal: Animal = createDog();
                return animal.score(2);
            }
            val result = calculate();
            """);

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146", true));

        assertTrue(result.succeeded(), () -> result.diagnostics().toString());
        String mlog = result.mlog().orElseThrow();
        assertTrue(mlog.contains("virtual_method_"), mlog);
        assertTrue(result.physicalMemoryLayout().physicalSlots() > 0);
        String mil = result.mil().orElseThrow();
        assertTrue(mil.contains("public value: Int;"), mil);
        assertTrue(mil.contains("public bonus: Int;"), mil);
    }

    @Test
    void laysOutScalarTupleObjectFields(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), """
            class Point {
                private coordinates: (Int, Int);
                public fun Point(x: Int, y: Int) { this.coordinates = (x, y); }
                public fun x(): Int { return this.coordinates[0]; }
            }
            val point = new Point(7, 9);
            val pointX = point.x();
            """);

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146", true));

        assertTrue(result.succeeded(), () -> result.diagnostics().toString());
        String mlog = result.mlog().orElseThrow();
        assertTrue(mlog.contains("__mpl_obj1_coordinates_e0"), mlog);
        assertTrue(mlog.contains("__mpl_obj1_coordinates_e1"), mlog);
        assertTrue(result.mil().orElseThrow().contains("this.coordinates = (x, y);"));
    }

    @Test
    void collectsNestedStaticAllocationsAndAllowsUnusedClasses(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), """
            class Marker {
                private id: Int;
                public fun Marker(id: Int) { this.id = id; }
                public fun get(): Int { return this.id; }
            }
            class Unused {
                private value: Int;
                public fun Unused() { this.value = 0; }
                public fun get(): Int { return this.value; }
            }
            val pair = (new Marker(1), new Marker(2));
            val selected = pair[1].get();
            """);

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146", true));

        assertTrue(result.succeeded(), () -> result.diagnostics().toString());
        String mlog = result.mlog().orElseThrow();
        assertTrue(mlog.contains("__mpl_obj1_id"), mlog);
        assertTrue(mlog.contains("__mpl_obj2_id"), mlog);
        assertTrue(result.mil().orElseThrow().contains("(new Marker(1), new Marker(2))"));
    }

    @Test
    void reusesLocalAllocationPointSlotsAcrossLoopsAndFunctions(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), """
            class Counter {
                private value: Int;
                public fun Counter(initial: Int) { this.value = initial; }
                public fun add(amount: Int): Int {
                    this.value += amount;
                    return this.value;
                }
            }

            fun calculate(seed: Int): Int {
                val local = new Counter(seed);
                return local.add(2);
            }

            var index = 0;
            while (index < 2) {
                val item = new Counter(index);
                item.add(1);
                index += 1;
            }
            val result = calculate(5);
            """);

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146", true));

        assertTrue(result.succeeded(), () -> result.diagnostics().toString());
        String mlog = result.mlog().orElseThrow();
        assertTrue(mlog.contains("__mpl_obj1_value"), mlog);
        assertTrue(mlog.contains("__mpl_obj2_value"), mlog);
        assertFalse(mlog.contains("__mpl_obj3_value"), mlog);
        String mil = result.mil().orElseThrow();
        assertTrue(mil.contains("val item: Counter = new Counter(index);"), mil);
        assertTrue(mil.contains("val local: Counter = new Counter(seed);"), mil);

        java.nio.file.Files.writeString(project.resolve("mpl.json"), "{ \"entry\": \"src/generated.mil\" }");
        java.nio.file.Files.writeString(sourceDirectory.resolve("generated.mil"), mil);
        CompilationResult regenerated = compiler.compile(new CompilationRequest(project, "v146", true));
        assertTrue(regenerated.succeeded(), () -> regenerated.diagnostics().toString());
    }

    @Test
    void compilesOwnedFactoryObjectsThroughThePhysicalPoolAndMilRoundTrip(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), """
            class Counter {
                private value: Int;
                public fun Counter(initial: Int) { this.value = initial; }
                public fun add(amount: Int): Int {
                    this.value += amount;
                    return this.value;
                }
            }

            fun create(initial: Int): Counter {
                return new Counter(initial);
            }

            fun use(): Int {
                val first = create(1);
                val second = create(10);
                return first.add(second.add(2));
            }

            val result = use();
            """);

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146", true));

        assertTrue(result.succeeded(), () -> result.diagnostics().toString());
        assertEquals(4, result.physicalMemoryLayout().objectPoolSlots());
        var pool = result.physicalMemoryLayout().objectPool("Counter").orElseThrow();
        assertEquals(2, pool.capacity());
        assertEquals(2, pool.occupancy().size());
        assertEquals(2, pool.field("value").allocation().size());
        String mlog = result.mlog().orElseThrow();
        assertTrue(mlog.contains("write 0 bank1"), mlog);
        assertTrue(mlog.contains("set __mpl_tmp"), mlog);
        assertTrue(mlog.contains(" -1"), mlog);
        String mil = result.mil().orElseThrow();
        assertTrue(mil.contains("return new Counter(initial);"), mil);
        assertTrue(mil.contains("val first: Counter = create(1);"), mil);

        java.nio.file.Files.writeString(project.resolve("mpl.json"), "{ \"entry\": \"src/generated.mil\" }");
        java.nio.file.Files.writeString(sourceDirectory.resolve("generated.mil"), mil);
        CompilationResult regenerated = compiler.compile(new CompilationRequest(project, "v146", true));
        assertTrue(regenerated.succeeded(), () -> regenerated.diagnostics().toString());
        assertEquals(4, regenerated.physicalMemoryLayout().objectPoolSlots());
    }

    @Test
    void compilesAConfiguredMilEntryThroughRuntimeAndTargetLowering(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(project.resolve("mpl.json"), """
            {
              "entry": "src/main.mil",
              "runtime": { "processors": { "logic": 1 }, "memory": { "cell": 2 } }
            }
            """);
        java.nio.file.Files.writeString(sourceDirectory.resolve("hardware.mplh"), """
            const Status: Message = link("message1");
            const Canvas: Display = link("display1");
            """);
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mil"), """
            var phase: Float = 0.0;
            @unit.eachManaged(@dagger, unit, 3, @unit.alive(unit)) {
                @unit.move(unit, phase, 20.0);
            }
            @io.print(@message1, "phase=", phase);
            @io.draw(@display1, clear, 0, 0, 0);
            """);

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146", true));

        assertTrue(result.succeeded(), () -> result.diagnostics().toString());
        String mlog = result.mlog().orElseThrow();
        assertTrue(mlog.contains("ubind @dagger"));
        assertTrue(mlog.contains("ucontrol move mpl_phase 20.0"));
        assertTrue(mlog.contains("print \"phase=\""));
        assertTrue(mlog.contains("printflush message1"));
        assertTrue(mlog.contains("draw clear 0 0 0"));
        assertTrue(mlog.contains("drawflush display1"));
        assertTrue(result.mil().orElseThrow().contains("@unit.eachManaged(@dagger, unit, 3"));
    }

    @Test
    void compilesPublicBuildingMacrosFromMil(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(project.resolve("mpl.json"), "{ \"entry\": \"src/main.mil\" }");
        java.nio.file.Files.writeString(sourceDirectory.resolve("hardware.mplh"),
            "const FrontTurret: Duo = link(\"duo1\");\n");
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mil"), """
            @building.each(@duo, turret, @building.read(turret, enabled)) {
                @building.control(turret, enabled, false);
            }
            """);

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146", true));

        assertTrue(result.succeeded(), () -> result.diagnostics().toString());
        String mlog = result.mlog().orElseThrow();
        assertTrue(mlog.contains("control enabled duo1 0 0 0 0"), mlog);
        assertTrue(result.mil().orElseThrow().contains("@building.each(@duo, turret"));
    }

    @Test
    void reportsInvalidMilGameLinksBeforeSemanticAnalysis(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(project.resolve("mpl.json"), "{ \"entry\": \"src/main.mil\" }");
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mil"), "@io.print(@message9, \"missing\");\n");

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146"));

        assertFalse(result.succeeded());
        assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("MIL3105")));
    }

    @Test
    void reportsInvalidEntryConfigurationWithAStableLocalizedDiagnostic(@TempDir Path project) throws IOException {
        java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(project.resolve("mpl.json"), "{ \"entry\": \"../outside.mil\" }");

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146"));

        assertFalse(result.succeeded());
        assertEquals("MPL1105", result.diagnostics().get(0).code());
        assertEquals("compiler.source.config", result.diagnostics().get(0).messageKey().orElseThrow());
    }

    @Test
    void compilesMixedMplAndMilModulesWithExportedFunctionsAndValues(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("hardware.mplh"),
            "const Status: Message = link(\"message1\");\n");
        java.nio.file.Files.writeString(sourceDirectory.resolve("math.mpl"), """
            export val factor: Int = 2;
            export fun scale(value: Int): Int { return value * factor; }
            """);
        java.nio.file.Files.writeString(sourceDirectory.resolve("output.mil"), """
            export fun announce(value: Int): Void {
                @io.print(@message1, "value=", value);
            }
            """);
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), """
            import { factor, scale } from "./math";
            import { announce } from "./output.mil";
            val result: Int = scale(21) + factor;
            announce(result);
            """);

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146", true));

        assertTrue(result.succeeded(), () -> result.diagnostics().toString());
        String mil = result.mil().orElseThrow();
        assertTrue(mil.contains("fun __module_math_mpl_scale"));
        assertTrue(mil.contains("fun __module_output_mil_announce"));
        assertTrue(mil.contains("val __module_math_mpl_factor: Int = 2;"));
        assertTrue(result.mlog().orElseThrow().contains("print \"value=\""));
        assertTrue(result.mlog().orElseThrow().contains("printflush message1"));
    }

    @Test
    void resolvesExportedFunctionOverloadsAcrossModules(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("types.mpl"), """
            export class Animal { public fun Animal() {} }
            export class Dog extends Animal { public fun Dog() { super(); } }
            export fun classify(value: Animal): Int { return 1; }
            export fun classify(value: Dog): Int { return 2; }
            """);
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), """
            import { Animal, Dog, classify } from "./types";
            val animal: Animal = new Dog();
            val result: Int = classify(animal);
            val specific: Int = classify(new Dog());
            """);

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146", true));

        assertTrue(result.succeeded(), () -> result.diagnostics().toString());
        String mil = result.mil().orElseThrow();
        assertTrue(mil.contains("fun __module_types_mpl_classify(value: __module_types_mpl_Animal): Int"), mil);
        assertTrue(mil.contains("fun __module_types_mpl_classify(value: __module_types_mpl_Dog): Int"), mil);

        java.nio.file.Files.writeString(project.resolve("mpl.json"), "{ \"entry\": \"src/generated.mil\" }");
        java.nio.file.Files.writeString(sourceDirectory.resolve("generated.mil"), mil);
        CompilationResult regenerated = compiler.compile(new CompilationRequest(project, "v146", true));
        assertTrue(regenerated.succeeded(), () -> regenerated.diagnostics().toString());
    }

    @Test
    void saturatesOutOfRangeIntLiteralsAndConstantArithmetic(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), """
            val maximum: Int = 2147483647 + 1;
            val minimum: Int = -2147483648 - 1;
            val literal: Int = 2147483648;
            """);

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146"));

        assertTrue(result.succeeded());
        assertEquals("""
            set mpl_maximum 2147483647
            set mpl_minimum -2147483648
            set mpl_literal 2147483647
            stop
            """, result.mlog().orElseThrow());
    }

    @Test
    void lowersDynamicIntArithmeticWithSaturatingBounds(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), """
            var maximum: Int = 2147483647;
            var minimum: Int = -2147483648;
            var above: Int = maximum + 1;
            var below: Int = minimum - 1;
            """);

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146", true));

        assertTrue(result.succeeded());
        String mlog = result.mlog().orElseThrow();
        assertTrue(mlog.contains("op add __mpl_tmp0 mpl_maximum 1"));
        assertTrue(mlog.contains("op min __mpl_tmp0 __mpl_tmp0 2147483647"));
        assertTrue(mlog.contains("op max __mpl_tmp0 __mpl_tmp0 -2147483648"));
        assertTrue(mlog.contains("op sub __mpl_tmp1 mpl_minimum 1"));
        assertTrue(mlog.contains("set mpl_above __mpl_tmp0"));
        assertTrue(mlog.contains("set mpl_below __mpl_tmp1"));
    }

    @Test
    void lowersDynamicIntRemainderByZeroToZero(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), """
            var divisor: Int = 0;
            var remainder: Int = 7 % divisor;
            """);

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146", true));

        assertTrue(result.succeeded());
        String mlog = result.mlog().orElseThrow();
        assertTrue(mlog.contains("jump mpl_int_mod_non_zero_0 notEqual mpl_divisor 0"));
        assertTrue(mlog.contains("set __mpl_tmp0 0"));
        assertTrue(mlog.contains("op mod __mpl_tmp0 7 mpl_divisor"));
        assertTrue(mlog.contains("set mpl_remainder __mpl_tmp0"));
    }

    @Test
    void normalizesConstantFloatDivisionByZeroAndOverflow(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), """
            val zero: Float = 1.0 / 0.0;
            val positive: Float = 1.7976931348623157e308 + 1.7976931348623157e308;
            val negative: Float = -1.7976931348623157e308 * 2.0;
            """);

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146"));

        assertTrue(result.succeeded());
        assertEquals("""
            set mpl_zero 0.0
            set mpl_positive 1.7976931348623157E308
            set mpl_negative -1.7976931348623157E308
            stop
            """, result.mlog().orElseThrow());
    }

    @Test
    void lowersDynamicFloatOperationsBeforeTargetOverflow(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), """
            var first: Float = Clock.timeMs;
            var sum: Float = first + 1.0;
            var product: Float = first * 2.0;
            var quotient: Float = first / first;
            var difference: Float = first - 1.0;
            """);

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146", true));

        assertTrue(result.succeeded());
        String mlog = result.mlog().orElseThrow();
        assertTrue(mlog.contains("jump mpl_float_add_rhs_positive_0 greaterThan 1.0 0"));
        assertTrue(mlog.contains("op sub __mpl_tmp1 1.7976931348623157E308 1.0"));
        assertTrue(mlog.contains("set __mpl_tmp0 1.7976931348623157E308"));
        assertTrue(mlog.contains("jump mpl_float_mul_rhs_positive_large_5 greaterThan 2.0 1"));
        assertTrue(mlog.contains("op div __mpl_tmp3 1.7976931348623157E308 2.0"));
        assertTrue(mlog.contains("jump mpl_float_div_non_zero_11 notEqual mpl_first 0"));
        assertTrue(mlog.contains("set __mpl_tmp4 0.0"));
        assertTrue(mlog.contains("jump mpl_float_sub_rhs_positive_19 greaterThan 1.0 0"));
        assertTrue(mlog.contains("op add __mpl_tmp7 -1.7976931348623157E308 1.0"));
    }

    @Test
    void rejectsNonFiniteFloatLiterals(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), "var impossible: Float = 1.0e309;");

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146"));

        assertFalse(result.succeeded());
        assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("MPL3103")
            && diagnostic.message().contains("Float 字面量必须是有限值")));
    }

    @Test
    void lowersExplicitFloatToIntConversionsWithSaturatingBounds(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), """
            val downward: Int = Int.floor(Clock.tick);
            val upward: Int = Int.ceil(Clock.tick);
            val nearest: Int = Int.round(Clock.tick);
            """);

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146"));

        assertTrue(result.succeeded());
        assertEquals("""
            op floor __mpl_tmp0 @tick 0
            op min __mpl_tmp0 __mpl_tmp0 2147483647
            op max __mpl_tmp0 __mpl_tmp0 -2147483648
            set mpl_downward __mpl_tmp0
            op ceil __mpl_tmp1 @tick 0
            op min __mpl_tmp1 __mpl_tmp1 2147483647
            op max __mpl_tmp1 __mpl_tmp1 -2147483648
            set mpl_upward __mpl_tmp1
            op round __mpl_tmp2 @tick 0
            op min __mpl_tmp2 __mpl_tmp2 2147483647
            op max __mpl_tmp2 __mpl_tmp2 -2147483648
            set mpl_nearest __mpl_tmp2
            stop
            """, result.mlog().orElseThrow());
        assertTrue(result.mil().orElseThrow().contains("val downward: Int = Int.floor(Clock.tick);"));
    }

    @Test
    void rejectsNonFloatIntConversionArguments(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), "val index: Int = Int.floor(1);");

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146"));

        assertFalse(result.succeeded());
        assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("MPL3103")
            && diagnostic.message().contains("只接受 Float 参数")));
    }

    @Test
    void optimizesConstantExpressionsAndConstantBranchesBeforeMlogLowering(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), """
            var value: Int = 1 + 2 * 3;
            if (false) {
                value = 99;
            } else {
                value += 1;
            }
            while (false) {
                value += 100;
            }
            """);

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146"));

        assertTrue(result.succeeded());
        assertEquals("set mpl_value 7\nop add __mpl_tmp0 mpl_value 1\nop min __mpl_tmp0 __mpl_tmp0 2147483647\nop max __mpl_tmp0 __mpl_tmp0 -2147483648\nset mpl_value __mpl_tmp0\nstop\n", result.mlog().orElseThrow());
        assertEquals(2, result.optimizationReport().constantFolds());
        assertEquals(1, result.optimizationReport().eliminatedBranches());
        assertEquals(1, result.optimizationReport().eliminatedLoops());
    }

    @Test
    void lowersLogicalOperatorsWithShortCircuitControlFlow(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), """
            var enabled: Bool = false;
            var changed: Bool = false;
            enabled && (changed = true);
            enabled || (changed = true);
            """);

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146", true));

        assertTrue(result.succeeded());
        String mlog = result.mlog().orElseThrow();
        assertTrue(mlog.contains("jump mpl_short_circuit_end_0 equal mpl_enabled 0"));
        assertTrue(mlog.contains("jump mpl_short_circuit_end_1 notEqual mpl_enabled 0"));
        assertFalse(mlog.contains("op land"));
        assertFalse(mlog.contains("op or"));
        assertTrue(mlog.indexOf("jump mpl_short_circuit_end_0") < mlog.indexOf("set mpl_changed 1"));
    }

    @Test
    void compilesStructuredIfElseWithBranchLocalVariables(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), """
            var total: Int = 0;
            if (true) {
                total = 1;
            } else {
                total = 2;
            }
            """);

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146", true));

        assertTrue(result.succeeded());
        assertEquals("""
            set mpl_total 0
            set mpl_total 1
            stop
            """, result.mlog().orElseThrow());
        assertFalse(result.mil().orElseThrow().contains("if (true) {"));
        assertFalse(result.mil().orElseThrow().contains("else {"));
    }

    @Test
    void compilesDoWhileBreakAndContinueToTheCorrectLoopTargets(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), """
            var count: Int = 0;
            do {
                count += 1;
                if (count < 2) {
                    continue;
                }
                if (count > 4) {
                    break;
                }
            } while (count < 10);
            """);

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146", true));

        assertTrue(result.succeeded());
        assertEquals("""
            set mpl_count 0
            mpl_do_start_0:
            op add __mpl_tmp0 mpl_count 1
            op min __mpl_tmp0 __mpl_tmp0 2147483647
            op max __mpl_tmp0 __mpl_tmp0 -2147483648
            set mpl_count __mpl_tmp0
            op lessThan __mpl_tmp1 mpl_count 2
            jump mpl_if_end_3 equal __mpl_tmp1 0
            jump mpl_do_condition_1 always 0 0
            mpl_if_end_3:
            op greaterThan __mpl_tmp2 mpl_count 4
            jump mpl_if_end_4 equal __mpl_tmp2 0
            jump mpl_do_end_2 always 0 0
            mpl_if_end_4:
            mpl_do_condition_1:
            op lessThan __mpl_tmp3 mpl_count 10
            jump mpl_do_start_0 notEqual __mpl_tmp3 0
            mpl_do_end_2:
            stop
            """, result.mlog().orElseThrow());
        assertTrue(result.mil().orElseThrow().contains("do {"));
        assertTrue(result.mil().orElseThrow().contains("continue;"));
        assertTrue(result.mil().orElseThrow().contains("break;"));
    }

    @Test
    void targetsUnitIterationNextAndEndForContinueAndBreak(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), """
            for (var unit : Unit.getAllDagger()) {
                if (!unit.alive) {
                    continue;
                }
                break;
            }
            """);

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146", true));

        assertTrue(result.succeeded());
        String mlog = result.mlog().orElseThrow();
        assertTrue(mlog.contains("jump mpl_unit_next_1 always 0 0"));
        assertTrue(mlog.contains("jump mpl_unit_end_2 always 0 0"));
    }

    @Test
    void compilesStaticAggregateTraversalWithContinueAndBreak(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), """
            val values: Int[] = [1, 2, 3];
            var total: Int = 0;
            for (var value : values) {
                if (value == 2) {
                    continue;
                }
                total += value;
                if (value == 3) {
                    break;
                }
            }
            """);

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146", true));

        assertTrue(result.succeeded());
        String mlog = result.mlog().orElseThrow();
        assertTrue(mlog.contains("set mpl_value mpl_values_e0"));
        assertTrue(mlog.contains("set mpl_value mpl_values_e1"));
        assertTrue(mlog.contains("set mpl_value mpl_values_e2"));
        assertTrue(mlog.contains("jump mpl_aggregate_next_1 always 0 0"));
        assertTrue(mlog.contains("jump mpl_aggregate_end_0 always 0 0"));
        assertTrue(result.mil().orElseThrow().contains("for (var value : values) {"));
    }

    @Test
    void compilesCountingForAndRunsUpdateAfterContinue(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), """
            var total: Int = 0;
            for (var i: Int = 0; i < 3; i += 1) {
                if (i == 1) {
                    continue;
                }
                total += i;
            }
            """);

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146", true));

        assertTrue(result.succeeded());
        assertEquals("""
            set mpl_total 0
            set mpl_i 0
            mpl_for_condition_0:
            op lessThan __mpl_tmp0 mpl_i 3
            jump mpl_for_end_2 equal __mpl_tmp0 0
            op equal __mpl_tmp1 mpl_i 1
            jump mpl_if_end_3 equal __mpl_tmp1 0
            jump mpl_for_update_1 always 0 0
            mpl_if_end_3:
            op add __mpl_tmp2 mpl_total mpl_i
            op min __mpl_tmp2 __mpl_tmp2 2147483647
            op max __mpl_tmp2 __mpl_tmp2 -2147483648
            set mpl_total __mpl_tmp2
            mpl_for_update_1:
            op add __mpl_tmp3 mpl_i 1
            op min __mpl_tmp3 __mpl_tmp3 2147483647
            op max __mpl_tmp3 __mpl_tmp3 -2147483648
            set mpl_i __mpl_tmp3
            jump mpl_for_condition_0 always 0 0
            mpl_for_end_2:
            stop
            """, result.mlog().orElseThrow());
        assertTrue(result.mil().orElseThrow().contains(
            "for (var i: Int = 0; (i < 3); i += 1) {"));
    }

    @Test
    void compilesNonRecursiveFunctionWithStaticCounterAbi(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), """
            fun add(left: Int, right: Int): Int {
                return left + right;
            }
            var result: Int = add(2, 3);
            """);

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146", true));

        assertTrue(result.succeeded());
        assertEquals("""
            set __mpl_tmp0 2
            set __mpl_tmp1 3
            set __mpl_fn0_arg0 __mpl_tmp0
            set __mpl_fn0_arg1 __mpl_tmp1
            op add __mpl_fn0_return @counter 1
            set @counter 9
            set __mpl_tmp2 __mpl_fn0_result
            set mpl_result __mpl_tmp2
            stop
            mpl_function_add_0:
            op add __mpl_tmp3 __mpl_fn0_arg0 __mpl_fn0_arg1
            op min __mpl_tmp3 __mpl_tmp3 2147483647
            op max __mpl_tmp3 __mpl_tmp3 -2147483648
            set __mpl_fn0_result __mpl_tmp3
            set @counter __mpl_fn0_return
            """, result.mlog().orElseThrow());
        assertEquals("""
            // 由 MPL 自动生成的 MIL；请通过 mpl build 重新生成，勿直接编辑。
            // 普通结构保留为 MIL；@unit.* 与 @io.* 是由 target profile 展开的受限宏。
            fun add(left: Int, right: Int): Int {
                return (left + right);
            }
            var result: Int = add(2, 3);
            """, result.mil().orElseThrow());
    }

    @Test
    void compilesNestedAndImplicitVoidFunctionReturnsWithCollisionFreeSlots(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), """
            fun a(b_c: Int) {
                var doubled: Int = b_c * 2;
            }
            fun a_b(c: Int): Int {
                a(c);
                return c + 1;
            }
            var result: Int = a_b(4);
            """);

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146", true));

        assertTrue(result.succeeded());
        String mlog = result.mlog().orElseThrow();
        assertTrue(mlog.contains("set __mpl_fn1_arg0 __mpl_tmp0"));
        assertTrue(mlog.contains("set __mpl_fn0_arg0 __mpl_tmp"));
        assertTrue(mlog.contains("set __mpl_fn0_local_doubled __mpl_tmp"));
        assertTrue(mlog.contains("set @counter __mpl_fn0_return"));
        assertTrue(mlog.contains("set __mpl_fn1_result __mpl_tmp"));
        assertTrue(mlog.contains("set @counter __mpl_fn1_return"));
        assertFalse(mlog.contains("__mpl_fn_a_param_b_c"));
        assertFalse(mlog.contains("__mpl_fn_a_b_param_c"));
    }

    @Test
    void compilesStaticallyLaidOutTupleAndArrayElements(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), """
            val test : (Int,Int,Int) = (1,2,3);
            val array : Int[] = [1,2,3,4,5];
            var selected: Int = test[1] + array[3];
            var count: Int = array.size;
            """);

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146"));

        assertTrue(result.succeeded());
        assertEquals("""
            set mpl_test_e0 1
            set mpl_test_e1 2
            set mpl_test_e2 3
            set mpl_array_e0 1
            set mpl_array_e1 2
            set mpl_array_e2 3
            set mpl_array_e3 4
            set mpl_array_e4 5
            op add __mpl_tmp0 mpl_test_e1 mpl_array_e3
            op min __mpl_tmp0 __mpl_tmp0 2147483647
            op max __mpl_tmp0 __mpl_tmp0 -2147483648
            set mpl_selected __mpl_tmp0
            set mpl_count 5
            stop
            """, result.mlog().orElseThrow());
        assertTrue(result.mil().orElseThrow().contains("val test: (Int, Int, Int) = (1, 2, 3);"));
        assertTrue(result.mil().orElseThrow().contains("val array: Int[] = [1, 2, 3, 4, 5];"));
    }

    @Test
    void compilesStaticListSetMembershipAndArrayElementUpdate(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), """
            var array: Int[] = [1, 2, 3];
            array.set(1, 9);
            val queue: List<Int> = listOf(2, 4, 6);
            val tags: Set<Int> = Set.of(3, 5, 7);
            var found: Bool = queue.contains(4);
            """);

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146"));

        assertTrue(result.succeeded());
        String mlog = result.mlog().orElseThrow();
        assertTrue(mlog.contains("set mpl_array_e1 9"));
        assertTrue(mlog.contains("set mpl_queue_e0 2"));
        assertTrue(mlog.contains("set mpl_tags_e2 7"));
        assertTrue(mlog.contains("op equal __mpl_tmp2 mpl_queue_e0 __mpl_tmp0"));
        assertTrue(mlog.contains("op or __mpl_tmp1 __mpl_tmp1 __mpl_tmp2"));
        assertTrue(result.mil().orElseThrow().contains("val queue: List<Int> = listOf(2, 4, 6);"));
        assertTrue(result.mil().orElseThrow().contains("val tags: Set<Int> = setOf(3, 5, 7);"));
    }

    @Test
    void compilesMessagePrintUsingTheAutomaticFirstMessageLink(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("hardware.mplh"), "const AlertBoard: Message = link(\"message1\");");
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), "var frog: Int = 21;\nAlertBoard.print(\"frog=\", frog * 2);");

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146"));

        assertTrue(result.succeeded());
        assertEquals("_0:\nsensor __mpl_hw0 message1 @type\njump _0 notEqual __mpl_hw0 @message\nset mpl_frog 21\nprint \"frog=\"\nop mul __mpl_tmp0 mpl_frog 2\nop min __mpl_tmp0 __mpl_tmp0 2147483647\nop max __mpl_tmp0 __mpl_tmp0 -2147483648\nprint __mpl_tmp0\nprintflush message1\nstop\n",
            result.mlog().orElseThrow());
    }

    @Test
    void lowersDirectDisplayDrawingWithoutLeakingRawMlog(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("hardware.mplh"),
            "const Screen: Display = link(\"display1\", width: 80, height: 80);");
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), """
            Screen.clear(Color.black);
            Screen.fill(Color.green);
            Screen.fillRect(1, 2, 30, 40);
            Screen.stroke(Color.white);
            Screen.strokeRect(2, 3, 20, 10);
            Screen.line(0, 0, 80, 80);
            """);

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146"));

        assertTrue(result.succeeded());
        assertEquals("""
            _0:
            sensor __mpl_hw0 display1 @type
            jump _0 notEqual __mpl_hw0 @logic-display
            draw clear 0 0 0 0 0 0
            draw color 0 255 0 255 0 0
            draw rect 1 2 30 40 0 0
            draw color 255 255 255 255 0 0
            draw lineRect 2 3 20 10 0 0
            draw line 0 0 80 80 0 0
            drawflush display1
            stop
            """, result.mlog().orElseThrow());
        assertTrue(result.mil().orElseThrow().contains("@io.draw(@display1, clear, 0, 0, 0);"));
        assertTrue(result.mil().orElseThrow().contains("@io.drawFlush(@display1);"));
    }

    @Test
    void acceptsEveryProfileDisplayTypeWhenPhysicalSizeIsOmitted(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("hardware.mplh"),
            "const Screen: Display = link(\"display1\");");
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"),
            "Screen.clear(Color.black);");

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146"));

        assertTrue(result.succeeded(), () -> result.diagnostics().toString());
        String mlog = result.mlog().orElseThrow();
        assertTrue(mlog.contains("equal __mpl_hw0 @logic-display"), mlog);
        assertTrue(mlog.contains("equal __mpl_hw0 @large-logic-display"), mlog);
        assertTrue(mlog.contains("drawflush display1"), mlog);
    }

    @Test
    void automaticallyFlushesDrawingInsideLoops(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("hardware.mplh"),
            "const Screen: Display = link(\"display1\", width: 80, height: 80);");
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), """
            while (true) {
                Screen.fill(Color.green);
                Screen.fillRect(1, 2, 3, 4);
            }
            """);

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146"));

        assertTrue(result.succeeded());
        String mlog = result.mlog().orElseThrow();
        assertTrue(mlog.indexOf("draw rect 1 2 3 4 0 0") < mlog.indexOf("drawflush display1"));
        int loopBack = mlog.indexOf("jump _1 always 0 0");
        assertTrue(loopBack >= 0);
        assertTrue(mlog.indexOf("drawflush display1") < loopBack);
        assertTrue(result.mil().orElseThrow().contains("@io.drawFlush(@display1);"));
    }

    @Test
    void rejectsExplicitDisplayFlushBecauseItIsRuntimeManaged(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("hardware.mplh"),
            "const Screen: Display = link(\"display1\");");
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), """
            Screen.flush();
            Screen.fill(Color.blue);
            """);

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146"));

        assertFalse(result.succeeded());
        assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("MPL3203")
            && diagnostic.message().contains("不提供 Display.flush")));
    }

    @Test
    void splitsDisplayDrawBuffersAtTheTargetLimit(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("hardware.mplh"),
            "const Screen: Display = link(\"display1\");");
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"),
            "Screen.fillRect(0, 0, 1, 1);\n".repeat(257));

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146"));

        assertTrue(result.succeeded());
        assertEquals(2, result.mlog().orElseThrow().lines()
            .filter(line -> line.equals("drawflush display1")).count());
    }

    @Test
    void flushesTheProcessorGraphicsBufferBeforeSwitchingDisplays(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("hardware.mplh"), """
            const Left: Display = link("display1");
            const Right: Display = link("display2");
            """);
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), """
            Left.fillRect(0, 0, 1, 1);
            Right.fillRect(0, 0, 1, 1);
            """);

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146"));

        assertTrue(result.succeeded());
        String mlog = result.mlog().orElseThrow();
        assertTrue(mlog.indexOf("draw rect 0 0 1 1 0 0") < mlog.indexOf("drawflush display1"));
        assertTrue(mlog.indexOf("drawflush display1") < mlog.lastIndexOf("draw rect 0 0 1 1 0 0"));
        assertTrue(mlog.contains("drawflush display2\nstop"));
    }

    @Test
    void expandsACombinedDisplayBatchWithPerTileCoordinates(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("hardware.mplh"), """
            const Left: Display = link("display1", width: 80, height: 80);
            const Right: Display = link("display2", width: 80, height: 80);
            const Wall: Display = Display.combine([[Left, Right]]);
            """);
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), """
            Wall.clear(Color.black);
            Wall.fill(Color.green);
            Wall.fillRect(72, 4, 16, 8);
            Wall.line(0, 0, Wall.width, Wall.height);
            """);

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146"));

        assertTrue(result.succeeded(), () -> result.diagnostics().toString());
        String mlog = result.mlog().orElseThrow();
        assertTrue(mlog.contains("""
            draw clear 0 0 0 0 0 0
            draw color 0 255 0 255 0 0
            draw rect 72 4 16 8 0 0
            draw line 0 0 160 80 0 0
            drawflush display1
            draw clear 0 0 0 0 0 0
            draw color 0 255 0 255 0 0
            draw rect -8 4 16 8 0 0
            draw line -80 0 80 80 0 0
            drawflush display2
            """), mlog);
        String mil = result.mil().orElseThrow();
        assertTrue(mil.contains("@io.draw(@display2, rect, -8, 4, 16, 8);"), mil);
        assertTrue(mil.contains("@io.drawFlush(@display2);"), mil);
    }

    @Test
    void evaluatesCombinedDisplayArgumentsOnceBeforeFanOut(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("hardware.mplh"), """
            const Left: Display = link("display1", width: 80, height: 80);
            const Right: Display = link("display2", width: 80, height: 80);
            const Wall: Display = Display.combine([[Left, Right]]);
            """);
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), """
            var base: Int = 71;
            Wall.fillRect(base + 1, 4, 16, 8);
            """);

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146"));

        assertTrue(result.succeeded(), () -> result.diagnostics().toString());
        String mlog = result.mlog().orElseThrow();
        assertEquals(1, mlog.lines().filter(line -> line.startsWith("op add ")).count(), mlog);
        assertEquals(2, mlog.lines().filter(line -> line.startsWith("draw rect ")).count(), mlog);
        assertTrue(result.mil().orElseThrow().contains("val __mpl_display_arg0: Int = (base + 1);"));
    }

    @Test
    void expandsBuildingTraversalOverDeclaredLinksOnly(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("hardware.mplh"), """
            const NorthTurret: Duo = link("duo1");
            const SouthTurret: Duo = link("duo2");
            const Status: Message = link("message1");
            """);
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), """
            var health: Float = 0.0;
            for (var turret : Building.getAllDuo()) {
                health += turret.health;
                turret.shoot(turret.x, turret.y, true);
            }
            """);

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146", true));

        assertTrue(result.succeeded());
        String mlog = result.mlog().orElseThrow();
        assertTrue(mlog.contains("duo1 @health"));
        assertTrue(mlog.contains("duo2 @health"));
        assertTrue(mlog.contains("control shoot duo1"));
        assertTrue(mlog.contains("control shoot duo2"));
        assertTrue(result.mil().orElseThrow().contains("@building.each(@duo, turret) {"));
    }

    @Test
    void filtersEachStaticallyLinkedBuildingBeforeExecutingTheTraversalBody(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("hardware.mplh"), """
            const NorthTurret: Duo = link("duo1");
            const SouthTurret: Duo = link("duo2");
            """);
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), """
            val minimum: Float = 10.0;
            var matched: Int = 0;
            for (var turret : Building.getAllDuo(_ => _.enabled).where(candidate => candidate.health > minimum)) {
                matched += 1;
                turret.setEnabled(false);
            }
            """);

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146", true));

        assertTrue(result.succeeded());
        String mlog = result.mlog().orElseThrow();
        assertTrue(mlog.contains("duo1 @enabled"));
        assertTrue(mlog.contains("duo1 @health"));
        assertTrue(mlog.contains("duo2 @enabled"));
        assertTrue(mlog.contains("duo2 @health"));
        assertTrue(mlog.contains("control enabled duo1 0 0 0 0"));
        assertTrue(mlog.contains("control enabled duo2 0 0 0 0"));
        assertTrue(mlog.indexOf("duo1 @enabled") < mlog.indexOf("control enabled duo1"));
        assertTrue(mlog.indexOf("duo2 @enabled") < mlog.indexOf("control enabled duo2"));
        assertTrue(result.mil().orElseThrow().contains(
            "@building.each(@duo, turret, @building.read(turret, enabled), (@building.read(turret, health) > minimum)) {"));
    }

    @Test
    void savesCountsGetsAndControlsBuildingSets(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("hardware.mplh"), """
            const NorthTurret: Duo = link("duo1");
            const SouthTurret: Duo = link("duo2");
            """);
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), """
            val turrets: Set<Building<Duo>> = Building.getAllDuo().where(_.enabled);
            val count: Int = turrets.size;
            val first: Building<Duo>? = turrets.get(0);
            if (first != null) {
                val health: Float = first.health;
                first.setEnabled(false);
            }
            for (var turret : turrets) {
                turret.shoot(1.0, 2.0, true);
            }
            """);

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146", true));

        assertTrue(result.succeeded(), () -> result.diagnostics().toString());
        String mil = result.mil().orElseThrow();
        assertTrue(mil.contains(
            "val turrets: Set<Building<Duo>> = Building.getAllDuo().where(_ => @building.read(_, enabled));"));
        assertTrue(mil.contains("val count: Int = @building.count(@duo, _, @building.read(_, enabled));"));
        assertTrue(mil.contains("val first: Building<Duo>? = @building.get(@duo, _, 0, @building.read(_, enabled));"));
        assertTrue(mil.contains("@building.read(first, health)"));
        assertTrue(mil.contains("@building.control(first, enabled, false);"));

        String mlog = result.mlog().orElseThrow();
        assertFalse(mlog.contains("mpl_turrets"));
        assertTrue(mlog.contains("set mpl_count"));
        assertTrue(mlog.lines().anyMatch(line -> line.matches("set __mpl_tmp\\d+ duo1")), mlog);
        assertTrue(mlog.lines().anyMatch(line -> line.matches("set __mpl_tmp\\d+ duo2")), mlog);
        assertTrue(mlog.lines().anyMatch(line -> line.matches("set mpl_first __mpl_tmp\\d+")), mlog);
        assertTrue(mlog.contains("sensor __mpl_tmp"));
        assertTrue(mlog.lines().anyMatch(line -> line.matches("set __mpl_tmp\\d+ 1")), mlog);
        assertTrue(mlog.lines().anyMatch(line -> line.matches("set __mpl_tmp\\d+ 2")), mlog);
        assertTrue(mlog.lines().filter(line -> line.matches("jump \\S+ notEqual mpl_first [12]")).count() >= 4,
            mlog);
        assertFalse(mlog.contains("mpl_first @health"));
        assertFalse(mlog.contains("control enabled mpl_first"));
        assertTrue(mlog.lines().anyMatch(line -> line.matches("sensor __mpl_tmp\\d+ __mpl_tmp\\d+ @health")), mlog);
        assertTrue(mlog.lines().anyMatch(line -> line.matches("control enabled __mpl_tmp\\d+ 0 0 0 0")), mlog);
        assertTrue(mlog.contains("control shoot duo1 1.0 2.0 1 0"));
        assertTrue(mlog.contains("control shoot duo2 1.0 2.0 1 0"));
    }

    @Test
    void rejectsUnsafeBuildingSetUses(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("hardware.mplh"),
            "const Turret: Duo = link(\"duo1\");");
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), """
            var mutable = Building.getAllDuo();
            var unstable: Building<Duo>? = Building.getAllDuo().get(0);
            val invented: Building<Duo>? = null;
            val wrongIndex = Building.getAllDuo().get(1.0);
            val missing = Building.getAllDuo().get(0);
            val health = missing.health;
            missing.setEnabled(true);
            if (missing != null) {
                for (var other : Building.getAllDuo().where(_.health > missing.health)) {
                    other.setEnabled(false);
                }
            }
            """);

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146"));

        assertFalse(result.succeeded());
        assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> "MPL3201".equals(diagnostic.code())
            && diagnostic.message().contains("只能使用 val")));
        assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> "MPL3210".equals(diagnostic.code())));
        assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> "MPL3212".equals(diagnostic.code())
            && diagnostic.message().contains("只能使用 val")));
        assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> "MPL3212".equals(diagnostic.code())
            && diagnostic.message().contains("get(index) 初始化")));
        assertTrue(result.diagnostics().stream().filter(diagnostic -> "MPL3211".equals(diagnostic.code())).count() >= 2);
        assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> "MPL3201".equals(diagnostic.code())
            && diagnostic.message().contains("val 标量")));
    }

    @Test
    void rejectsImpureOrNonLambdaBuildingFilters(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("hardware.mplh"),
            "const Turret: Duo = link(\"duo1\");");
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), """
            var threshold: Float = 5.0;
            for (var turret : Building.getAllDuo().where(_ => _.health > threshold)) {
                turret.setEnabled(true);
            }
            for (var turret : Building.getAllDuo().where(true)) {
                turret.setEnabled(true);
            }
            """);

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146"));

        assertFalse(result.succeeded());
        assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("MPL3201")
            && diagnostic.message().contains("val 标量")));
        assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("MPL3201")
            && diagnostic.message().contains("必须是 lambda")));
    }

    @Test
    void removesBuildingTraversalWithAConstantFalseFilter(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("hardware.mplh"),
            "const Turret: Duo = link(\"duo1\");");
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), """
            for (var turret : Building.getAllDuo(_ => false).where(_ => true)) {
                turret.setEnabled(true);
            }
            """);

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146"));

        assertTrue(result.succeeded());
        assertEquals("""
            _0:
            sensor __mpl_hw0 duo1 @type
            jump _0 notEqual __mpl_hw0 @duo
            stop
            """, result.mlog().orElseThrow());
        assertEquals(1, result.optimizationReport().eliminatedStatements());
    }

    @Test
    void removesBuildingTraversalWhenNoMatchingLinkIsDeclared(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("hardware.mplh"),
            "const Status: Message = link(\"message1\");");
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), """
            for (var turret : Building.getAllDuo()) {
                turret.shoot(1.0, 2.0, true);
            }
            """);

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146"));

        assertTrue(result.succeeded());
        assertEquals("""
            _0:
            sensor __mpl_hw0 message1 @type
            jump _0 notEqual __mpl_hw0 @message
            stop
            """, result.mlog().orElseThrow());
    }

    @Test
    void compilesImmutableStaticStringsAndFoldsLiteralConcatenation(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("hardware.mplh"),
            "const AlertBoard: Message = link(\"message1\");");
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), """
            val title: String = "MPL" + " demo";
            AlertBoard.print(title, " v1");
            """);

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146"));

        assertTrue(result.succeeded());
        String mlog = result.mlog().orElseThrow();
        assertTrue(mlog.contains("set mpl_title "), mlog);
        assertTrue(mlog.contains("set @counter __mpl_tmp"), mlog);
        assertTrue(mlog.contains("print \" v1\""), mlog);
        assertTrue(mlog.contains("print \"M\""), mlog);
        assertTrue(result.physicalMemoryLayout().stringRuntime().slots() > 0);
        assertTrue(result.mil().orElseThrow().contains("val title: String = \"MPL demo\";"));
    }

    @Test
    void lowersStringConcatenationInMessagePrintWithoutAnIntermediateValue(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("hardware.mplh"),
            "const AlertBoard: Message = link(\"message1\");");
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), """
            val title: String = "MPL";
            AlertBoard.print("运行 ", title + " v" + "1");
            """);

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146"));

        assertTrue(result.succeeded());
        String mlog = result.mlog().orElseThrow();
        assertTrue(mlog.contains("print \"运行 \""), mlog);
        assertTrue(mlog.contains("print \" v1\""), mlog);
        assertTrue(mlog.contains("set @counter __mpl_tmp"), mlog);
        assertTrue(mlog.contains("printflush message1"), mlog);
        assertTrue(result.mil().orElseThrow().contains("@io.print(@message1, \"运行 \""));
        assertTrue(result.physicalMemoryLayout().stringRuntime().concatenations().isEmpty(),
            "print 专用拼接不应分配中间 String 缓冲");
    }

    @Test
    void compilesDynamicStringConcatenationOutsideMessagePrint(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("hardware.mplh"),
            "const Status: Message = link(\"message1\");");
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), """
            var title: String = "MP";
            title = "MPL";
            val banner: String = title + " v1";
            val length: Int = banner.length;
            val same: Bool = banner == "MPL v1";
            Status.print(banner, ":", length, ":", same);
            """);

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146"));

        assertTrue(result.succeeded(), () -> result.diagnostics().toString());
        String mlog = result.mlog().orElseThrow();
        assertTrue(mlog.contains("read "), mlog);
        assertTrue(mlog.contains("write "), mlog);
        assertTrue(mlog.contains("set @counter __mpl_tmp"), mlog);
        assertFalse(mlog.contains("printchar "), mlog);
        assertTrue(mlog.contains("printflush message1"), mlog);
        assertTrue(result.mil().orElseThrow().contains("val banner: String = (title + \" v1\");"));
        assertTrue(result.physicalMemoryLayout().stringRuntime().slots() > 0);
    }

    @Test
    void usesV159PrintCharForDynamicStringOutput(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("hardware.mplh"),
            "const Status: Message = link(\"message1\");");
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), """
            var title: String = "😀";
            title = title + "PL";
            Status.print(title);
            """);

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v159.7"));

        assertTrue(result.succeeded(), () -> result.diagnostics().toString());
        String mlog = result.mlog().orElseThrow();
        assertTrue(mlog.contains("printchar "), mlog);
        assertFalse(mlog.contains("__mpl_string_return"), mlog);
        assertFalse(mlog.contains("string_unit"), mlog);
        assertTrue(mlog.contains("write 55357 "), mlog);
        assertTrue(mlog.contains("write 56832 "), mlog);
        assertTrue(mlog.contains("printflush message1"), mlog);
        var optimization = result.optimizationReport().profileOptimizations().stream()
            .filter(value -> value.name().equals("printcharStringOutput")).findFirst().orElseThrow();
        assertEquals(1, optimization.applied());
        assertTrue(optimization.estimatedInstructionsSaved() > 0);
        assertTrue(optimization.estimatedLabelsSaved() > 0);
    }

    @Test
    void givesEachStringFunctionCallAnIndependentResultBuffer(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("hardware.mplh"),
            "const Status: Message = link(\"message1\");");
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), """
            fun copy(value: String): String {
                return value;
            }

            val same: Bool = copy("A") == copy("B");
            Status.print(same);
            """);

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146"));

        assertTrue(result.succeeded(), () -> result.diagnostics().toString());
        assertEquals(2, result.physicalMemoryLayout().stringRuntime().callResults().size());
        assertEquals(2, result.physicalMemoryLayout().stringRuntime().snapshots().size());
        assertTrue(result.mlog().orElseThrow().contains("printflush message1"));
    }

    @Test
    void propagatesExactStringCapacityThroughAFunctionChain(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), """
            fun identity(value: String): String {
                return value;
            }

            fun forward(value: String): String {
                return identity(value);
            }

            val result: String = forward("MPL");
            """);

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146"));

        assertTrue(result.succeeded(), () -> result.diagnostics().toString());
        var strings = result.physicalMemoryLayout().stringRuntime();
        assertEquals(3, strings.variable("identity", "value").orElseThrow().capacity());
        assertEquals(3, strings.variable("forward", "value").orElseThrow().capacity());
        assertEquals(3, strings.variable(null, "result").orElseThrow().capacity());
        assertTrue(strings.functionResults().values().stream().allMatch(entry -> entry.capacity() == 3));
        assertTrue(strings.snapshots().values().stream().allMatch(entry -> entry.capacity() == 3));
        assertTrue(strings.callResults().values().stream().allMatch(entry -> entry.capacity() == 3));
    }

    @Test
    void usesTheLargestObservedCapacityAcrossAssignmentsAndCallSites(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), """
            fun copy(value: String): String {
                return value;
            }

            var changing: String = "A";
            changing = "ABCDE";
            val short: String = copy("XY");
            val longest: String = copy(changing);
            """);

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146"));

        assertTrue(result.succeeded(), () -> result.diagnostics().toString());
        var strings = result.physicalMemoryLayout().stringRuntime();
        assertEquals(5, strings.variable(null, "changing").orElseThrow().capacity());
        assertEquals(5, strings.variable("copy", "value").orElseThrow().capacity());
        assertEquals(5, strings.variable(null, "short").orElseThrow().capacity());
        assertEquals(5, strings.variable(null, "longest").orElseThrow().capacity());
        assertEquals(5, strings.functionResult("copy").orElseThrow().capacity());
        assertEquals(List.of(5, 5), strings.callResults().values().stream()
            .map(com.arc.mpl.memory.StringRuntimeLayout.Entry::capacity).sorted().toList());
    }

    @Test
    void keepsTargetCapacityForAnUncalledStringFunction(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), """
            fun unused(value: String): String {
                return value;
            }

            val marker: Int = 1;
            """);

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146"));

        assertTrue(result.succeeded(), () -> result.diagnostics().toString());
        var strings = result.physicalMemoryLayout().stringRuntime();
        assertEquals(400, strings.variable("unused", "value").orElseThrow().capacity());
        assertEquals(400, strings.functionResult("unused").orElseThrow().capacity());
    }

    @Test
    void includesFunctionAssignmentsToTopLevelStringCapacity(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), """
            var status: String = "A";

            fun updateStatus(): Int {
                status = "READY";
                return 0;
            }

            updateStatus();
            """);

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146"));

        assertTrue(result.succeeded(), () -> result.diagnostics().toString());
        assertEquals(5, result.physicalMemoryLayout().stringRuntime()
            .variable(null, "status").orElseThrow().capacity());
    }

    @Test
    void usesLargestStringCapacityAcrossBranchesAndLoops(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), """
            var value: String = "A";
            if (true) {
                value = "BRANCH";
            } else {
                value = "ALT";
            }
            do {
                value = "DO-WHILE";
            } while (false);
            for (var index: Int = 0; index < 1; index += 1) {
                value = "FOR-LOOP-VALUE";
            }
            """);

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146"));

        assertTrue(result.succeeded(), () -> result.diagnostics().toString());
        assertEquals(14, result.physicalMemoryLayout().stringRuntime()
            .variable(null, "value").orElseThrow().capacity());
    }

    @Test
    void rejectsStaticPrintTextThatExceedsTheTargetMessageLimit(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("hardware.mplh"),
            "const AlertBoard: Message = link(\"message1\");");
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"),
            "AlertBoard.print(\"" + "x".repeat(401) + "\");");

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146"));

        assertFalse(result.succeeded());
        assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("MPL3202")
            && diagnostic.message().contains("400")));
    }

    @Test
    void checksTheStaticMessageLimitAcrossPrintOnlyStringConcatenation(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("hardware.mplh"),
            "const AlertBoard: Message = link(\"message1\");");
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"),
            "val prefix: String = \"x\";\nAlertBoard.print(prefix + \"" + "x".repeat(400) + "\");");

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146"));

        assertFalse(result.succeeded());
        assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("MPL3202")
            && diagnostic.message().contains("401")));
    }

    @Test
    void readsAndControlsOnlyTypedDeclaredHardware(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("hardware.mplh"), """
            const LaunchSwitch: Switch = link("switch1");
            const Gun: Duo = link("duo1");
            """);
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), """
            val enabled: Bool = LaunchSwitch.enabled;
            LaunchSwitch.setEnabled(!enabled);
            Gun.shoot(12.0, 24.0, enabled);
            """);

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146"));

        assertTrue(result.succeeded());
        assertEquals("""
            _0:
            sensor __mpl_hw0 switch1 @type
            jump _0 notEqual __mpl_hw0 @switch
            sensor __mpl_hw1 duo1 @type
            jump _0 notEqual __mpl_hw1 @duo
            sensor __mpl_tmp0 switch1 @enabled
            set mpl_enabled __mpl_tmp0
            op equal __mpl_tmp1 0 mpl_enabled
            control enabled switch1 __mpl_tmp1 0 0 0
            control shoot duo1 12.0 24.0 mpl_enabled 0
            stop
            """, result.mlog().orElseThrow());
        assertEquals("""
            // 由 MPL 自动生成的 MIL；请通过 mpl build 重新生成，勿直接编辑。
            // 普通结构保留为 MIL；@unit.* 与 @io.* 是由 target profile 展开的受限宏。
            val enabled: Bool = @building.read(@switch1, enabled);
            @building.control(@switch1, enabled, (!enabled));
            @building.control(@duo1, shoot, 12.0, 24.0, enabled);
            """, result.mil().orElseThrow());
    }

    @Test
    void rejectsUndeclaredOrUnsupportedHardwareMembers(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("hardware.mplh"),
            "const LaunchSwitch: Switch = link(\"switch1\");");
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), "LaunchSwitch.shoot(1.0, 2.0, true);");

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146"));

        assertFalse(result.succeeded());
        assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("MPL3201")
            && diagnostic.message().contains("不支持控制方法")));
    }

    @Test
    void rejectsHardwareLinkTypesOutsideTheSelectedProfile(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("hardware.mplh"),
            "const Foreign: UnknownBlock = link(\"unknown1\");");
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), "var value: Int = 1;");

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146"));

        assertFalse(result.succeeded());
        assertTrue(result.mlog().isEmpty());
        assertEquals("MPL1201", result.diagnostics().get(0).code());
        assertEquals("compiler.hardware.type.unsupported", result.diagnostics().get(0).messageKey().orElseThrow());
    }

    @Test
    void keepsCompilerTemporariesSeparateFromUserVariableNames(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), "var tmp0: Int = 7;\nvar total: Int = tmp0 + 1;");

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146"));

        assertTrue(result.succeeded());
        assertEquals("set mpl_tmp0 7\nop add __mpl_tmp0 mpl_tmp0 1\nop min __mpl_tmp0 __mpl_tmp0 2147483647\nop max __mpl_tmp0 __mpl_tmp0 -2147483648\nset mpl_total __mpl_tmp0\nstop\n",
            result.mlog().orElseThrow());
    }

    @Test
    void usesReadableLabelsOnlyForAnExplicitDebugBuild(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), "while (true) { }");

        CompilationResult release = compiler.compile(new CompilationRequest(project, "v146"));
        CompilationResult debug = compiler.compile(new CompilationRequest(project, "v146", true));

        assertTrue(release.succeeded());
        assertTrue(debug.succeeded());
        assertTrue(release.mlog().orElseThrow().contains("_0:"));
        assertFalse(release.mlog().orElseThrow().contains("mpl_while_start_0"));
        assertTrue(debug.mlog().orElseThrow().contains("mpl_while_start_0:"));
        assertEquals(release.mil(), debug.mil());
        assertTrue(debug.mil().orElseThrow().contains("while (true) {"));
    }

    @Test
    void omitsTheHardwareStartupGateWhenNoHardwareIsDeclared(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), "var ready: Bool = true;");

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146"));

        assertTrue(result.succeeded());
        assertFalse(result.mlog().orElseThrow().contains("__mpl_hw"));
        assertFalse(result.mlog().orElseThrow().contains("@type"));
    }

    @Test
    void waitsAtOneSharedStartupLabelForEveryDeclaredHardwareLink(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("hardware.mplh"), """
            const Status: Message = link("message1");
            const Launch: Switch = link("switch1");
            """);
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), "Status.print(\"ready\");");

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146"));

        assertTrue(result.succeeded());
        String mlog = result.mlog().orElseThrow();
        assertTrue(mlog.startsWith("""
            _0:
            sensor __mpl_hw0 message1 @type
            jump _0 notEqual __mpl_hw0 @message
            sensor __mpl_hw1 switch1 @type
            jump _0 notEqual __mpl_hw1 @switch
            """));
        assertEquals(2, mlog.lines().filter(line -> line.startsWith("jump _0 notEqual __mpl_hw")).count());
        assertFalse(result.mil().orElseThrow().contains("__mpl_hw"));
        assertFalse(result.mil().orElseThrow().contains("hardware_wait"));
    }

    @Test
    void givesTheHardwareStartupGateAReadableDebugLabel(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("hardware.mplh"),
            "const Status: Message = link(\"message1\");");
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), "Status.print(\"ready\");");

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146", true));

        assertTrue(result.succeeded());
        assertTrue(result.mlog().orElseThrow().startsWith("mpl_hardware_wait_0:\n"));
        assertTrue(result.mlog().orElseThrow().contains(
            "jump mpl_hardware_wait_0 notEqual __mpl_hw0 @message"));
    }

    @Test
    void lowersV146UnitSetTraversalWithFiltersAndUnitControl(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), """
            while (true) {
                for (var unit : Unit.getAllDagger().where(_.health > 0.0).where(_.alive)) {
                    unit.move(Math.cos(0.0), Math.sin(0.0));
                }
            }
            """);

        // Keep the detailed lowering golden readable; release labels are
        // exercised in usesReadableLabelsOnlyForAnExplicitDebugBuild.
        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146", true));

        assertTrue(result.succeeded());
        assertEquals("""
            mpl_while_start_0:
            jump mpl_while_end_1 equal 1 0
            ubind @dagger
            jump mpl_unit_end_4 strictEqual @unit null
            set __mpl_unit_sentinel0 @unit
            mpl_unit_scan_2:
            sensor __mpl_tmp0 @unit @health
            op greaterThan __mpl_tmp1 __mpl_tmp0 0.0
            jump mpl_unit_next_3 equal __mpl_tmp1 0
            sensor __mpl_tmp2 @unit @dead
            op equal __mpl_tmp3 __mpl_tmp2 0
            jump mpl_unit_next_3 equal __mpl_tmp3 0
            op cos __mpl_tmp4 0.0 0
            op sin __mpl_tmp5 0.0 0
            ucontrol move __mpl_tmp4 __mpl_tmp5 0 0 0
            mpl_unit_next_3:
            ubind __mpl_unit_sentinel0
            jump mpl_unit_end_4 strictEqual @unit null
            sensor __mpl_tmp6 @unit @dead
            jump mpl_unit_end_4 equal __mpl_tmp6 1
            ubind @dagger
            jump mpl_unit_end_4 strictEqual @unit null
            jump mpl_unit_end_4 strictEqual @unit __mpl_unit_sentinel0
            jump mpl_unit_scan_2 always 0 0
            mpl_unit_end_4:
            jump mpl_while_start_0 always 0 0
            mpl_while_end_1:
            stop
            """, result.mlog().orElseThrow());
    }

    @Test
    void lowersManagedUnitSetTakeWithoutExposingFlagToMpl(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), """
            while (true) {
                for (var unit : Unit.getAllDagger().where(_.alive).take(3)) {
                    unit.move(10.0, 20.0);
                }
            }
            """);

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146"));

        assertTrue(result.succeeded());
        String mlog = result.mlog().orElseThrow();
        assertTrue(mlog.contains("sensor __mpl_tmp5 @unit @flag"));
        assertTrue(mlog.contains("@unit @controlled"));
        assertTrue(mlog.contains("ucontrol flag __mpl_managed_owner0 0 0 0 0"));
        assertTrue(mlog.contains("ucontrol move 10.0 20.0 0 0 0"));
    }

    @Test
    void sharesOneManagedUnitSetAcrossSizeGetAndIteration(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), """
            val squad: Set<Unit<Dagger>> = Unit.getAllDagger().where(_.alive).take(3);
            val count: Int = squad.size;
            val leader: Unit<Dagger>? = squad.get(0);
            for (var unit : squad) {
                unit.move(10.0, 20.0);
            }
            """);

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146", true));

        assertTrue(result.succeeded(), () -> result.diagnostics().toString());
        String mil = result.mil().orElseThrow();
        assertTrue(mil.contains("@unit.countManaged(@dagger, _, 3, @unit.alive(_))"));
        assertTrue(mil.contains("@unit.getManaged(@dagger, _, 0, 3, @unit.alive(_))"));
        assertTrue(mil.contains("@unit.eachManaged(@dagger, unit, 3, @unit.alive(unit))"));
        assertFalse(mil.contains("managedId"));
        assertFalse(mil.contains("flag"));

        String mlog = result.mlog().orElseThrow();
        assertTrue(mlog.contains("__mpl_managed_owner0"));
        assertFalse(mlog.contains("__mpl_managed_owner1"));
        assertTrue(mlog.contains("set mpl_count __mpl_managed_count"));
        assertTrue(mlog.contains("set mpl_leader __mpl_tmp"));
        assertTrue(mlog.contains("ucontrol move 10.0 20.0 0 0 0"));
    }

    @Test
    void savesReusesAndCountsALazyUnitSet(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), """
            val active: Set<Unit<Dagger>> = Unit.getAllDagger().where(_.alive);
            val healthy = active.where(_.health > 0.0);
            var count: Int = healthy.size;
            for (var unit : healthy) {
                unit.move(4.0, 8.0);
            }
            """);

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146", true));

        assertTrue(result.succeeded());
        String mil = result.mil().orElseThrow();
        assertTrue(mil.contains("val active: Set<Unit<Dagger>> = Unit.getAllDagger().where(_ => @unit.alive(_));"));
        assertTrue(mil.contains("val healthy: Set<Unit<Dagger>> = Unit.getAllDagger()"));
        assertTrue(mil.contains("var count: Int = @unit.count(@dagger, _, @unit.alive(_),"));
        assertTrue(mil.contains("@unit.each(@dagger, unit, @unit.alive(unit),"));
        String mlog = result.mlog().orElseThrow();
        assertFalse(mlog.contains("set mpl_active"));
        assertFalse(mlog.contains("set mpl_healthy"));
        assertTrue(mlog.contains("set __mpl_tmp0 0"));
        assertTrue(mlog.contains("mpl_unit_count_scan_0:"));
        assertTrue(mlog.contains("op add __mpl_tmp0 __mpl_tmp0 1"));
        assertTrue(mlog.contains("set mpl_count __mpl_tmp0"));
        assertTrue(mlog.contains("ucontrol move 4.0 8.0 0 0 0"));
    }

    @Test
    void getsAndRebindsAPersistentNullableUnitReference(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), """
            val active = Unit.getAllDagger().where(_.alive);
            val leader: Unit<Dagger>? = active.get(0);
            if (leader != null) {
                val health = leader.health;
                leader.move(4.0, 8.0);
            }
            """);

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146", true));

        assertTrue(result.succeeded(), () -> result.diagnostics().toString());
        String mil = result.mil().orElseThrow();
        assertTrue(mil.contains("val leader: Unit<Dagger>? = @unit.get(@dagger, _, 0, @unit.alive(_));"));
        assertTrue(mil.contains("val health: Float = @unit.refRead(leader, health);"));
        assertTrue(mil.contains("@unit.refMove(leader, 4.0, 8.0);"));
        String mlog = result.mlog().orElseThrow();
        assertTrue(mlog.contains("set __mpl_tmp0 null"));
        assertTrue(mlog.contains("mpl_unit_get_scan_0:"));
        assertTrue(mlog.contains("set mpl_leader __mpl_tmp0"));
        assertTrue(mlog.contains("ubind mpl_leader"));
        assertTrue(mlog.lines().filter(line -> line.contains("@unit @dead")).count() >= 2);
        assertTrue(mlog.contains("sensor __mpl_tmp"));
        assertTrue(mlog.contains("ucontrol move 4.0 8.0 0 0 0"));
        assertTrue(mlog.lines().anyMatch(line -> line.matches("jump mpl_[A-Za-z0-9_]+ strictEqual @unit null")));
    }

    @Test
    void compilesProvenDynamicArraysThroughOnePhysicalMemoryLayout(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), """
            var values: Int[] = [1, 2, 3];
            for (var i: Int = 0; i < values.size; i += 1) {
                values.set(i, values[i] + 1);
            }
            """);

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146"));

        assertTrue(result.succeeded());
        assertEquals(3, result.physicalMemoryLayout().physicalSlots());
        assertEquals(1, result.physicalMemoryLayout().memoryBanks());
        assertTrue(result.mil().orElseThrow().contains("values.set(i, (values[i] + 1));"));
        String mlog = result.mlog().orElseThrow();
        assertTrue(mlog.contains("write 1 bank1 0"));
        assertTrue(mlog.lines().anyMatch(line -> line.matches("read __mpl_tmp\\d+ bank1 mpl_i")));
        assertTrue(mlog.lines().anyMatch(line -> line.matches("write __mpl_tmp\\d+ bank1 __mpl_tmp\\d+")));
        assertFalse(mlog.contains("mpl_values_e"));
    }

    @Test
    void reportsUnsatisfiedRuntimeMemoryLimitsAsACompilationDiagnostic(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        String elements = java.util.stream.IntStream.range(0, 65).mapToObj(Integer::toString)
            .collect(java.util.stream.Collectors.joining(", "));
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), """
            var values: Int[] = [%s];
            for (var i: Int = 0; i < values.size; i += 1) {
                values.set(i, values[i]);
            }
            """.formatted(elements));
        java.nio.file.Files.writeString(project.resolve("mpl.json"), """
            { "runtime": { "memory": { "cell": 1 } } }
            """);

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146"));

        assertFalse(result.succeeded());
        assertEquals("MPL1301", result.diagnostics().get(0).code());
        assertTrue(result.diagnostics().get(0).message().contains("65 个物理槽"));
        assertTrue(result.mlog().isEmpty());
    }

    @Test
    void rejectsGeneratedMlogThatExceedsTheTargetInstructionLimit(@TempDir Path project) throws IOException {
        Path sourceDirectory = java.nio.file.Files.createDirectories(project.resolve("src"));
        StringBuilder source = new StringBuilder();
        for (int index = 0; index < 1_000; index++) {
            source.append("var value").append(index).append(": Int = 0;\n");
        }
        java.nio.file.Files.writeString(sourceDirectory.resolve("main.mpl"), source);

        CompilationResult result = compiler.compile(new CompilationRequest(project, "v146"));

        assertFalse(result.succeeded());
        assertTrue(result.mlog().isEmpty());
        assertTrue(result.mil().isEmpty());
        assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> "MPL5001".equals(diagnostic.code())));
    }
}
