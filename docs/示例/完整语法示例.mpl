// 这是一个端到端的 MPL 表面语法示例。
// 它与“完整硬件声明.mplh”配套：MainScreen、AlertBoard 和内存预算
// 均由硬件声明强制声明。内存不暴露给 MPL/MIL 源码。

import { StatusPanel } from "@mpl/status-panel" with {
    screen: MainScreen
};

const val HealthLimit: Float = 50.0;
const val MaxSamples: Int = 8;

class SampleWindow {
    private total: Int;

    public fun SampleWindow() {
        this.total = 0;
    }

    public fun add(value: Int): Void {
        this.total += value;
    }

    public fun average(): Float {
        return this.total / MaxSamples;
    }
}

fun clamp(value: Float, lower: Float, upper: Float): Float {
    if (value < lower) {
        return lower;
    } else if (value > upper) {
        return upper;
    }
    return value;
}

// main.mpl 的顶层语句就是程序入口。
var panel = new StatusPanel();
var samples = new SampleWindow();
var damagedCount: Int = 0;
var index: Int = 0;

for (var unit : Unit.getAllAlpha(_.health < HealthLimit)) {
    damagedCount += 1;
    samples.add(1);
    unit.move(MainScreen.width / 2, MainScreen.height / 2);
}

for (var i: Int = 0; i < MaxSamples; i += 1) {
    var level: Float = clamp(damagedCount + i, 0.0, 100.0);
    MainScreen.fill(Color.green);
    MainScreen.fillRect(i * 8, MainScreen.height - level, 6, level);
}

while (index < damagedCount) {
    index += 1;
}

do {
    index -= 1;
} while (index > 0);

panel.render(damagedCount);
AlertBoard.print("受损 Alpha 数量：", damagedCount, "；采样均值：", samples.average());
