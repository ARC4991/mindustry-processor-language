import type { TutorialLesson } from "../types/tutorial";

export const mplLessons: TutorialLesson[] = [
  {
    id: "mpl-project", track: "mpl", number: "01", title: "项目与第一份蓝图",
    summary: "认识项目结构、入口文件和当前目录 CLI。", keywords: ["mpl init", "main.mpl", "hardware.mplh", "v146"],
    sections: [
      { title: "从当前目录开始", paragraphs: ["mpl init 在当前目录创建项目；main.mpl 是顺序执行的顶层入口，不需要 main 函数，也不会隐式循环。程序自然结束时编译器生成 stop。"], terms: ["mpl init", "main.mpl", "stop"], code: { language: "shell", title: "终端", source: "mkdir dagger-orbit\ncd dagger-orbit\nmpl init\nmpl check --target=v146\nmpl build --target=v146" } },
      { title: "最小目录", paragraphs: ["mpl.json 固定项目名、版本、入口和目标版本。src/hardware.mplh 只声明蓝图外硬件；处理器与 Memory 由编译器根据代码自动规划。"], terms: ["mpl.json", "src/hardware.mplh", "Memory"], code: { language: "json", title: "mpl.json", source: "{\n  \"schemaVersion\": 1,\n  \"name\": \"dagger-orbit\",\n  \"version\": \"0.1.0\",\n  \"target\": { \"mindustry\": \"v146\" },\n  \"entry\": \"src/main.mpl\",\n  \"hardware\": \"src/hardware.mplh\"\n}" } }
    ]
  },
  {
    id: "mpl-bindings", track: "mpl", number: "02", title: "变量与类型推导",
    summary: "使用 val、var 与初始化器推导减少重复类型。", keywords: ["val", "var", "类型推导", "大驼峰"],
    sections: [
      { title: "不可变与可变绑定", paragraphs: ["val 初始化后不可重新赋值，var 允许赋值。两者都必须有初始化器；编译器从初始化表达式推导变量类型。类型名严格使用大驼峰。"], terms: ["val", "var", "类型推导", "大驼峰"], code: { language: "mpl", source: "val limit = 3;           // Int\nvar phase = 0.0;         // Float\nval enabled = true;      // Bool\nval title = \"Orbit\";  // String" } },
      { title: "需要显式类型的地方", paragraphs: ["公开 API、空容器和可空对象应写出类型。显式标注是约束，不是转换；Float 不能悄悄赋给 Int。"], terms: ["公开 API", "可空对象", "Float", "Int"], code: { language: "mpl", source: "val empty: Int[] = [];\nval leader: Unit<Dagger>? = Unit.getAllDagger().get(0);\nvar distance: Float = 3; // Int 安全提升为 Float" } }
    ]
  },
  {
    id: "mpl-values", track: "mpl", number: "03", title: "基础类型与运算",
    summary: "理解 Int、Float、Bool、String 的严格边界。", keywords: ["Int", "Float", "Bool", "String"],
    sections: [
      { title: "数值不是同一种类型", paragraphs: ["Int 是有符号 32 位整数；Float 映射目标 double。Int 可以提升到 Float，反向必须显式转换。Int 除法得到 Float，Bool 不参与数值运算。"], terms: ["Int", "Float", "Bool", "32 位"], code: { language: "mpl", source: "val count: Int = 3;\nval radius: Float = count + 20.5;\nval half: Float = count / 2;\nval index: Int = Int.floor(radius);" } },
      { title: "字符串是运行时值", paragraphs: ["String 支持赋值、参数、返回、比较和拼接。编译器计算每个字符串的 UTF-16 上界并分配物理 Memory；print 可以直接流式输出拼接结果。"], terms: ["String", "UTF-16", "物理 Memory", "print"], code: { language: "mpl", source: "val prefix = \"units=\";\nvar message = prefix + \"3\";\nStatus.print(message);" } }
    ]
  },
  {
    id: "mpl-tuples-arrays", track: "mpl", number: "04", title: "元组与数组推导",
    summary: "表达固定结构和同类型连续元素。", keywords: ["元组", "数组", "Int[]", "静态下标"],
    sections: [
      { title: "元组保留每个位置的类型", paragraphs: ["元组可以包含不同类型，省略声明类型时从每个元素推导。异构元组使用常量下标读取，不能直接 for。"], terms: ["元组", "常量下标", "for"], code: { language: "mpl", source: "val spawn = (24.0, 48.0, true); // (Float, Float, Bool)\nval x = spawn[0];               // Float\nval active = spawn[2];          // Bool" } },
      { title: "数组会求共同元素类型", paragraphs: ["数组字面量的元素必须能归一到同一类型。Int 与 Float 混合时提升为 Float[]；空数组没有推导依据，必须显式标注。已知长度数组可在新声明中按值复制。"], terms: ["数组字面量", "Float[]", "空数组", "按值复制"], code: { language: "mpl", source: "val ids = [1, 2, 3];       // Int[]\nval copy = ids;            // Int[]\nval points = [1, 2.5, 3];  // Float[]\nval empty: Int[] = [];" } }
    ]
  },
  {
    id: "mpl-collections", track: "mpl", number: "05", title: "List、Set 与 MutableList",
    summary: "选择有序、去重或受容量约束的容器。", keywords: ["List.of", "Set.of", "MutableList"],
    sections: [
      { title: "统一工厂 API", paragraphs: ["List 保留顺序，Set 表达不重复成员；两者使用 List.of 与 Set.of 创建。Unit 和 Building 查询也公开为普通 Set 类型。"], terms: ["List", "Set", "List.of", "Set.of"], code: { language: "mpl", source: "val route = List.of(4, 8, 15, 16);\nval allowed = Set.of(1, 3, 5);\nfor (var value : route) { Status.print(value); }" } },
      { title: "容量先于运行时", paragraphs: ["MutableList 必须给出编译期容量。add、clear 和动态下标只在编译器能证明容量与边界的路径上开放，Memory 地址不会暴露给代码。"], terms: ["MutableList", "编译期容量", "Memory"], code: { language: "mpl", source: "var queue: MutableList<Int> = MutableList.withCapacity(8);\nqueue.add(10);\nqueue.add(20);\nval first = queue.get(0);" }, callout: { tone: "warning", title: "当前限制", text: "可变 Set、嵌套聚合和一般运行时下标尚未开放；无法证明边界的访问会在编译期失败。" } }
    ]
  },
  {
    id: "mpl-control-flow", track: "mpl", number: "06", title: "条件与循环",
    summary: "使用熟悉的结构化控制流表达持续逻辑。", keywords: ["if", "while", "for", "stop"],
    sections: [
      { title: "控制流保持传统语义", paragraphs: ["if、while、do while、计数 for、break 和 continue 都由编译器降级为跳转。逻辑需要持续运行时显式写 while；顶层代码不会自动重启。"], terms: ["if", "while", "do while", "for", "break", "continue"], code: { language: "mpl", source: "var tick = 0;\nwhile (true) {\n  if (tick >= 60) { tick = 0; }\n  tick += 1;\n}" } },
      { title: "跨 tick 不是协程", paragraphs: ["游戏处理器按 IPT 连续执行指令，超出一个 tick 的程序会从当前指令继续。MPL 保持顺序语义，不提供隐式主循环或用户协程。"], terms: ["IPT", "跨 tick", "顺序语义"], callout: { tone: "info", title: "执行模型", text: "程序末尾默认 stop；持续循环由源码明确表达，因此读代码即可判断是否持续运行。" } }
    ]
  },
  {
    id: "mpl-functions", track: "mpl", number: "07", title: "函数、返回推导与后置声明",
    summary: "组织逻辑并让函数先使用、后声明。", keywords: ["fun", "return", "后置声明", "聚合 ABI"],
    sections: [
      { title: "返回类型也能推导", paragraphs: ["函数参数必须标注类型；返回类型可从所有 return 路径推导。函数和类支持后置声明，调用点不受源码排列顺序限制。顶层函数和对象方法可传入并返回数值/Bool 元组；顶层函数还支持固定形状数组。"], terms: ["fun", "return", "返回类型", "后置声明", "Int[]", "元组", "数组"], code: { language: "mpl", source: "val rotated = rotate([1, 2, 3]); // Int[]\n\nfun rotate(values: Int[]) {\n  return [values[2], values[0], values[1]];\n}" } },
      { title: "目标层 ABI", paragraphs: ["函数最终使用 @counter 间接跳转和编译器私有返回槽。元组与数组都按元素进入独立参数与结果槽；对象方法额外把隐藏 this 放在第一个槽。调用前先快照所有元素，虚方法和 super 调用返回后也会立即快照。生成的 MIL 保留原签名并可再次编译。"], terms: ["@counter", "元组", "数组", "this", "MIL", "快照", "编译器私有返回槽"], callout: { tone: "info", title: "编译期形状", text: "T[] 的长度不是公开类型的一部分。编译器沿调用图推导一个函数 ABI 的固定长度；当前同一函数的不同长度调用会在编译期拒绝。" } }
    ]
  },
  {
    id: "mpl-nullability", track: "mpl", number: "08", title: "可空对象与流收窄",
    summary: "默认非空，只在查询可能失败时显式处理 null。", keywords: ["Type?", "null", "val 收窄"],
    sections: [
      { title: "默认不可空", paragraphs: ["只有对象引用能写 Type?。Int?、Float? 与 Bool? 不存在；new 永远返回非空对象。Set.get 可能找不到成员，因此返回可空 Unit 或 Building。"], terms: ["Type?", "Int?", "new", "Set.get"], code: { language: "mpl", source: "val leader: Unit<Dagger>? = Unit.getAllDagger().get(0);\nif (leader != null) {\n  leader.move(40.0, 20.0);\n}" } },
      { title: "稳定 val 才能收窄", paragraphs: ["编译器在 null 判断分支中把稳定 val 收窄为非空类型；var 可能被改写，因此当前不收窄。安全调用 ?.、Elvis ?: 和 !! 尚未进入可用语法。"], terms: ["val", "null", "?.", "?:", "!!"], callout: { tone: "warning", title: "尚未实现", text: "请使用显式 if 判空；教程不会把规划中的 ?. 或 ?: 写成可用功能。" } }
    ]
  },
  {
    id: "mpl-classes", track: "mpl", number: "09", title: "类、字段与 new",
    summary: "用简单对象封装状态和行为。", keywords: ["class", "new", "this", "构造器"],
    sections: [
      { title: "构造器与字段", paragraphs: ["构造函数与类同名，所有字段必须在构造结束前初始化。new 表达对象身份；字段布局与生命周期由编译器统一安排。"], terms: ["class", "构造函数", "new", "字段布局"], code: { language: "mpl", source: "class Counter {\n  private value: Int;\n  public fun Counter(initial: Int) { this.value = initial; }\n  public fun add(amount: Int): Int { return this.value + amount; }\n}\nval counter = new Counter(1);" } },
      { title: "没有 delete 和 GC", paragraphs: ["顶层对象长期存活；可证明不逃逸的局部对象复用固定槽；唯一所有权工厂使用对象池并在作用域退出时自动回收。源码不操作 Memory，也没有 delete。"], terms: ["唯一所有权", "对象池", "Memory", "delete"], callout: { tone: "info", title: "可证明生命周期", text: "局部对象不能任意返回、别名或放入容器；违反所有权约束是编译错误。" } }
    ]
  },
  {
    id: "mpl-inheritance", track: "mpl", number: "10", title: "继承、访问控制与重载",
    summary: "使用单继承和签名重载扩展对象行为。", keywords: ["extends", "super", "public", "private"],
    sections: [
      { title: "单继承与动态派发", paragraphs: ["类最多 extends 一个父类。派生构造器先调用 super；public 方法可覆盖并按运行时对象类型派发，super.method 静态调用父类实现。"], terms: ["extends", "super", "public", "动态派发"], code: { language: "mpl", source: "class FastCounter extends Counter {\n  private bonus: Int;\n  public fun FastCounter(value: Int, bonus: Int) {\n    super(value);\n    this.bonus = bonus;\n  }\n  public fun add(amount: Int): Int {\n    return super.add(amount) + this.bonus;\n  }\n}" } },
      { title: "按签名选择最具体重载", paragraphs: ["顶层函数、构造器和方法可按参数数量与类型重载。编译器选择唯一最具体候选；二义性、不可见 private 成员和不兼容覆盖都会失败。"], terms: ["重载", "最具体候选", "private"], callout: { tone: "warning", title: "有意保持简单", text: "第一版没有多继承、interface、protected、反射或用户泛型。" } }
    ]
  },
  {
    id: "mpl-modules", track: "mpl", number: "11", title: "模块、export 与包",
    summary: "组合 MPL/MIL 模块并锁定外部依赖。", keywords: ["import", "export", "with", "mpl.lock"],
    sections: [
      { title: "模块默认私有", paragraphs: ["import 必须位于模块顶部；只有 export class、export fun 和 export val 能被其它模块导入。相对模块可以是 .mpl 或 .mil。"], terms: ["import", "export class", "export fun", "export val", ".mil"], code: { language: "mpl", source: "import { distance } from \"./geometry\";\nexport fun radius(x: Float, y: Float): Float {\n  return distance(x, y);\n}" } },
      { title: "硬件在导入点注入", paragraphs: ["外部包在自己的 .mplh 声明硬件 require，应用使用 with 严格传入同名硬件常量。mpl install 解析依赖并写 mpl.lock；check/build 不隐式更新网络状态。"], terms: ["require", "with", "mpl install", "mpl.lock"], code: { language: "mpl", source: "import { Dashboard } from \"@mpl/dashboard\" with {\n  screen: MainScreen,\n  status: Status\n};" } }
    ]
  },
  {
    id: "mpl-hardware", track: "mpl", number: "12", title: ".mplh 硬件契约",
    summary: "用名称绑定外部建筑，用组合屏满足逻辑尺寸。", keywords: ["link", "Display.combine", "require", ".mplh"],
    sections: [
      { title: "游戏 alias 与 MPL 名称分层", paragraphs: ["link 只允许出现在 .mplh。MainStatus 是 MPL 硬件常量，message1 是游戏处理器的链接 alias；两者不会混入普通变量命名空间。"], terms: ["link", ".mplh", "硬件常量", "链接 alias"], code: { language: "mplh", title: "src/hardware.mplh", source: "const MainStatus: Message = link(\"message1\");\nconst Left: Display = link(\"display1\", width: 80, height: 80);\nconst Right: Display = link(\"display2\", width: 80, height: 80);\nconst MainScreen: Display = Display.combine([[Left, Right]]);" } },
      { title: "包只描述能力需求", paragraphs: ["包端 require 描述名称、类型、读写能力和最小尺寸，不能指定处理器、Memory 或 alias。组合 Display 按逻辑尺寸匹配，因此多个小屏可满足一个大屏需求。"], terms: ["require", "最小尺寸", "组合 Display", "Memory"], code: { language: "mplh", title: "包 hardware.mplh", source: "require screen: Display(\n  access: write,\n  minWidth: 160,\n  minHeight: 80\n);" } }
    ]
  },
  {
    id: "mpl-unit-building", track: "mpl", number: "13", title: "Unit 与 Building 对象化控制",
    summary: "查询普通 Set，串联 where，再直接操作对象。", keywords: ["Unit.getAllDagger", "Building.getAllDuo", "where", "take"],
    sections: [
      { title: "查询返回通用 Set", paragraphs: ["Unit.getAllDagger 返回 Set<Unit<Dagger>>，where 可连续串联；size、get 与 for 观察同一查询计划。普通查询弱一致，不承诺快照或稳定顺序。"], terms: ["Unit.getAllDagger", "Set<Unit<Dagger>>", "where", "size", "get", "for"], code: { language: "mpl", source: "val ready = Unit.getAllDagger()\n  .where(unit => unit.alive)\n  .where(unit => unit.ammo > 0.0);\nval count = ready.size;\nfor (var unit : ready) { unit.move(40.0, 20.0); }" } },
      { title: "take 使用隐藏 flag 固定成员", paragraphs: ["take(3) 让 Runtime 私有认领最多三只匹配单位；flag、bind、ID 和扫描游标都不对 MPL/MIL 开放。Building 查询来自已声明链接，可在重新连接后按 alias 恢复。"], terms: ["take(3)", "Runtime", "flag", "Building"], code: { language: "mpl", source: "val squad = Unit.getAllDagger()\n  .where(_.alive)\n  .take(3);\nval turrets = Building.getAllDuo().where(_.enabled);" }, callout: { tone: "warning", title: "Unit 上下文限制", text: "当前禁止嵌套 Unit 遍历，也不允许 UnitRef 作为函数参数、返回值或用户对象字段。" } }
    ]
  },
  {
    id: "mpl-io", track: "mpl", number: "14", title: "文本与绘制",
    summary: "以对象接口输出文本和绘图，刷新由 Runtime 管理。", keywords: ["Message.print", "Display", "自动 flush", "Color"],
    sections: [
      { title: "print 自动连接并刷新", paragraphs: ["Message.print 接受多个值，按顺序拼接并在调用结尾提交。MPL 用户看不到 printflush，也不需要维护字符跳转表。"], terms: ["Message.print", "printflush", "字符跳转表"], code: { language: "mpl", source: "Status.print(\"active=\", squad.size, \" time=\", Clock.time, \"s\");" } },
      { title: "Canvas 风格绘制子集", paragraphs: ["Display 提供 clear、fill、stroke、fillRect、strokeRect 与 line。绘制可放进循环；Runtime 在循环回边、分支退出、函数结束和缓冲上限前自动提交。"], terms: ["Display", "clear", "fillRect", "Runtime", "自动提交"], code: { language: "mpl", source: "while (true) {\n  MainScreen.clear(Color.black);\n  MainScreen.fill(Color.green);\n  MainScreen.fillRect(x, 8, 12, 12);\n  x = (x + 1) % MainScreen.width;\n}" } }
    ]
  },
  {
    id: "mpl-build", track: "mpl", number: "15", title: "Runtime、构建与部署",
    summary: "理解自动资源规划、多处理器分片和最终产物。", keywords: ["runtime.goal", "1000 条指令", "runtime.msch", "report.json"],
    sections: [
      { title: "编译器按实际需求规划", paragraphs: ["处理器变量承载虚拟槽，Cell/Bank 承载对象池、字符串、动态聚合和跨处理器邮箱。runtime.goal 在 minResources 与 maxPerformance 之间选择策略；用户只限制可用处理器和 Memory 类型数量。"], terms: ["Cell/Bank", "对象池", "runtime.goal", "minResources", "maxPerformance"], code: { language: "json", title: "mpl.json 资源偏好", source: "{\n  \"runtime\": {\n    \"goal\": \"minResources\",\n    \"processors\": { \"micro\": 8 },\n    \"memory\": { \"cell\": 8, \"bank\": 4 }\n  }\n}" } },
      { title: "蓝图是正式结果", paragraphs: ["每个处理器最多 1000 条真实指令，label 不计入该上限。超限时编译器优化或把可证明纯数值函数分配给 Worker；最终蓝图包含处理器与已连接 Memory。MIL、mlog、格式化 JSON 报告和连接说明放在构建目录供审计。"], terms: ["1000 条真实指令", "label", "Worker", "蓝图", "MIL", "mlog"], code: { language: "shell", source: "mpl build --target=v146 build\n# build/runtime.msch\n# build/Main.mil / Main.mlog\n# build/report.json / deployment.json\n# build/连接说明.txt" } }
    ]
  }
];
