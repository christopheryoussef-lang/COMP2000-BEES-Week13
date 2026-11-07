import java.util.Map;
import Components.Vec3;

/** The brood stage is distinct from a flying adult bee. */
public final class Larvae extends SceneObject implements Updatable {
    public static final double MATURATION_TIME = 15;
    private double age;
    private boolean hatched;

    public Larvae(String name, Vec3 position) {
        super(name, position, new Vec3(0.18, 0.18, 0.18));
    }

    public String getType() { return "Larva"; }
    public boolean isActive() { return !hatched; }
    public double getAge() { return age; }
    public boolean isMature() { return age >= MATURATION_TIME - 1e-9; }
    public void markHatched() { hatched = true; }

    public void update(double deltaTime) {
        requireAmount(deltaTime);
        if (!hatched) age = Math.min(MATURATION_TIME, age + deltaTime);
    }

    public Map<String, String> getStats() {
        Map<String, String> stats = super.getStats();
        stats.put("Growth", amount(age / MATURATION_TIME * 100) + "%");
        stats.put("Hatches in", amount(MATURATION_TIME - age) + " s");
        stats.put("Food", "Provided by the hive");
        return stats;
    }
}
