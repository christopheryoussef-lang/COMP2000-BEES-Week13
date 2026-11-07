import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import Components.CollisionBox;
import Components.Vec3;

public abstract class SceneObject implements Inspectable {
    private final String name;
    private Vec3 position;
    private final CollisionBox collider;

    protected SceneObject(String name, Vec3 position, Vec3 halfSize) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("An object needs a name.");
        this.name = name;
        this.position = Objects.requireNonNull(position);
        collider = new CollisionBox(position, halfSize);
    }

    public final String getName() { return name; }
    public final Vec3 getPosition() { return position; }
    public final CollisionBox getCollider() { return collider; }
    public boolean isActive() { return true; }

    protected final void setPosition(Vec3 position) {
        this.position = Objects.requireNonNull(position);
        collider.moveTo(position);
    }

    public Map<String, String> getStats() {
        Map<String, String> stats = new LinkedHashMap<>();
        stats.put("Type", getType());
        stats.put("Position", String.format(Locale.ROOT, "%.1f, %.1f, %.1f",
                position.x(), position.y(), position.z()));
        return stats;
    }

    protected static String amount(double value) { return String.format(Locale.ROOT, "%.1f", value); }
    protected static void requireAmount(double value) {
        if (!Double.isFinite(value) || value < 0) {
            throw new IllegalArgumentException("Amount must be finite and non-negative.");
        }
    }
}
