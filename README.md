# mindustry-processor-language

MPL（Mindustry Processor Language）是一个面向 Mindustry 游戏逻辑处理器的高级语言与编译器项目。它先将 MPL 源码降级为支持 profile 宏的 MIL（Macro Intermediate Language），再生成游戏可执行的 mlog 指令。MIL 保留变量、表达式、函数和结构化控制流等 MPL 基础语法，只展开 Unit Set/Unit/Building、字符串运行时等高级糖及其运行时需求。

项目目前处于原型实现阶段，尚未提供稳定的编译器版本。编译器使用 Java（JDK 17），MPL 与 MIL 均使用拆分的 ANTLR 4 词法/语法文件；任何标记为“讨论基线”的语法仍可能调整。

## 快速试用

当前原型支持初始化项目、基础数值/控制流、项目内及锁定工作区包的 MPL/MIL 混合模块、无继承 class/new、Message/Display I/O，以及 Unit/Building 对象查询。顶层 `new` 使用编译器管理的静态对象槽；函数和循环内可证明不逃逸的局部 `val` 按分配点复用固定槽。构造器、public/private、可空用户对象和实例方法已经接通 MIL/mlog。`Set<Unit<T>>` 与 `LinkedBuildingSet<T>` 均可保存、过滤、计数、索引和遍历；可空 Unit/Building 引用在判空后可读取和控制。需要 JDK 17：

```bash
# 默认使用 v146；目录必须不存在或为空。
./gradlew run --args='init my-mpl-project'

# 依赖非空时先生成确定性的 mpl.lock。
./gradlew run --args='install my-mpl-project'

# 生成蓝图、Main.mlog、Main.mil 及格式化的构建清单。
./gradlew run --args='build --target=v146 my-mpl-project my-mpl-project/build'
```

`mpl.json` 的 `entry` 可指向 `src` 下的 `.mpl` 或 `.mil`。入口可用 `import { name } from "./module";` 递归链接 `src` 内显式 `export class` / `export fun` / `export val` 的 MPL 或 MIL 模块；依赖顶层初始化只执行一次，私有符号由链接器隔离。`workspace:` 依赖由 `mpl install` 递归锁定到格式化的 `mpl.lock`，`check/build` 会验证根清单、包源码、`.mplh` 摘要、单版本约束和 target 能力，绝不自动更新锁文件。包可在自己的 `.mplh` 中用 `require` 声明命名硬件，并由调用方通过 `with` 严格注入；组合 Display 的尺寸约束也会在链接期验证。registry 下载尚未实现。

手写 MIL 使用独立 ANTLR 前端，只能调用 target profile 公开宏；它与 MPL 一样经过严格类型、硬件契约、优化、Runtime、内存规划和 mlog 限制校验。

`build` 的最后一个参数是构建目录。编译器在其中生成最终 `runtime.msch` 蓝图，以及 `Main.mlog`、`Main.mil`、`report.json`、`deployment.json` 和连接说明等可检查的中间产物。`.mil` 保留普通变量、表达式及 `if`/`while`/`for` 等结构化写法，只有需要映射游戏能力的高级糖变为所选 profile 的宏调用；它不是逐条包装 mlog。正常部署时把蓝图导入游戏，`.mlog` 用于排查单个处理器代码。

默认构建在最终 `.mlog` 中使用最短的 `_0`、`_1` … 跳转标签以节省代码空间；排查生成逻辑时可加 `--debug`，让最终 target lowering 使用可读的完整标签名。源级 `.mil` 保留结构化控制流，通常不需要展示这些标签：

```bash
./gradlew run --args='build --debug --target=v146 my-mpl-project my-mpl-project/build'
```

编译信息默认使用中文；`--lang=zh-CN` 可显式指定该 catalogue，并为后续语言目录保留稳定的命令行接口。错误码不随翻译改变。

`src/hardware.mplh` 中的 `const AlertBoard: Message = link("message1");` 将 MPL 硬件名绑定到游戏提供的链接变量。可参考 [基础输出示例](examples/基础输出)。

## 文档

