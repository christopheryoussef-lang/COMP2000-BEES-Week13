import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import Components.Vec3;

public final class Field extends Location implements Updatable {
    public static final int MAX_FLOWERS = 75;
    public static final int NATURAL_FLOWERS = 50;
    private final List<Flower> flowers = new ArrayList<>();
    private final Random random;
    private FlowerSelectionStrategy selectionStrategy;
    private final double radius;
    private double growthTimer;
    private int nextFlowerId = 1;

    public Field(Vec3 position, double radius, Random random) {
        this(position, radius, random, new NearestFlowerStrategy());
    }

    public Field(Vec3 position, double radius, Random random, FlowerSelectionStrategy selectionStrategy) {
        super("Flower meadow", position, new Vec3(radius, 0.1, radius));
        if (!Double.isFinite(radius) || radius <= 0) throw new IllegalArgumentException("Invalid field radius.");
        this.radius = radius;
        this.random = Objects.requireNonNull(random);
        this.selectionStrategy = Objects.requireNonNull(selectionStrategy);
    }

    public String getType() { return "Field"; }
    public List<Flower> getFlowers() { return Collections.unmodifiableList(flowers); }

    /** Existing reservations remain valid; the new policy applies to the next trip. */
    public void setSelectionStrategy(FlowerSelectionStrategy strategy) {
        selectionStrategy = Objects.requireNonNull(strategy);
    }

    public boolean spawnFlower() {
        if (flowers.size() >= MAX_FLOWERS) return false;
        double angle = random.nextDouble() * Math.PI * 2;
        double distance = Math.sqrt(4 + random.nextDouble() * Math.max(0, radius * radius - 4));
        distance = Math.min(distance, radius);
        Vec3 position = getPosition().add(Math.cos(angle) * distance, 0.5, Math.sin(angle) * distance);
        flowers.add(new Flower("Flower " + nextFlowerId++, position, 100));
        return true;
    }

    public int addFlowers(int count) {
        if (count < 0) throw new IllegalArgumentException("Flower count cannot be negative.");
        int added = 0;
        while (added < count && spawnFlower()) added++;
        return added;
    }

    public Flower reserveFlower(BeeWorker bee) {
        Flower best = selectionStrategy.choose(getFlowers(), bee.getPosition());
        return best != null && flowers.contains(best) && best.reserve(bee) ? best : null;
    }

    public void update(double deltaTime) {
        requireAmount(deltaTime);
        flowers.removeIf(flower -> !flower.isActive() && !flower.isOccupied());
        growthTimer += deltaTime;
        while (growthTimer >= 2) {
            growthTimer -= 2;
            if (flowers.size() < NATURAL_FLOWERS) spawnFlower();
        }
    }

    public Map<String, String> getStats() {
        Map<String, String> stats = super.getStats();
        stats.put("Flowers", Integer.toString(flowers.size()));
        stats.put("Pollen / nectar", amount(flowers.stream().mapToDouble(Flower::getPollen).sum()));
        return stats;
    }
}
