# 动态聚合 Memory 运行时（讨论稿）

> 状态：受证明的定长 Array 动态读写、物理分段、mlog lowering 与蓝图部署已实现。动态长度、MutableList/MutableSet、嵌套聚合和对象池尚未实现。

运行时下标数组不向 MPL 暴露 Memory 或地址。每个需要运行时下标的 `Array<T>` 由编译器分配固定容量的逻辑连续物理槽；一个数组可跨越多个 Cell/Bank 切片，目标 lowering 负责将逻辑下标转换为正确的段 alias 和局部 offset。容量是布局属性，不属于公开类型。

首个闭环只支持标量 `Int`、`Float`、`Bool` 元素。数组字面量确定容量，运行时下标读写均生成 compiler-private `read`/`write`。越界不是运行时异常：语义阶段必须证明 `0 <= index < capacity`，无法证明时构建失败。当前已实现的证明形式为：

~~~mpl
var values: Int[] = [1, 2, 3];
for (var i: Int = 0; i < values.size; i += 1) {
    values.set(i, values[i] + 1);
}
~~~

初值必须为 `0`，条件必须为同一数组的 `i < array.size`，更新必须为 `i += 1`，且循环体不能重新赋值 `i`。该证明只授权同一数组的 `array[i]`、`array.get(i)` 和 `array.set(i, value)`；`i + 1`、`<= array.size` 或另一个数组都不会被隐式接受。

布局器按确定顺序汇总所有需要动态下标的数组，根据 `mpl.json` 中 Cell/Bank 数量上限生成分段。同一份 `PhysicalMemoryLayout` 同时交给 mlog lowering、`RuntimePlanner`、`runtime.msch` 和 `deployment.json`，不允许产物阶段重新猜测内存类型或数量。构建报告当前输出总 `physicalSlots`，部署清单输出每段的 kind、capacity、usedSlots 和 alias；按数组列出占用来源仍属后续报告完善项。

Tuple 保持静态布局。可变 `List`、`Set`、嵌套聚合与对象池在数组 read/write 闭环验证后复用同一分段布局，但必须另行定义容量、去重与生命周期。
