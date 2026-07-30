class Animal {
    public value: Int;

    public fun Animal(value: Int) {
        this.value = value;
    }

    public fun score(amount: Int) {
        return this.value + amount;
    }
}

class Dog extends Animal {
    public bonus: Int;

    public fun Dog(value: Int, bonus: Int) {
        super(value);
        this.bonus = bonus;
    }

    public fun score(amount: Int) {
        return super.score(amount) + this.bonus;
    }
}

fun classify(value: Animal) {
    return value.score(1);
}

fun classify(value: Dog) {
    return value.score(2);
}

val pet: Animal = new Dog(3, 4);
val virtualScore = pet.score(2);
val overloadScore = classify(new Dog(3, 4));
val values = List.of(virtualScore, overloadScore);

Status.print("virtual=", virtualScore, ", overload=", overloadScore,
    ", count=", values.size);
