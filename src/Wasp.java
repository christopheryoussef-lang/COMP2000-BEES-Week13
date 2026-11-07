import java.util.List;
import java.util.Map;
import Components.Vec3;

/** Wasps hunt bees and raid honey when close enough to the hive. */
public final class Wasp extends Enemy {
    private int beesKilled;
    private double honeyStolen;
    private Bee target;

    public Wasp(String name, Vec3 position, Hive hive, List<Enemy> enemies) {
        super(name, position, new Vec3(0.35, 0.3, 0.35), hive, enemies, 70, 55);
    }

    public String getType() { return "Wasp"; }
    public int getBeesKilled() { return beesKilled; }
    public double getHoneyStolen() { return honeyStolen; }

    public void update(double deltaTime) {
        requireAmount(deltaTime);
        if (!isActive() || deltaTime == 0) return;
        age += deltaTime;
        if (!isActive()) return;
        if (target == null || !target.isActive()) target = nearestBee();
        Vec3 destination = target == null ? hive.getPosition() : target.getPosition();
        setPosition(getPosition().moveTowards(destination, 2 * deltaTime));
        if (target != null && getPosition().distanceTo(target.getPosition()) <= 0.7) {
            target.takeDamage(16 * deltaTime);
            if (!target.isActive()) { beesKilled++; target = null; }
        }
        if (getPosition().distanceTo(hive.getPosition()) < 1.3) {
            honeyStolen += hive.stealHoney(3 * deltaTime);
        }
    }

    public Map<String, String> getStats() {
        Map<String, String> stats = super.getStats();
        stats.put("Target", target == null ? "Hive" : target.getName());
        stats.put("Bees killed", Integer.toString(beesKilled));
        stats.put("Honey stolen", amount(honeyStolen));
        return stats;
    }
}
