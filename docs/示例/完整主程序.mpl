// main.mpl：顶层语句直接执行。
// MainScreen、AlertBoard、Data、Runtime 来自完整硬件声明.mplh，无需 import。

import { StatusPanel } from "@mpl/status-panel" with {
    storage: Runtime,
    screen: MainScreen
};

var panel = new StatusPanel();
var damagedCount: Int = 0;

for (var unit : Unit.getAllAlpha(_.health < 50.0)) {
    damagedCount += 1;
    unit.move(MainScreen.width / 2, MainScreen.height / 2);
}

Data[0] = damagedCount;
panel.render(damagedCount);
AlertBoard.print("受损 Alpha 数量：", damagedCount);
