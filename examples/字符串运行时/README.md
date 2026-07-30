# 字符串运行时示例

该示例覆盖可重新赋值的 `var String`、运行时拼接、函数参数/返回值的按值复制、UTF-16 `length`、内容相等比较和 Message 自动刷新。

~~~bash
./gradlew run --args='build --target=v146 examples/字符串运行时 examples/字符串运行时/build'
~~~

构建产物中的 `runtime.msch` 会自动包含 String 描述符表和代码单元序列所需的 Memory。导入游戏后，只需把 Message 建筑连接到 `Main` 处理器，并确认游戏链接名为 `message1`。程序应输出：

~~~text
MPL 字符串运行时，UTF-16 长度=10，内容相等=1
~~~

MPL 不公开 String 地址、Memory 或 flush。直接位于 `Status.print(...)` 中的字符串字面量仍编译为普通 `print`；动态 String 才通过全局去重的输出块和编译器私有间接跳转输出。

编译器会沿整个函数调用图推导 String 容量，并汇总 `var String` 的所有赋值。因此该示例的短文本不会为每个参数和返回值各预留 400 槽；当前 v146 构建只需 1 个自动连接的 Memory Bank。
