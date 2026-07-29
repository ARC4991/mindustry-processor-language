# 静态对象示例

该示例创建两个独立的 `Counter`，分别得到 `3` 和 `15`，并通过 `!==` 验证对象身份不同。构造器、private 字段和实例方法会保留在 `Main.mil`，随后降级为隐藏 this 函数、正整数句柄和互相隔离的 mlog 字段槽。

~~~bash
./gradlew run --args='build --target=v146 examples/对象模型 examples/对象模型/build'
~~~

将生成的 `runtime.msch` 导入游戏后，把一个 Message 建筑连接到蓝图中的 `Main` 处理器，并确保游戏分配的链接名为 `message1`。程序执行一次，输出 `first=3, second=15, distinct=1` 后停止；目标 mlog 以 `1` 表示 true。

当前 `new` 只允许出现在顶层变量初始化中。函数、循环或块内的动态分配会报告 `MPL3708`，不会在游戏内发生对象池耗尽。
