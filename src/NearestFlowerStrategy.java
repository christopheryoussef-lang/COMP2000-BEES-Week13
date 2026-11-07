import java.util.List;
import Components.Vec3;

public final class NearestFlowerStrategy implements FlowerSelectionStrategy {
    public Flower choose(List<Flower> flowers, Vec3 beePosition) {
        Flower best = null;
        double nearest = Double.POSITIVE_INFINITY;
        for (Flower flower : flowers) {
            if (!flower.isActive() || flower.isOccupied()) continue;
            double distance = beePosition.distanceTo(flower.getPosition());
            if (distance < nearest) { best = flower; nearest = distance; }
        }
        return best;
    }
}
