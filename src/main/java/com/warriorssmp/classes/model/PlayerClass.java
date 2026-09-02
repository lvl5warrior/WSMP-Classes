package com.warriorssmp.classes.model;

public enum PlayerClass {
    WARRIOR("Warrior", "§c"),
    MAGE("Mage", "§b"),
    ARCHER("Archer", "§a");

    private final String displayName;
    private final String color;

    PlayerClass(String displayName, String color) {
        this.displayName = displayName;
        this.color = color;
    }

    public String displayName() {
        return displayName;
    }

    public String color() {
        return color;
    }

    public String coloredName() {
        return color + displayName;
    }
}
