while (true) {
    for (var unit : Unit.getAllDagger().where(_.alive).take(3)) {
        unit.move(20.0, 20.0);
    }
}
