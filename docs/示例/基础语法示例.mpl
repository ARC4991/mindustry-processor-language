// 本示例描述讨论中的表面语法，不保证当前可编译。

const val step: num = 8;

fun clamp(value: num, lower: num, upper: num): num {
    if (value < lower) {
        return lower;
    }
    if (value > upper) {
        return upper;
    }
    return value;
}
for (var unit : Unit.getAllAlpha(_.health < 50)) {
    unit.move(32, 64);
    print("单位血量：", unit.health);
}
