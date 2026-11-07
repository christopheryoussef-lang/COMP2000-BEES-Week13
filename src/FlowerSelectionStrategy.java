import java.util.List;
import Components.Vec3;

/** Strategy contract lets the field change how available flowers are chosen. */
@FunctionalInterface
public interface FlowerSelectionStrategy {
    Flower choose(List<Flower> flowers, Vec3 beePosition);
}
