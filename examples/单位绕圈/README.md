# 单位绕圈

持续控制最多三台存活的 Dagger。`take(3)` 使用编译器私有 Unit runtime；MPL 源码不能访问 flag。

```bash
cd examples/单位绕圈
mpl build --target=v146 --debug
```

将 `build/runtime.msch` 导入游戏，再把 `build/Main.mlog` 写入蓝图中的处理器。地图需启用逻辑单位控制。
