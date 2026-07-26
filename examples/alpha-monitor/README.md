# Alpha 受损单位监控

这是 MPL 当前讨论稿的端到端示例项目。它展示一个处理器如何：

- 由 `hardware.mplh` 集中声明屏幕、消息板、物理内存与虚拟内存；
- 不导入硬件常量，直接在 `main.mpl` 中使用它们；
- 将满足最小硬件参数的资源注入外部包；
- 遍历生命值低于阈值的 Alpha 单位、绘制柱状图并输出消息。

## 目录

```text
alpha-monitor/
├── mpl.json                         # 项目配置（讨论格式）
├── src/
│   ├── hardware.mplh                # 唯一硬件声明文件
│   └── main.mpl                     # 顶层入口
└── packages/status-panel/
    ├── mpl.json                     # 本地演示包元数据
    └── index.mpl                    # 包的公开实现与资源需求
```

## 使用说明

当前仓库尚未提供编译器，因此此项目是语法和架构样例，不能直接构建。`mpl.json` 中的 `workspace:` 依赖写法用于表达“本地演示包”；正式包清单与锁文件格式仍待冻结。

构建时，`src/hardware.mplh` 是必需且唯一的硬件声明。其 `export const` 资源会自动对项目源码可见；`main.mpl` 不允许用 `import` 补充或替换内存声明。`Data` 的容量、`Scratch` 的下标范围，以及传给 `StatusPanel` 的 `Runtime`、`MainScreen` 均由编译器静态校验。
