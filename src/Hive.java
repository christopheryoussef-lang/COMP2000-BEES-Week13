import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import Components.Vec3;

public final class Hive extends Location implements Updatable {
    public static final int MAX_POPULATION = 60;
    public static final double EGG_COST = 8;
    private final Field field;
    private final List<Bee> bees = new ArrayList<>();
    private final List<Larvae> larvae = new ArrayList<>();
    private final List<Enemy> enemies;
    private double honey = 20;
    private double totalCollected;
    private int nextBeeId = 1;
    private int nextLarvaId = 1;
    private int hatchedCount;
    private int deaths;

    public Hive(Vec3 position, Field field, List<Enemy> enemies) {
        super("Meadow hive", position, new Vec3(0.55, 0.6, 0.55));
        this.field = Objects.requireNonNull(field);
        this.enemies = Objects.requireNonNull(enemies);
    }

    public String getType() { return "Hive"; }
    public Field getField() { return field; }
    public List<Bee> getBees() { return Collections.unmodifiableList(bees); }
    public List<Larvae> getLarvae() { return Collections.unmodifiableList(larvae); }
    public double getHoney() { return honey; }
    public double getTotalCollected() { return totalCollected; }
    public int getHatchedCount() { return hatchedCount; }
    public int getDeaths() { return deaths; }
    public long getWorkerCount() { return bees.stream().filter(b -> b instanceof BeeWorker && b.isActive()).count(); }
    public boolean hasQueen() { return bees.stream().anyMatch(b -> b instanceof BeeQueen && b.isActive()); }
    public long livingCount() { return bees.stream().filter(Bee::isActive).count(); }

    public Bee addBee(BeeFactory.Kind kind) {
        if (livingCount() + larvae.size() >= MAX_POPULATION) return null;
        if (kind == BeeFactory.Kind.QUEEN && hasQueen()) return null;
        Bee bee = BeeFactory.create(kind, (kind == BeeFactory.Kind.QUEEN ? "Queen " : "Worker ") + nextBeeId++, this);
        bees.add(bee);
        return bee;
    }

    public void deposit(double nectar) {
        requireAmount(nectar);
        honey += nectar;
        totalCollected += nectar;
    }

    public double stealHoney(double requested) {
        requireAmount(requested);
        double stolen = Math.min(honey, requested);
        honey = Math.max(0, honey - stolen);
        return stolen;
    }

    public boolean layEgg() {
        if (!hasQueen() || honey < EGG_COST || livingCount() + larvae.size() >= MAX_POPULATION) return false;
        honey -= EGG_COST;
        double angle = nextLarvaId * 2.4;
        larvae.add(new Larvae("Larva " + nextLarvaId++,
                getPosition().add(Math.cos(angle) * 0.7, -0.35, Math.sin(angle) * 0.7)));
        return true;
    }

    public Wasp nearestWasp(Vec3 position, double radius) {
        Wasp nearest = null;
        for (Enemy enemy : enemies) {
            if (enemy instanceof Wasp wasp && wasp.isActive()) {
                double distance = position.distanceTo(wasp.getPosition());
                if (distance < radius) { radius = distance; nearest = wasp; }
            }
        }
        return nearest;
    }

    public void update(double deltaTime) {
        requireAmount(deltaTime);
        // Snapshot permits a queen to add brood without invalidating this traversal.
        for (Bee bee : List.copyOf(bees)) bee.update(deltaTime);
        int before = bees.size();
        bees.removeIf(bee -> !bee.isActive());
        deaths += before - bees.size();

        for (Larvae larva : List.copyOf(larvae)) {
            larva.update(deltaTime);
            if (larva.isMature()) {
                // Removing the brood slot first makes room at the population cap.
                larvae.remove(larva);
                BeeFactory.Kind kind = hasQueen() ? BeeFactory.Kind.WORKER : BeeFactory.Kind.QUEEN;
                Bee adult = addBee(kind);
                if (adult != null) {
                    larva.markHatched();
                    hatchedCount++;
                } else larvae.add(larva);
            }
        }
    }

    public Map<String, String> getStats() {
        Map<String, String> stats = super.getStats();
        stats.put("Honey stored", amount(honey));
        stats.put("Nectar delivered", amount(totalCollected));
        stats.put("Workers", Long.toString(getWorkerCount()));
        stats.put("Queen", hasQueen() ? "Present" : "Missing");
        stats.put("Larvae", Integer.toString(larvae.size()));
        stats.put("Adults hatched", Integer.toString(hatchedCount));
        stats.put("Bees lost", Integer.toString(deaths));
        stats.put("Population limit", Integer.toString(MAX_POPULATION));
        return stats;
    }
}
