import java.util.List;
import java.util.Map;
import Components.Vec3;

/** A ground visitor whose spray harms both bees and wasps, as in the UML. */
public final class Exterminator extends Enemy {
    public static final double SPRAY_RADIUS = 2.2;
    private int beesKilled;
    private int waspsKilled;
    private boolean spraying;

    public Exterminator(String name, Vec3 position, Hive hive, List<Enemy> enemies) {
        super(name, new Vec3(position.x(), 0.8, position.z()), new Vec3(0.4, 0.8, 0.4),
                hive, enemies, 150, 25);
    }

    public String getType() { return "Exterminator"; }
    public boolean isSpraying() { return spraying; }
    public int getBeesKilled() { return beesKilled; }
    public int getWaspsKilled() { return waspsKilled; }

    public void update(double deltaTime) {
        requireAmount(deltaTime);
        if (!isActive() || deltaTime == 0) return;
        age += deltaTime;
        if (!isActive()) return;
        SceneObject target = hive.nearestWasp(getPosition(), Double.POSITIVE_INFINITY);
        if (target == null) target = nearestBee();
        Vec3 destination = target == null ? hive.getPosition() : target.getPosition();
        setPosition(getPosition().moveTowards(new Vec3(destination.x(), 0.8, destination.z()), 1.4 * deltaTime));
        spraying = false;
        for (Bee bee : hive.getBees()) {
            if (bee.isActive() && inSpray(bee)) {
                spraying = true;
                bee.takeDamage(32 * deltaTime);
                if (!bee.isActive()) beesKilled++;
            }
        }
        for (Enemy enemy : enemies) {
            if (enemy instanceof Wasp && enemy.isActive() && inSpray(enemy)) {
                spraying = true;
                enemy.takeDamage(40 * deltaTime);
                if (!enemy.isActive()) waspsKilled++;
            }
        }
    }

    private boolean inSpray(SceneObject object) {
        double dx = getPosition().x() - object.getPosition().x();
        double dz = getPosition().z() - object.getPosition().z();
        return Math.hypot(dx, dz) <= SPRAY_RADIUS;
    }

    public Map<String, String> getStats() {
        Map<String, String> stats = super.getStats();
        stats.put("State", spraying ? "Spraying" : "Approaching");
        stats.put("Bees killed", Integer.toString(beesKilled));
        stats.put("Wasps killed", Integer.toString(waspsKilled));
        stats.put("Spray radius", amount(SPRAY_RADIUS) + " m");
        return stats;
    }
}
