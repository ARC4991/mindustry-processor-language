// @mpl/status-panel 包的公开源码示意。
// 它的硬件要求位于同包的“外部包硬件声明示例.mplh”。

export class StatusPanel {
    public fun StatusPanel() {}

    public fun render(count: Int): Void {
        screen.clear(Color.black);
        screen.fill(Color.green);
        screen.fillRect(4, 4, count, 8);
    }
}
