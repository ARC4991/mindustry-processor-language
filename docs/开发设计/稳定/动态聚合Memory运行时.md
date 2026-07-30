# 动态聚合 Memory 运行时（讨论稿）

> 状态：受证明的定长 Array 动态读写、固定容量 MutableList、物理分段、唯一所有权对象池、mlog lowering 与蓝图部署已实现。MutableSet 和嵌套聚合尚未实现。

运行时下标数组和列表不向 MPL 暴露 Memory 或地址。每个需要运行时下标的 `Array<T>` 或 `MutableList<T>` 由编译器分配固定容量的逻辑连续物理槽；一个容器可跨越多个 Cell/Bank 切片，目标 lowering 负责将逻辑下标转换为正确的段 alias 和局部 offset。容量是布局属性，不属于公开类型。

首个闭环只支持标量 `Int`、`Float`、`Bool` 元素。数组字面量确定容量；`MutableList.withCapacity(n)` 显式确定列表容量，`MutableList.of(...)` 使用初值个数作为容量。运行时下标读写均生成 compiler-private `read`/`write`，列表另有 compiler-private 长度槽。越界不是运行时异常：语义阶段必须证明 `0 <= index < size`，无法证明时构建失败。当前已实现的证明形式为：

~~~mpl
var values: Int[] = [1, 2, 3];
for (var i: Int = 0; i < values.size; i += 1) {
    values.set(i, values[i] + 1);
}
~~~

初值必须为 `0`，条件必须为同一容器的 `i < container.size`，更新必须为 `i += 1`，且循环体不能重新赋值 `i`。该证明只授权同一容器的 `container[i]`、`container.get(i)` 和 `container.set(i, value)`；`i + 1`、`<= container.size` 或另一个容器都不会被隐式接受。`MutableList.add`、`removeAt` 与 `clear` 当前只允许在线性路径上出现，容量检查在语义阶段完成；`removeAt` 的下标必须是当前长度内的非负字面量，mlog Runtime 会搬移后续槽位并递减长度。

布局器按确定顺序汇总所有需要动态下标的数组，根据 `mpl.json` 中 Cell/Bank 数量上限生成分段。同一份 `PhysicalMemoryLayout` 同时交给 mlog lowering、`RuntimePlanner`、`runtime.msch` 和 `deployment.json`，不允许产物阶段重新猜测内存类型或数量。构建报告当前输出总 `physicalSlots`，部署清单输出每段的 kind、capacity、usedSlots 和 alias；按数组列出占用来源仍属后续报告完善项。

Tuple 默认保持静态布局。对象池已复用数组的跨段 read/write 后端，并以编译器证明的所有者数量作为容量；`MutableList` 复用同一跨段后端并以长度槽记录当前元素数。`MutableSet` 与嵌套聚合仍必须另行定义容量、去重与生命周期。
