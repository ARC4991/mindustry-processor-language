# Dagger 三单位绕圈

这是 `Unit.getAllDagger()`、`for`、Unit 对象方法与持续 `while` 的游戏内验收示例。它让三个同队 Dagger 围绕一个固定的逻辑坐标点移动，彼此保持约 120° 的相位差。

`Dagger` 是正确的 MPL 类型名；底层 Mindustry 内容名为 `dagger`。`Dragger` 不是游戏中的单位类型。

## 部署前提

- target 为 Mindustry `v146`；推荐使用 Hyper Processor，使目标点更新更平滑。
- 处理器同队、可控制的 Dagger 必须**恰好为三个**，且没有其他同队 Dagger。`UnitSet` 不承诺顺序；若存在更多同类单位，遍历会向更多单位下达命令，不能把它当成只控制三个固定对象的编队。
- 三个 Dagger 不能正由玩家、单位指挥命令、手写 mlog 或另一份 MPL 部署控制；`unit.move(...)` 会接管其 LogicAI，竞争控制者会覆盖命令。
- 将 `centerX`、`centerY` 改为可通行区域中心。坐标是 Logic 的 tile 坐标，不是屏幕/世界像素坐标；半径 `6.0` 也是 tile 单位。
- 没有硬件链接要求。单位死亡、生成或被其他控制器抢占时，UnitSet 按弱一致语义重新扫描，不保证编队身份或稳定顺序。

## 生成 `output.mlog`

在仓库根目录运行：

```bash
./gradlew run --args='build --target=v146 examples/dagger-circle examples/dagger-circle/output.mlog'
```

再将生成的 `examples/dagger-circle/output.mlog` 粘贴到上述处理器的代码编辑器中。

该命令已由仓库的 v146 UnitSet 纵切构建验证；`output.mlog` 是同一源码生成的可粘贴产物，而不是手写 mlog。

## 示例的 MPL 约定

`Math.sin(angle)` 与 `Math.cos(angle)` 以**度**为输入，直接映射 v146 mlog 的 `op sin` / `op cos`。`Clock.time` 映射 v146 的 `@time`（毫秒），因此 `phase = Clock.time * 0.02` 表示每秒 20°，不会因处理器 IPT 改变编队的角速度。`.where(_.alive)` 同时验证了 UnitSet 的链式筛选与 `alive → !@dead` lowering。
