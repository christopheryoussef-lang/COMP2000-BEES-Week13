import java.util.Locale;

/** Named, stateless Strategy instances used by the UI, CLI and experiments. */
public enum ForagingPolicy {
    NEAREST("nearest", "Nearest flower", new NearestFlowerStrategy()),
    RICHEST("richest", "Richest flower", new RichestFlowerStrategy());

    private final String key;
    private final String label;
    private final FlowerSelectionStrategy strategy;

    ForagingPolicy(String key, String label, FlowerSelectionStrategy strategy) {
        this.key = key;
        this.label = label;
        this.strategy = strategy;
    }

    public String key() { return key; }
    public String label() { return label; }
    public FlowerSelectionStrategy strategy() { return strategy; }
    public ForagingPolicy next() { return this == NEAREST ? RICHEST : NEAREST; }

    public static ForagingPolicy parse(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "nearest" -> NEAREST;
            case "richest" -> RICHEST;
            default -> throw new IllegalArgumentException("--policy must be nearest or richest.");
        };
    }
}
