class Counter {
    private value: Int;

    public fun Counter(initial: Int) {
        this.value = initial;
    }

    public fun add(amount: Int): Int {
        this.value += amount;
        return this.value;
    }
}

fun temporaryTotal(initial: Int): Int {
    val temporary = new Counter(initial);
    return temporary.add(4);
}

val first = new Counter(1);
val second = new Counter(10);
val firstValue = first.add(2);
val secondValue = second.add(5);
val temporaryValue = temporaryTotal(20);
val distinct = first !== second;

Status.print("first=", firstValue, ", second=", secondValue,
    ", temporary=", temporaryValue, ", distinct=", distinct);
