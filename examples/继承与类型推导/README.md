# 继承与类型推导示例

该项目展示 MPL 的单继承、`super`、虚方法派发、Java/Kotlin 风格重载选择，以及 Kotlin 风格变量和返回类型推导。

`Animal.score` 与 `Dog.score` 都省略了返回类型；编译器从字段、参数和 `super.score(...)` 推导为 `Int`。`classify` 也省略返回类型，调用 `classify(new Dog(...))` 时按最具体参数类型选择 `classify(Dog)`。`pet` 的静态类型是 `Animal`，`pet.score(2)` 则通过虚派发执行 `Dog.score`。

~~~bash
./gradlew run --args='build --target=v146 examples/继承与类型推导 examples/继承与类型推导/build'
~~~

构建产物中的 `runtime.msch` 只包含处理器与编译器需要的 Memory。导入游戏后，将一个 Message 连接到蓝图最左侧的 `Main` 处理器，并使链接 alias 为 `message1`。程序执行一次，输出：

~~~text
virtual=9, overload=9, count=2
~~~

`Main.mil` 会保留 `extends`、`super` 和推导后写明的 `: Int` 返回类型；`Main.mlog` 包含根据对象运行时类型生成的虚方法跳转。
