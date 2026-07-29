# 组合屏幕契约

演示两块 80×80 Display 组成一个 160×80 逻辑画布。示例绘制一条跨越两屏的矩形和对角线；编译器按物理 tile 平移坐标并自动刷新。Display 不会进入 `runtime.msch`，但其物理 alias、规格和逻辑布局会写入 `deployment.json` 与连接说明。
