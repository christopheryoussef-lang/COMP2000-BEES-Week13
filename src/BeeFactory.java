/** Factory centralizes creation of the two bee subclasses. */
public final class BeeFactory {
    public enum Kind { WORKER, QUEEN }
    private BeeFactory() { }

    public static Bee create(Kind kind, String name, Hive hive) {
        return switch (java.util.Objects.requireNonNull(kind)) {
            case WORKER -> new BeeWorker(name, hive);
            case QUEEN -> new BeeQueen(name, hive);
        };
    }
}
