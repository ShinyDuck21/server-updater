package dev.shinyduck.mincecraft;

public enum Modloaders {
    ANY(0),
    FORGE(1),
    CAULDRON(2),
    LITELOADER(3),
    FABRIC(4),
    QUILT(5),
    NEOFORGE(6);

    private final int id;

    Modloaders(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }
}