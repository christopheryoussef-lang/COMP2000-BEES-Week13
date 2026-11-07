import java.util.Collection;
import java.util.Optional;
import Components.Vec3;

/** Bounded generics let the same picker select bees, locations, or all objects. */
public final class ObjectPicker<T extends Inspectable> {
    public Optional<T> pick(Collection<? extends T> objects, Vec3 origin, Vec3 direction) {
        T selected = null;
        double nearest = Double.POSITIVE_INFINITY;
        for (T object : objects) {
            if (!object.isActive()) continue;
            double distance = object.getCollider().rayDistance(origin, direction);
            if (distance < nearest) {
                nearest = distance;
                selected = object;
            }
        }
        return Optional.ofNullable(selected);
    }
}
