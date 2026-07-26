// 本示例描述讨论中的表面语法，不保证当前可编译。

const val step: Int = 8;

fun clamp(value: Float, lower: Float, upper: Float): Float {
    if (value < lower) {
        return lower;
    }
    if (value > upper) {
        return upper;
    }
    return value;
}
for (var unit : Unit.getAllAlpha(_.health < 50.0)) {
    unit.move(32, 64);
    // AlertBoard 由项目的硬件声明文件导出，无需在此导入。
    AlertBoard.print("单位血量：", unit.health);
}
