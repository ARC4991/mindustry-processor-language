fun rotate(source: (Int, Float)) {
    return (source[1], source[0]);
}

fun normalize(decimal: Bool) {
    if (decimal) {
        return (1.5, 2);
    }
    return (1, 2.5);
}

val original = (3, 4.5);
val rotated = rotate(original);
val normalized = normalize(true);
val mixed = [1, 2.5, 3];

Status.print("rotated=", rotated[0], ",", rotated[1],
    " normalized=", normalized[0], ",", normalized[1],
    " arraySize=", mixed.size);
