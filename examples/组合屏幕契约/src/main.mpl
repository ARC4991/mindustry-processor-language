MainScreen.clear(Color.black);
MainScreen.fill(Color.green);
MainScreen.fillRect(72, 20, 16, 40);
MainScreen.stroke(Color.white);
MainScreen.line(0, 0, MainScreen.width, MainScreen.height);
AlertBoard.print("组合屏幕尺寸：", MainScreen.width, "x", MainScreen.height);
