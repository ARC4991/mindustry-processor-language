# 元组函数与推导示例

该项目展示变量的元组/混合数值数组推导，以及顶层函数的数值/布尔元组参数、返回值和返回类型推导。

`rotate` 推导返回 `(Float, Int)`；`normalize` 的两条返回路径逐位置求公共类型，推导为 `(Float, Float)`；`mixed` 推导为 `Float[]`。

~~~bash
cd examples/元组函数推导
mpl build --target=v146
~~~

导入 `build/runtime.msch` 后，将 Message 连接到蓝图中的 `Main` 处理器并保持游戏 alias 为 `message1`。程序执行一次并输出：

~~~text
rotated=4.5,3 normalized=1.5,2 arraySize=3
~~~

`build/Main.mil` 保留推导后的元组签名并可再次作为 MIL 入口编译；`build/Main.mlog` 使用逐位置参数槽和结果槽，不把元组压成单个数值。
