import java.util.Comparator;
import java.util.List;
import Components.Vec3;

/** Chooses the richest available patch, then the nearest patch when nectar ties. */
public final class RichestFlowerStrategy implements FlowerSelectionStrategy {
    public Flower choose(List<Flower> flowers, Vec3 beePosition) {
        // This values the patch's remaining resource, not the worker's next load.
        // It can spread visits toward fresh flowers but may lengthen travel.
        return flowers.stream()
                .filter(flower -> flower.isActive() && !flower.isOccupied())
                .max(Comparator.comparingDouble(Flower::getPollen)
                        .thenComparingDouble(flower -> -beePosition.distanceTo(flower.getPosition()))
                        .thenComparing(Flower::getName, Comparator.reverseOrder()))
                .orElse(null);
    }
}
