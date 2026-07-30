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

fun createCounter(initial: Int): Counter {
    return new Counter(initial);
}

fun pooledTotal(): Int {
    val left = createCounter(1);
    val right = createCounter(3);
    return left.add(right.add(1));
}

val first = new Counter(1);
val second = new Counter(10);
val firstValue = first.add(2);
val secondValue = second.add(5);
val temporaryValue = temporaryTotal(20);
val pooledValue = pooledTotal();
val distinct = first !== second;

Status.print("first=", firstValue, ", second=", secondValue,
    ", temporary=", temporaryValue, ", pooled=", pooledValue, ", distinct=", distinct);
