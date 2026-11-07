import java.util.Map;
import java.util.Objects;
import Components.Vec3;

/** Shared bee identity, movement, health and hive membership. */
public abstract class Bee extends SceneObject implements Updatable {
    protected final Hive hive;
    protected final double speed;
    private double health = 100;
    private double age;

    protected Bee(String name, Hive hive, double speed, double size) {
        super(name, Objects.requireNonNull(hive).getPosition(), new Vec3(size, size, size));
        this.hive = hive;
        this.speed = speed;
    }

    public final Hive getHive() { return hive; }
    public final double getHealth() { return health; }
    public final double getAge() { return age; }
    public final boolean isActive() { return health > 0; }

    protected final void growOlder(double deltaTime) { age += deltaTime; }
    protected final boolean moveTowards(Vec3 target, double deltaTime) {
        setPosition(getPosition().moveTowards(target, speed * deltaTime));
        return getPosition().distanceTo(target) < 1e-6;
    }

    public final void takeDamage(double damage) {
        requireAmount(damage);
        boolean wasAlive = isActive();
        health = Math.max(0, health - damage);
        if (wasAlive && !isActive()) onDeath();
    }

    protected void onDeath() { }

    public Map<String, String> getStats() {
        Map<String, String> stats = super.getStats();
        stats.put("Health", amount(health) + " / 100");
        stats.put("Age", amount(age) + " s");
        stats.put("Hive", hive.getName());
        return stats;
    }
}
