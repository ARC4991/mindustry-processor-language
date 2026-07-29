# 对象模型示例

该示例创建两个长期存活的 `Counter`，分别得到 `3` 和 `15`，并通过 `!==` 验证对象身份不同。`temporaryTotal` 还在函数内创建一个不逃逸的临时 `Counter` 并得到 `24`。构造器、private 字段和实例方法会保留在 `Main.mil`，随后降级为隐藏 this 函数、正整数句柄和互相隔离的 mlog 字段槽。

~~~bash
./gradlew run --args='build --target=v146 examples/对象模型 examples/对象模型/build'
~~~

将生成的 `runtime.msch` 导入游戏后，把一个 Message 建筑连接到蓝图中的 `Main` 处理器，并确保游戏分配的链接名为 `message1`。程序执行一次，输出 `first=3, second=15, temporary=24, distinct=1` 后停止；目标 mlog 以 `1` 表示 true。

局部 `new` 必须直接初始化 `val`，且引用只能访问字段、比较身份或调用经编译器证明不会泄露接收者的方法。它按分配点复用同一个句柄和字段槽，每次执行仍重新运行构造器。返回、建立别名、作为普通参数传出或存入聚合都会报告 `MPL3708`。需要逃逸的动态对象仍等待物理 Memory 对象池，因此不会在游戏内发生对象池耗尽。
