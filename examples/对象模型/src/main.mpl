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

val first = new Counter(1);
val second = new Counter(10);
val firstValue = first.add(2);
val secondValue = second.add(5);
val distinct = first !== second;

Status.print("first=", firstValue, ", second=", secondValue, ", distinct=", distinct);
