# 动态聚合 Memory 运行时（讨论稿）

动态数组不向 MPL 暴露 Memory 或地址。每个动态 `Array<T>` 由编译器分配固定容量的连续物理槽，元素地址为 `base + index`；容量是布局属性，不属于公开类型。

首个闭环只支持标量 `Int`、`Float`、`Bool` 元素。数组字面量确定容量，运行时下标读写均生成 compiler-private `read`/`write`。越界不是运行时异常：语义阶段必须证明 `0 <= index < capacity`，无法证明时构建失败。

布局器汇总所有动态数组的容量，追加初始化和运行时元数据槽后，将总需求交给 `RuntimePlanner`。Cell/Bank、内部链接 alias 与跨 Bank 分派只存在于目标 lowering 和 runtime 蓝图；构建报告分别列出每个数组的元素槽和 runtime 槽。

Tuple 保持静态布局。可变 `List`、`Set`、嵌套聚合与对象池在数组 read/write 闭环验证后复用同一分段布局，但必须另行定义容量、去重与生命周期。
