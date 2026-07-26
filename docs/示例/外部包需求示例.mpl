// @mpl/status-panel 包的公开源码示意。

export requires {
    storage: Memory { minSize: 512, mode: Pool };
    screen: Display { minWidth: 176, minHeight: 176 };
}

export class StatusPanel {
    public fun StatusPanel() {}

    public fun render(count: Int): Void {
        storage[0] = count;
        screen.clear(Color.black);
        screen.fill(Color.green);
        screen.fillRect(4, 4, count, 8);
        screen.flush();
    }
}
