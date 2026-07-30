# 建筑重连示例

这个示例保存 `Building.getAllDuo().get(0)` 得到的 `Building<Duo>?`，并在持续循环中读取其生命值、关闭该炮塔。

构建生成的蓝图只包含 `Main` 处理器。导入后，把两个 Duo 都连接到 `Main`，并在处理器配置界面确认它们依次显示为 `duo1`、`duo2`。全部外部硬件就绪前，程序会停留在编译器生成的启动门。

`primary` 保存的是编译器私有的稳定链接描述符，而不是某一时刻的游戏 Building 对象。拆除 `duo1` 所指向的 Duo 后，访问会安全得到空目标；在同一链接位置重建且 alias 恢复为 `duo1` 后，后续字段读取和控制会自动解析到新建筑。MPL 源码不能读取或修改这个描述符。

构建命令：

```bash
./gradlew run --args='build --target=v146 examples/建筑重连 examples/建筑重连/build'
```
