// main.mpl 的顶层语句会直接执行。
// MainScreen、AlertBoard、Data、Runtime、Scratch 自动来自 hardware.mplh。

import { StatusPanel } from "@mpl/status-panel" with {
    storage: Runtime,
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

// Data 是物理内存，Scratch 是虚拟内存；访问语法完全一致。
Data[0] = damagedCount;
Scratch[0] = damagedCount;

MainScreen.clear(Color.black);
for (var i: Int = 0; i < damagedCount; i += 1) {
    var height: Float = clamp(Data[0] + i, 0.0, MainScreen.height);
    MainScreen.fill(Color.green);
    MainScreen.fillRect(i * BarWidth, MainScreen.height - height, BarWidth - 2, height);
}
MainScreen.flush();

panel.render(damagedCount);
AlertBoard.print("受损 Alpha 数量：", damagedCount);
