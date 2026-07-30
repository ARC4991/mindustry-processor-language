val turrets: Set<Building<Duo>> = Building.getAllDuo().where(_.enabled);
val primary: Building<Duo>? = turrets.get(0);

while (true) {
    if (primary != null) {
        val health: Float = primary.health;
        primary.setEnabled(false);
    }
}
