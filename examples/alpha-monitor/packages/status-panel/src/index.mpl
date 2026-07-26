// 外部包不依赖调用方的 link 编号、屏幕布局或内存槽位。
// 内存需求在同包 hardware.mplh 中自动声明；screen 由 import with 注入。

export class StatusPanel {
    public fun StatusPanel() {}

    public fun render(count: Int): Void {
        screen.fill(Color.green);
        screen.fillRect(4, 4, count, 8);
        screen.flush();
    }
}
