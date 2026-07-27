# 消息输出演示

1. 放置一个逻辑处理器与一个 Message Block，并把二者连线。
2. 运行 `./gradlew run --args='build --target=v146 examples/message-output-demo examples/message-output-demo/output.mlog'`。该命令会同时生成可检查的 `output.mil` 与游戏用的 `output.mlog`。
3. 将生成的 `output.mlog` 粘贴到处理器代码编辑器。

游戏中应在 Message Block 显示：`MPL 运行成功，答案：42`。
