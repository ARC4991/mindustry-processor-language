# mindustry-processor-language

MPL（Mindustry Processor Language）是一个面向 Mindustry 游戏逻辑处理器的高级语言与编译器项目。它先将 MPL 源码降级为支持 profile 宏的 MIL（Macro Intermediate Language），再生成游戏可执行的 mlog 指令。MIL 保留变量、表达式、函数和结构化控制流等 MPL 基础语法，只展开 Unit/Building 查询、字符串运行时等高级糖及其运行时需求。

项目目前处于原型实现阶段，尚未提供稳定的编译器版本。编译器使用 Java（JDK 17），MPL 与 MIL 均使用拆分的 ANTLR 4 词法/语法文件；任何标记为“讨论基线”的语法仍可能调整。v146 使用兼容的 String 字符跳转表，v159.7 已自动选择 `printchar` 专用 lowering，并在构建报告中给出相对 baseline 的指令与标签差值。

## 快速试用

当前原型支持初始化项目、基础数值/控制流、锁定的 workspace/Git/registry 包、MPL/MIL 混合模块、Kotlin 风格类型推导、单继承 `class/new`、Java/Kotlin 风格按签名重载、Message/Display I/O，以及 Unit/Building 对象查询。`LinkedBuildingSet` 等实现类型不会出现在 MPL 表面，用户统一使用 `Set<Unit<T>>` 或 `Set<Building<T>>`。字符串、对象、MutableList、跨 tick Runtime 和多处理器蓝图均由编译器统一规划。需要 JDK 17；发行目录同时提供 Unix shell 与 Windows `.bat` 启动器：

```bash
# 本地发行包可通过 ./gradlew releaseArchive 生成；GitHub CD 会自动生成同样的 releases 产物。

# CLI 始终以当前目录为项目根。
mkdir my-mpl-project && cd my-mpl-project
../releases/mpl init

# 依赖非空时先生成确定性的 mpl.lock。
../releases/mpl install

# 生成蓝图、Main.mlog、Main.mil 及格式化的构建清单。
../releases/mpl build --target=v146
```

`mpl.json` 的 `entry` 可指向 `src` 下的 `.mpl` 或 `.mil`。入口可用 `import { name } from "./module";` 递归链接 `src` 内显式 `export class` / `export fun` / `export val` 的 MPL 或 MIL 模块；依赖顶层初始化只执行一次，私有符号由链接器隔离。`workspace:`、`git:` 和 `registry:` 依赖由 `mpl install` 递归锁定到格式化的 `mpl.lock`，`check/build` 会验证根清单、包源码、`.mplh` 摘要、单版本约束和 target 能力，绝不自动更新锁文件。`mpl install <包名>` 从 IO 网络清单安装，`mpl install 名称=<git-url|mplpkg>` 支持直连 Git 和 `.mplpkg`；`mpl search` 搜索清单。包可在自己的 `.mplh` 中用 `require` 声明命名硬件，并由调用方通过 `with` 严格注入；组合 Display 的尺寸约束也会在链接期验证。

手写 MIL 使用独立 ANTLR 前端，只能调用 target profile 公开宏；它与 MPL 一样经过严格类型、硬件契约、优化、Runtime、内存规划和 mlog 限制校验。

`build` 始终在当前目录的 `build/` 中生成最终 `runtime.msch` 蓝图，以及 `Main.mlog`、`Main.mil`、`report.json`、`deployment.json` 和连接说明等可检查的中间产物。`.mil` 保留普通变量、表达式及 `if`/`while`/`for` 等结构化写法，只有需要映射游戏能力的高级糖变为所选 profile 的宏调用；它不是逐条包装 mlog。正常部署时把蓝图导入游戏，`.mlog` 用于排查单个处理器代码。

当 `runtime.goal` 为 `maxPerformance`、允许至少两个处理器，且调用图中存在可达的纯数值函数时，编译器会自动生成 `Worker-N.mlog/.mil`。效果分析只接受无捕获、无 I/O、无 Unit/Building、无物理 Memory/对象分配，且参数与返回值均为 `Int` / `Float` / `Bool` 的函数；有调用关系的 helper 固定同片，互不依赖的分量按同一次 target 发射得到的真实指令/标签数，并计入 handler 与请求宽度开销后确定性均衡。Main 通过编译器私有的共享 Memory 邮箱同步调用这些函数，最后确认关闭全部 Worker。普通顺序控制流不会按行硬切。

默认构建在最终 `.mlog` 中使用最短的 `_0`、`_1` … 跳转标签以节省代码空间；排查生成逻辑时可加 `--debug`，让最终 target lowering 使用可读的完整标签名。源级 `.mil` 保留结构化控制流，通常不需要展示这些标签：

```bash
./releases/mpl build --debug --target=v146
```

编译信息默认使用中文；`--lang=zh-CN` 可显式指定该 catalogue，并为后续语言目录保留稳定的命令行接口。错误码不随翻译改变。

`src/hardware.mplh` 中的 `const AlertBoard: Message = link("message1");` 将 MPL 硬件名绑定到游戏提供的链接变量。可参考 [基础输出示例](examples/基础输出)。

## 文档