- [MPL 语言规范（讨论稿）](docs/语法设计/语言规范（讨论稿）.md)：语法、类型、控制流、优先级及待决议题。
- [聚合类型与静态容器（讨论稿）](docs/语法设计/聚合类型与静态容器（讨论稿）.md)：元组、`T[]`、`List<T>`、`Set<T>` 的字面量、元素访问、遍历与受证明 Array 动态下标。
- [动态聚合 Memory 运行时（讨论稿）](docs/开发设计/动态聚合Memory运行时（讨论稿）.md)：边界证明、物理 Cell/Bank 分段、mlog `read`/`write` 与蓝图拓扑。
- [单位遍历与私有运行时（讨论稿）](docs/语法设计/单位遍历与私有运行时（讨论稿）.md)：以 Unit.getAll类型() / Building.getAll类型() 遍历游戏对象的语义与实现边界。
- [字符串与输出运行时（讨论稿）](docs/语法设计/字符串与输出运行时（讨论稿）.md)：全局字符输出表、字符串序列与拼接边界。
- [硬件声明、内存与图形接口（讨论稿）](docs/语法设计/硬件声明、内存与图形接口（讨论稿）.md)：只含声明语句的 .mplh、组合屏幕、编译器统一管理的内存和绘制接口。
- [模块、包与对象模型（讨论稿）](docs/语法设计/模块、包与对象模型（讨论稿）.md)：import/export、硬件依赖注入、访问控制与 C++ 风格对象生命周期。
- [宏中间语言设计（讨论稿）](docs/开发设计/宏中间语言设计（讨论稿）.md)：可由性能敏感用户直接编写的宏语言与两阶段编译边界。
- [编译器路线图](docs/开发设计/编译器路线图.md)：Java 编译器的分层设计与阶段目标。
- [编译器架构（实现稿）](docs/开发设计/编译器架构（实现稿）.md)：Java 模块边界、IR、v146 函数 ABI、内存布局与最小闭环。
- [运行时存储、函数与跨 Tick 语义（讨论稿）](docs/开发设计/运行时存储、函数与跨Tick语义（讨论稿）.md)：处理器变量与物理内存的边界、`@counter` 函数 ABI、跨 tick 可观察行为及生成预算。
- [多处理器协作、分片与共享内存（讨论稿）](docs/开发设计/多处理器协作、分片与共享内存（讨论稿）.md)：单处理器优先的分片部署、共享 Memory 邮箱、每片指令限制、I/O/Unit 所有权与优化策略。
- [蓝图处理器识别与手动连接（讨论稿）](docs/开发设计/蓝图处理器识别与手动连接（讨论稿）.md)：`Main` 固定位置、代码头标识、外部 alias 核验与链接就绪启动门。
- [语言设计完备性审查（讨论稿）](docs/开发设计/语言设计完备性审查（讨论稿）.md)：已冻结规则、阻塞实现的决议与建议冻结顺序。
- [V146 目标配置（讨论稿）](docs/开发设计/V146目标配置（讨论稿）.md)：基线 Mindustry v146 profile，以及由源码确认的执行、内存和 I/O 限制。
- [V159.7 目标配置（讨论稿）](docs/开发设计/V159.7目标配置（讨论稿）.md)：最新支持版本的 Logic 增量、特权边界和硬件限制。
- [目标版本与优化策略（讨论稿）](docs/开发设计/目标版本与优化策略（讨论稿）.md)：多 profile 构建、包兼容性与新指令专用优化的规则。
- [Profile 与构建产物 Schema（讨论稿）](docs/开发设计/数据格式/Profile与构建产物Schema（讨论稿）.md)：机器可读 target profile、构建报告、部署清单与只含处理器/Memory 的运行时蓝图契约。
- [基于官方 Wiki 的设计审查（讨论稿）](docs/开发设计/基于官方Wiki的设计审查（讨论稿）.md)：将当前设计与官方 Logic Wiki 对照，记录目标层约束、缺失语义与原型优先级。
- [基于 V146 源码的指令审查（讨论稿）](docs/开发设计/基于V146源码的指令审查（讨论稿）.md)：逐项记录 v146 指令的权限、缓冲、暂停与静默失败行为，以及对应的 MPL 约束。
- [基础语法示例](docs/示例/基础语法示例.mpl)：讨论中的最小表面语法示例，未承诺可编译。
- [完整语法示例](docs/示例/完整语法示例.mpl)：以一个顶层程序串联模块注入、类、函数、严格数值类型、控制流、单位遍历、内存、绘图与消息输出。
- [完整示例：项目配置](docs/示例/完整项目配置示例.json)、[硬件声明](docs/示例/完整硬件声明.mplh)、[主程序](docs/示例/完整主程序.mpl)、[外部包源码](docs/示例/外部包需求示例.mpl) 与 [外部包硬件声明](docs/示例/外部包硬件声明示例.mplh)：展示硬件注入、全局内存预算、对象、单位遍历和图形输出如何协作。
- [组合屏幕契约示例](examples/组合屏幕契约)：展示组合 Display 的硬件声明、绘制代码和蓝图构建产物。
- [Dagger 三单位绕圈示例](examples/单位绕圈)：`Unit.getAllDagger()`、持续循环和单位移动的 v146 游戏内验收输入，以及部署限制说明。
- [MPL/MIL 混合模块示例](examples/混合模块)：展示相对 import、导出常量、跨语言函数、硬件 alias 与完整蓝图构建产物。
- [工作区包示例](examples/工作区包)：展示 `mpl install`、确定性锁文件、包 `.mplh require` 与 `with` 硬件注入。
- [对象模型示例](examples/对象模型)：展示构造器、字段、实例方法、对象身份、长期实例和函数内非逃逸临时实例的完整构建。

## 当前范围

- 维护可讨论、可版本化的 MPL 语法规范；
- 明确 MPL 与 mlog 的映射边界；
- 规划 Java 编译器的前端、IR 与代码生成阶段。

动态长度/泛型可变容器、继承与闭包、用户并发、跨重新部署持久化，以及完整 profile 指令签名表仍在后续范围。当前已支持元组、定长数组、List 与 Set 的静态布局，以及标准计数循环中受证明的 Array 动态下标；后者由编译器自动规划物理 Memory 并写入蓝图。嵌套容器、任意动态下标和可变 List/Set 尚未实现。工作区包解析、严格硬件注入、组合屏幕尺寸匹配、绘制分发、顶层静态 class/new 与非逃逸局部分配点复用已实现；registry、String 动态序列和允许引用逃逸的物理对象池仍在后续阶段。

## 开发约定

- 源码使用 UTF-8，MPL 文件使用 .mpl 扩展名。
- 目标 JDK 为 17。
- 新增或修改已确认语法时，应同时添加正向和反向测试。
- 在语法冻结前，示例中的数组、元组、属性访问和方法调用不得视为稳定功能。

## 仓库状态

仓库当前计划先以私有方式维护，待语法和基础实现稳定后再公开。发布前会补充版本策略与贡献指南。
