import type { TutorialLesson } from "../types/tutorial";

export const milLessons: TutorialLesson[] = [
  {
    id: "mil-boundary", track: "mil", number: "01", title: "MIL 的位置与边界",
    summary: "理解为何中间语言仍保留高级结构。", keywords: ["MPL", "MIL", "mlog", "受限宏"],
    sections: [
      { title: "不是完全展开的汇编", paragraphs: ["MIL 位于 MPL 与 mlog 之间。它保留变量、函数、类和结构化控制流，只把 Unit、Building、I/O 与 Runtime 糖展开为 profile 白名单中的受限宏。"], terms: ["MIL", "MPL", "mlog", "Runtime 糖", "受限宏"], code: { language: "mil", source: "var phase: Float = 0.0;\nwhile (true) {\n  @unit.eachManaged(@dagger, unit, 3, @unit.alive(unit)) {\n    @unit.move(unit, phase, 20.0);\n  }\n}" } },
      { title: "面向性能用户的稳定入口", paragraphs: ["src 可同时包含 .mpl 和 .mil；手写 MIL 能绕过 MPL 的部分对象 Runtime，但不能自定义宏、访问原始 Memory、读写 flag 或跳过硬件权限检查。"], terms: [".mil", "自定义宏", "Memory", "flag", "权限检查"], callout: { tone: "info", title: "分层原则", text: "MPL 像高级语言，MIL 像受验证的结构化目标层；两者都不是任意 mlog 文本通道。" } }
    ]
  },
  {
    id: "mil-syntax", track: "mil", number: "02", title: "共享语法与宏调用",
    summary: "在熟悉的语法中使用 @namespace.action。", keywords: ["@namespace.action", "宏块", "@gameSymbol"],
    sections: [
      { title: "MIL 继承 MPL 的基本结构", paragraphs: ["var、val、fun、class、if、for、while、import 与 export 在 MIL 中含义一致。新增的 @namespace.action(...) 是宏调用，带循环体的遍历宏使用宏块语法。"], terms: ["var", "val", "fun", "class", "@namespace.action(...)", "宏块"], code: { language: "mil", source: "fun announce(value: Int) {\n  if (value > 0) {\n    @io.print(@message1, \"value=\", value);\n  }\n}" } },
      { title: "游戏符号不是普通变量", paragraphs: ["@message1、@display1、@dagger 等符号由硬件契约或 target profile 验证。MIL 变量不能伪造这些对象，也不能依赖 mlog 的对象/数字隐式转换。"], terms: ["@message1", "@display1", "@dagger", "target profile", "隐式转换"] }
    ]
  },
  {
    id: "mil-profile", track: "mil", number: "03", title: "Profile、白名单与诊断",
    summary: "让同一份结构按 v146 或 v159.7 安全 lowering。", keywords: ["v146", "v159.7", "宏白名单", "runtimePrivate"],
    sections: [
      { title: "宏能力来自 target", paragraphs: ["每次构建选择一个 profile。profile 登记指令签名、处理器限制、内容类型、公开宏、效果和成本；未知宏或参数数量不匹配在 MIL 解析阶段失败。"], terms: ["profile", "公开宏", "效果", "成本"], code: { language: "shell", source: "mpl check --target=v146\nmpl build --target=v159.7 build-v159" } },
      { title: "公开与 Runtime 私有", paragraphs: ["public 宏可写入用户 MIL；runtimePrivate 宏只允许编译器生成。当前 @io.drawFlush 是典型私有提交点，MPL 与手写 MIL 都不能直接控制刷新。"], terms: ["public", "runtimePrivate", "@io.drawFlush"], callout: { tone: "warning", title: "版本优化不改变语义", text: "v159.7 可以选择更优指令序列，但不能让同一程序在不同 target 下产生不同的源级行为。" } }
    ]
  },
  {
    id: "mil-unit", track: "mil", number: "04", title: "Unit 宏族",
    summary: "展开扫描、计数、持久引用和受管编队。", keywords: ["@unit.each", "@unit.get", "@unit.refMove", "@unit.eachManaged"],
    sections: [
      { title: "普通查询", paragraphs: ["@unit.each 展开弱一致扫描；@unit.count 与 @unit.get 分别实现 Set.size 和 Set.get。过滤条件作为宏参数传入，读取字段使用 @unit.read 或 @unit.alive。"], terms: ["@unit.each", "@unit.count", "@unit.get", "@unit.read", "@unit.alive"], code: { language: "mil", source: "val leader: Unit<Dagger>? =\n  @unit.get(@dagger, unit, 0, @unit.alive(unit));\nif (leader != null) {\n  val health: Float = @unit.refRead(leader, health);\n  @unit.refMove(leader, 40.0, 20.0);\n}" } },
      { title: "受管 take", paragraphs: ["@unit.eachManaged、countManaged 与 getManaged 携带固定上限，Runtime 用私有 flag owner 集合维持成员。宏调用者仍不能读取或写入 flag。"], terms: ["@unit.eachManaged", "countManaged", "getManaged", "flag"], code: { language: "mil", source: "@unit.eachManaged(@dagger, unit, 3, @unit.alive(unit)) {\n  @unit.move(unit, 20.0, 20.0);\n}" } }
    ]
  },
  {
    id: "mil-building", track: "mil", number: "05", title: "Building 与硬件宏",
    summary: "读取链接建筑并执行 profile 允许的控制动作。", keywords: ["@building.each", "@building.read", "@building.control"],
    sections: [
      { title: "链接集合", paragraphs: ["@building.each、count 与 get 只在 .mplh 已声明且类型匹配的链接集合上工作。重新连接时按游戏 alias 恢复，不依赖易变的 link 序号。"], terms: ["@building.each", ".mplh", "游戏 alias", "link 序号"], code: { language: "mil", source: "@building.each(@duo, turret, @building.read(turret, enabled)) {\n  @building.control(turret, enabled, false);\n}" } },
      { title: "动作按类型授权", paragraphs: ["@building.read 对应 sensor 白名单；@building.control 对应具体建筑支持的控制动作。错误字段、只读硬件写入或不属于当前 shard 的硬件都会在构建期失败。"], terms: ["@building.read", "@building.control", "sensor", "shard"] }
    ]
  },
  {
    id: "mil-io", track: "mil", number: "06", title: "I/O、绘制与自动刷新",
    summary: "查看 Message 与 Display 对象操作如何展开。", keywords: ["@io.print", "@io.draw", "@io.drawFlush"],
    sections: [
      { title: "文本直接流向 Message", paragraphs: ["@io.print 的第一个参数必须是声明过的 Message game symbol，其余参数按顺序输出。它可以绕过 MPL String 中间值，但仍受 400 UTF-16 代码单元上限检查。"], terms: ["@io.print", "Message", "game symbol", "400 UTF-16"], code: { language: "mil", source: "@io.print(@message1, \"active=\", count, \" time=\", time);" } },
      { title: "绘图命令是显式宏，提交仍私有", paragraphs: ["@io.draw 记录 clear、color、rect、line 等 profile 操作；组合屏会展开为每个物理 tile 的坐标变换。生成的 MIL 含 @io.drawFlush，但用户手写 MIL 不得调用它。"], terms: ["@io.draw", "组合屏", "@io.drawFlush"], code: { language: "mil", source: "@io.draw(@display1, clear, 0, 0, 0);\n@io.draw(@display1, color, 0, 255, 0, 255);\n@io.draw(@display1, rect, 8, 8, 40, 20);\n@io.drawFlush(@display1);" } }
    ]
  },
  {
    id: "mil-control", track: "mil", number: "07", title: "控制流、函数 ABI 与标签",
    summary: "保留可读结构，交给 lowering 生成紧凑跳转。", keywords: ["@counter", "label", "debug", "stop"],
    sections: [
      { title: "结构化语句保持不变", paragraphs: ["MIL 的 if、while、for、break、continue 和 return 仍是结构化语法。lowering 使用 jump 与 @counter 实现分支、循环和函数返回，用户无需手写跳转地址。"], terms: ["if", "while", "for", "return", "jump", "@counter"], code: { language: "mil", source: "fun advance(value: Int): Int {\n  return value + 1;\n}\nwhile (true) { phase = advance(phase); }" } },
      { title: "标签只在目标代码出现", paragraphs: ["debug 构建保留可读完整标签，正式构建使用 _0、_1 等短标签。label 不计入 1000 条处理器指令上限，但仍受 profile 的标签数量限制。"], terms: ["debug", "_0", "label", "1000 条处理器指令"] }
    ]
  },
  {
    id: "mil-runtime", track: "mil", number: "08", title: "Runtime 展开与 Memory 边界",
    summary: "审计运行时节点，但不直接操作存储地址。", keywords: ["对象池", "字符串表", "共享邮箱", "Memory"],
    sections: [
      { title: "MIL 仍不提供 Memory API", paragraphs: ["对象池、字符串序列、动态聚合与 Worker 邮箱由编译器布局。MIL 可以看见与行为相关的 Runtime 宏和结构，但不能 read/write 任意地址，也不能声明 Cell 或 Bank。"], terms: ["对象池", "字符串序列", "Worker 邮箱", "Memory", "Cell", "Bank"], callout: { tone: "info", title: "为什么保持封装", text: "包、MPL 与手写 MIL 共用一个全局布局器，隐藏地址才能进行槽位复用、回收和多处理器连接优化。" } },
      { title: "多处理器是构建拓扑", paragraphs: ["纯数值函数经过效果分析后可进入 Worker shard。Main 与 Worker 通过公共物理 Memory 的单生产者/单消费者邮箱交互，各自的虚拟变量保持私有。"], terms: ["效果分析", "Worker shard", "公共物理 Memory", "虚拟变量"] }
    ]
  },
  {
    id: "mil-handwritten", track: "mil", number: "09", title: "手写 MIL 与构建产物",
    summary: "把 MIL 作为包模块或入口编译，并对照最终 mlog。", keywords: ["main.mil", "profile 校验", "source map", "构建报告"],
    sections: [
      { title: "把 MIL 放进 src", paragraphs: ["mpl.json.entry 可以指向 main.mil，MPL 模块也能导入 MIL 的 export。所有手写和生成 MIL 都经过相同语法、类型、硬件、profile 与资源校验。"], terms: ["main.mil", "export", "profile", "资源校验"], code: { language: "mil", title: "src/output.mil", source: "export fun announce(value: Int) {\n  @io.print(@message1, \"value=\", value);\n}" } },
      { title: "三层一起审计", paragraphs: ["构建目录同时保留 .mil、.mlog、report.json 与 deployment.json。定位问题时先看 MIL 是否正确表达目标能力，再看 mlog 指令成本和报告中的处理器、Memory、硬件所有权。"], terms: [".mil", ".mlog", "report.json", "deployment.json"], code: { language: "shell", source: "mpl check --target=v146\nmpl build --target=v146 build" }, callout: { tone: "warning", title: "宏卫生", text: "用户不能定义宏；未知 @ 前缀、runtimePrivate 宏和保留 flag 访问都会被拒绝。" } }
    ]
  }
];
