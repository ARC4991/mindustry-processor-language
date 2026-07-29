# 组合屏幕契约

演示 `Display.combine(...)` 仅属于外部硬件契约：两块 Display 不会进入 `runtime.msch`，但会在 `deployment.json` 中登记。
