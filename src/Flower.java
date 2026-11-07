import java.util.Map;
import Components.Vec3;

public final class Flower extends Location {
    private double pollen;
    private BeeWorker visitor;

    public Flower(String name, Vec3 position, double pollen) {
        super(name, position, new Vec3(0.35, 0.5, 0.35));
        requireAmount(pollen);
        this.pollen = pollen;
    }

    public String getType() { return "Flower"; }
    public double getPollen() { return pollen; }
    public boolean isActive() { return pollen > 1e-9; }
    public boolean isOccupied() { return visitor != null && visitor.isActive(); }

    public boolean reserve(BeeWorker bee) {
        if (bee == null || !bee.isActive() || !isActive() || (isOccupied() && visitor != bee)) return false;
        visitor = bee;
        return true;
    }

    public void release(BeeWorker bee) {
        if (visitor == bee) visitor = null;
    }

    public double harvest(BeeWorker bee, double requested) {
        requireAmount(requested);
        if (visitor != bee || !isOccupied()) return 0;
        double collected = Math.min(pollen, requested);
        pollen = Math.max(0, pollen - collected);
        return collected;
    }

    public Map<String, String> getStats() {
        Map<String, String> stats = super.getStats();
        stats.put("Pollen / nectar", amount(pollen));
        stats.put("Visitor", isOccupied() ? visitor.getName() : "Available");
        return stats;
    }
}
