# 工作区包

`应用` 通过 `workspace:` 锁定本地 `通知包`，并用 `with` 把根项目的 `StatusBoard` 注入包声明的 `output` 硬件需求。

```bash
cd examples/工作区包/应用
mpl install
mpl build --target=v146
```

`mpl install` 是唯一更新 `mpl.lock` 的命令。后续 `check/build` 只读取锁文件；修改包源码或 `.mplh` 后必须重新执行安装。
