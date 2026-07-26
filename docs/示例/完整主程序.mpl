// main.mpl：顶层语句直接执行。
// MainScreen、AlertBoard 来自完整硬件声明.mplh，无需 import。
// 内存也已在该文件声明，但由编译器统一管理，不暴露给源码。

import { StatusPanel } from "@mpl/status-panel" with {
    screen: MainScreen
};

var panel = new StatusPanel();
var damagedCount: Int = 0;

for (var unit : Unit.getAllAlpha(_.health < 50.0)) {
    damagedCount += 1;
    unit.move(MainScreen.width / 2, MainScreen.height / 2);
}

panel.render(damagedCount);
AlertBoard.print("受损 Alpha 数量：", damagedCount);
