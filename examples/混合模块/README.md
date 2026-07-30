# MPL/MIL 混合模块示例

`main.mpl` 是唯一执行入口。它从 `math.mpl` 导入常量和函数，并从 `output.mil` 导入直接使用公开 I/O 宏实现的函数。

```bash
cd examples/混合模块
mpl build --target=v146
```

部署后把 Message 链接为游戏变量 `message1`。程序执行一次，输出 `mixed module value=44`，随后由编译器生成的 `stop` 停止。

相对 import 可省略 `.mpl`/`.mil` 扩展名，但同一路径同时存在两种扩展名时必须明确指定，否则编译器会报告歧义。只有 `export fun` 与 `export val` 可以从其他模块导入；模块私有顶层符号由链接器隔离。