- [MPL 语言规范](docs/语法设计/稳定/语言规范.md)：语法、类型、控制流、优先级与稳定规则。
- [聚合类型与静态容器](docs/语法设计/稳定/聚合类型与静态容器.md)：元组、`T[]`、`List<T>`、`Set<T>` 与 `MutableList`。
- [动态聚合 Memory 运行时](docs/开发设计/稳定/动态聚合Memory运行时.md)：边界证明、物理 Cell/Bank 分段与蓝图拓扑。
- [单位遍历与运行时](docs/语法设计/稳定/单位遍历与运行时.md)：Unit/Building 查询、Set API 和私有 flag 管理。
- [字符串与输出运行时](docs/语法设计/稳定/字符串与输出运行时.md)：字符表、拼接和自动 flush。
- [硬件声明、内存与图形接口](docs/语法设计/稳定/硬件声明、内存与图形接口.md)：`.mplh`、组合 Display 和自动资源规划。
- [模块、包与对象模型](docs/语法设计/稳定/模块、包与对象模型.md)：import/export、包、继承、重载与对象生命周期。
- [宏中间语言设计](docs/开发设计/稳定/宏中间语言设计.md)：MIL 宏边界与两阶段编译。
- [编译器路线图](docs/开发设计/稳定/编译器路线图.md)：Java 编译器的分层设计与实现状态。
- [编译器架构](docs/开发设计/稳定/编译器架构.md)：Java 模块边界、IR、ABI、内存布局与 Runtime。
- [运行时存储、函数与跨 Tick 语义](docs/开发设计/稳定/运行时存储、函数与跨Tick语义.md)：变量、Memory、函数 ABI 和预算。
- [多处理器协作与共享内存](docs/开发设计/稳定/多处理器协作、分片与共享内存.md)：分片、邮箱和资源均衡。
- [蓝图处理器识别与手动连接](docs/开发设计/稳定/蓝图处理器识别与手动连接.md)：处理器身份、Memory 连接与部署提示。
- [目标版本与优化策略](docs/开发设计/稳定/目标版本与优化策略.md)：v146 基线、v159.7 增量和 profile 优化。
- [Profile 与构建产物 Schema](docs/开发设计/稳定/Profile与构建产物Schema.md)：报告、部署清单与蓝图契约。
- [CLI 与包管理](docs/开发设计/稳定/CLI与包管理.md)：当前目录命令、锁文件、Git、`.mplpkg`、包索引与跨平台发行。
- 源码和 Wiki 审查请见 [归档](docs/归档/README.md)。
- [基础语法示例](docs/示例/基础语法示例.mpl)：讨论中的最小表面语法示例，未承诺可编译。
- [完整语法示例](docs/示例/完整语法示例.mpl)：以一个顶层程序串联模块注入、类、函数、严格数值类型、控制流、单位遍历、内存、绘图与消息输出。
- [完整示例：项目配置](docs/示例/完整项目配置示例.json)、[硬件声明](docs/示例/完整硬件声明.mplh)、[主程序](docs/示例/完整主程序.mpl)、[外部包源码](docs/示例/外部包需求示例.mpl) 与 [外部包硬件声明](docs/示例/外部包硬件声明示例.mplh)：展示硬件注入、全局内存预算、对象、单位遍历和图形输出如何协作。
- [组合屏幕契约示例](examples/组合屏幕契约)：展示组合 Display 的硬件声明、绘制代码和蓝图构建产物。
- [Dagger 三单位绕圈示例](examples/单位绕圈)：`Unit.getAllDagger()`、持续循环和单位移动的 v146 游戏内验收输入，以及部署限制说明。
- [MPL/MIL 混合模块示例](examples/混合模块)：展示相对 import、导出常量、跨语言函数、硬件 alias 与完整蓝图构建产物。
- [工作区包示例](examples/工作区包)：展示 `mpl install`、确定性锁文件、包 `.mplh require` 与 `with` 硬件注入。
- [对象模型示例](examples/对象模型)：展示构造器、字段、实例方法、对象身份、长期实例和函数内非逃逸临时实例的完整构建。
- [继承与类型推导示例](examples/继承与类型推导)：展示单继承、`super`、虚方法、最具体重载以及变量/方法返回类型推导。
- [字符串运行时示例](examples/字符串运行时)：展示动态拼接、函数值复制、长度、内容比较和私有字符输出表。

## 当前范围

- 维护可讨论、可版本化的 MPL 语法规范；
- 明确 MPL 与 mlog 的映射边界；
- 规划 Java 编译器的前端、IR 与代码生成阶段。

动态长度/泛型可变容器、接口与闭包、用户并发、跨重新部署持久化，以及完整 profile 指令签名表仍在后续范围。当前已支持元组、定长数组、List、Set 与容量化 MutableList，以及标准计数循环中受证明的动态下标；物理 Memory 由编译器规划并写入蓝图。workspace、Git 和 `.mplpkg` registry 包均可锁定校验；网络索引、跨 shard 所有权转移和原生运行时仍在持续完善。

## 开发约定

- 源码使用 UTF-8，MPL 文件使用 .mpl 扩展名。
- 目标 JDK 为 17。
- 新增或修改已确认语法时，应同时添加正向和反向测试。
- 稳定语法和构建产物以 `docs/语法设计/稳定`、`docs/开发设计/稳定` 为准；历史讨论统一放在 `docs/归档`。

## 仓库状态

仓库当前计划先以私有方式维护，待语法和基础实现稳定后再公开。发布前会补充版本策略与贡献指南。
