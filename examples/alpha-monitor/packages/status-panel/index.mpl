// 外部包不依赖调用方的 link 编号或屏幕布局。
// requires 同时声明最小参数，并创建包内只读的 storage、screen 绑定。

export requires {
    storage: Memory { minSize: 512, mode: Pool };
    screen: Display { minWidth: 176, minHeight: 176 };
}

export class StatusPanel {
    public fun StatusPanel() {}

    public fun render(count: Int): Void {
        storage[0] = count;
        screen.fill(Color.green);
        screen.fillRect(4, 4, count, 8);
        screen.flush();
    }
}
