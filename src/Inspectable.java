import java.util.Map;
import Components.CollisionBox;
import Components.Vec3;

/** Common contract for object selection and the details panel. */
public interface Inspectable {
    String getName();
    String getType();
    Vec3 getPosition();
    CollisionBox getCollider();
    boolean isActive();
    Map<String, String> getStats();
}
