import java.util.List;
import java.util.Map;
import java.util.Objects;
import Components.Vec3;

/** Shared enemy health and limited visit lifetime. */
public abstract class Enemy extends SceneObject implements Updatable {
    protected final Hive hive;
    protected final List<Enemy> enemies;
    protected double age;
    private double health;
    private final double lifetime;

    protected Enemy(String name, Vec3 position, Vec3 halfSize, Hive hive,
            List<Enemy> enemies, double health, double lifetime) {
        super(name, position, halfSize);
        this.hive = Objects.requireNonNull(hive);
        this.enemies = Objects.requireNonNull(enemies);
        this.health = health;
        this.lifetime = lifetime;
    }

    public final boolean isActive() { return health > 0 && age < lifetime; }
    public final double getHealth() { return health; }
    public final void takeDamage(double damage) {
        requireAmount(damage);
        health = Math.max(0, health - damage);
    }

    protected final Bee nearestBee() {
        Bee target = null;
        double distance = Double.POSITIVE_INFINITY;
        for (Bee bee : hive.getBees()) {
            if (!bee.isActive()) continue;
            double candidate = getPosition().distanceTo(bee.getPosition());
            if (candidate < distance) { distance = candidate; target = bee; }
        }
        return target;
    }

    public Map<String, String> getStats() {
        Map<String, String> stats = super.getStats();
        stats.put("Health", amount(health));
        stats.put("Visit remaining", amount(Math.max(0, lifetime - age)) + " s");
        return stats;
    }
}
