// Dagger 三单位绕圈：请在处理器同队只保留三个 Dagger。
// 坐标使用 Mindustry Logic 的 tile 坐标，而非游戏画面的像素坐标。

val centerX: Float = 100.0;
val centerY: Float = 80.0;
val radius: Float = 6.0;

while (true) {
    // @time 是毫秒；0.02 表示每秒转动 20°。
    val phase: Float = Clock.time * 0.02;
    var slot: Int = 0;

    for (var unit : Unit.getAllDagger().where(_.alive)) {
        // 三个单位相差 120°，共同围绕 centerX / centerY 移动。
        val angle: Float = phase + slot * 120.0;
        val targetX: Float = centerX + Math.cos(angle) * radius;
        val targetY: Float = centerY + Math.sin(angle) * radius;

        unit.move(targetX, targetY);
        slot += 1;
    }
}
