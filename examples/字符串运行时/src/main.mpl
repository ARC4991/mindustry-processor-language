fun copy(value: String): String {
    return value;
}

var prefix: String = "M";
prefix = "MP";

val banner: String = prefix + "L 字符串运行时";
val copied: String = copy(banner);
val expected: String = "MPL 字符串运行时";
val same: Bool = copied == expected;

Status.print(banner, "，UTF-16 长度=", copied.length, "，内容相等=", same);
