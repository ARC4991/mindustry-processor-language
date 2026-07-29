# Profile 与构建产物 Schema（讨论稿）

> 状态：本文冻结编译器内部/产物 JSON 的第一版边界。字段可在 `schemaVersion` 递增后扩展；同一版本不得改变字段含义。

## 目标

target profile、构建报告和部署清单不能继续以 Java 常量或自由文本存在。它们是三种不同用途的数据：

| 文件 | 生产者 | 消费者 | 是否进入发布产物 |
| --- | --- | --- |
| `目标配置.schema.json` | 仓库维护者 | profile 加载器、测试 | 否，校验 profile 数据 |
| `profiles/v146.json` | 仓库维护者 | 编译器 | 随编译器发布 |
| `构建报告.schema.json` / `report.json` | 编译器 | 开发者、CI | 是 |
| `部署清单.schema.json` / `deployment.json` | 编译器 | 安装者、`mpl hardware check` | 多 shard 时是 |

profile 描述**游戏事实与可用 lowering**；`report.json` 描述一次构建实际使用了多少资源；`deployment.json` 描述安装时必须满足什么硬件契约。三者均不包含用户源码、私有 Unit flag、UnitRef、内部变量名、物理地址或宏展开细节。

当前原型的 `KnownProfiles` 已从内置 `v146.json`、`v159.7.json` 读取 schema 版本、能力、处理器、Memory、当前 lowering 使用的指令签名、已审计的 Unit/Building 内容子集和 MIL 宏表。`SemanticAnalyzer` 已用 profile 校验 `Unit.getAll...`、属性访问和 `move`；构建器也会用 Building 表校验 `.mplh` 的 `link(...)` 类型。Building 遍历、控制 lowering 与宏白名单执行尚未实现，表中的这些项目目前是目标事实与后续实现契约，不能被解释为已经可生成 mlog；表也不能被误称为完整游戏内容表。

## 版本与兼容性

- 所有文件的 `schemaVersion` 从 `1` 开始。消费者只接受自己支持的精确版本；未知版本是诊断而不是猜测解析。
- profile 的 `id` 是精确 target，例如 `v146`、`v159.7`；`game.version` 与 `game.commit` 记录审查来源，不能由构建时联网查询代替。
- profile 数据变更必须独立提交，并同时更新来源、差异报告及 profile 测试。新 target 不得修改旧 profile 的 JSON。
- 报告和部署清单附带 `compiler.version`、`targetProfile` 与 `inputDigest`。`inputDigest` 覆盖 `mpl.json`、锁文件、所有源码、`.mplh` 与 profile 摘要，便于判断蓝图/清单是否过期。

## Target profile

完整结构由 [目标配置.schema.json](目标配置.schema.json) 校验。第一版分为六块：

1. `limits`：指令、标签、token、Message、Display 等硬上限；
2. `processors`：处理器的 IPT、链接范围、权限类别；
3. `hardware`：Memory、Display、Message 等容量与部署属性；
4. `instructions`：目标 mlog 指令签名、特权与限制；
5. `contents`：Unit/Building 内容名、可读取字段、允许的控制动作；
6. `macros`：MIL 公开宏签名、效果、成本上界与 target lowering 名称。

profile 的 `macros` 是 MIL 白名单的唯一来源。每一个宏必须同时声明参数类型、权限、可能影响的资源、最坏成本和生成目标指令；宏展开器不能根据宏名字符串临时猜测。`instructions` 允许记录游戏完整指令表，但只有经 `macros` 或受支持 lowering 引用的指令才会进入普通 MPL/MIL。

`contents` 中的 `fields` 用 MPL 类型而非 mlog `LAccess` 原名表示；例如 `alive: Bool` 可映射为目标 `@dead` 的取反。这样 profile 可以保存来源字段，同时不把底层的 `flag`、对象/数字转换或未知 sensor 名称暴露给语言。

## `report.json`

完整结构由 [构建报告.schema.json](构建报告.schema.json) 校验。一次成功构建总会产生报告，即使它只输出一个 `output.mlog`。报告是优化和 CI 的事实记录，不是部署输入：

- `shards` 逐片列出指令、标签、token 峰值、IPT、虚拟槽、物理槽、String、对象池和 runtime 元数据；
- `totals` 可以求和展示，但物理与虚拟槽必须分别列出，不能把 `512 + 4096` 解释为可互换的内存；
- `optimizations` 记录优化名称、所属 shard 与实际应用次数。只有后端能够可靠计算节省的指令数时，才会在后续 schema 版本增加独立估算字段，不能用应用次数冒充节省量；
- `diagnosticSummary` 即使构建失败也可生成，但失败构建不得伪造可部署的 `deployment.json`。

## `deployment.json` 与运行时蓝图

完整结构由 [部署清单.schema.json](部署清单.schema.json) 校验。单 shard 项目可不生成它；多 shard 项目必须生成。它将硬件分成两类：

- `runtimeTopology`：处理器和真实 Memory Cell/Bank。可选 `runtime.msch` **只包含这一类**，并可包含处理器到 Memory 的内部连接。
- `externalHardware`：Display、Message、Switch、炮塔及其它 `link(...)` 建筑。它们不进入蓝图，必须由玩家自行放置、连接；清单只记录 shard、alias、期望类型、读/写权限与组合屏关系。

`mpl hardware check` 读取清单并验证外部硬件；每个 shard 的共享 Memory 同一性还要由生成代码的启动握手确认。硬件检查不能自动改变地图、补线或把外部建筑写入蓝图。

## 产物示例

- [v146 profile 最小示例](../../示例/v146目标配置示例.json)
- [构建报告示例](../../示例/构建报告示例.json)
- [多处理器部署清单示例](../../示例/部署清单示例.json)

示例不是 profile 的完整内容表；它们用于固定字段语义和测试序列化。完整 v146/v159.7 内容将从源码审查结果生成。
