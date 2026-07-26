// main.mpl 的顶层语句会直接执行。
// MainScreen、AlertBoard 自动来自 hardware.mplh。
// 内存预算也在那里声明，但不会暴露为源码变量。

import { StatusPanel } from "@mpl/status-panel" with {
    screen: MainScreen
};

const val HealthLimit: Float = 50.0;
const val BarWidth: Int = 8;

fun clamp(value: Float, lower: Float, upper: Float): Float {
    if (value < lower) {
        return lower;
    } else if (value > upper) {
        return upper;
    }
    return value;
}

var panel = new StatusPanel();
var damagedCount: Int = 0;

for (var unit : Unit.getAllAlpha(_.health < HealthLimit)) {
    damagedCount += 1;
    unit.move(MainScreen.width / 2, MainScreen.height / 2);
}

MainScreen.clear(Color.black);
for (var i: Int = 0; i < damagedCount; i += 1) {
    var height: Float = clamp(damagedCount + i, 0.0, MainScreen.height);
    MainScreen.fill(Color.green);
    MainScreen.fillRect(i * BarWidth, MainScreen.height - height, BarWidth - 2, height);
}
MainScreen.flush();

panel.render(damagedCount);
AlertBoard.print("受损 Alpha 数量：", damagedCount);
