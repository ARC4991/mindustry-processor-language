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
var x: num = 0;
var y: num = clamp(32, 0, 100);

@bindUnit(@flare);
while (x < 10) {
    @move(x * step, y);
    x += 1;
}
