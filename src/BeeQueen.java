import java.util.Map;

/** A queen lays a fed larva every twelve seconds while the hive can support it. */
public final class BeeQueen extends Bee {
    public static final double EGG_INTERVAL = 12;
    private double eggTimer;
    private int eggsLaid;

    public BeeQueen(String name, Hive hive) {
        super(name, hive, 1, 0.4);
        setPosition(hive.getPosition().add(-0.8, 0.5, 0));
    }

    public String getType() { return "Queen bee"; }
    public int getEggsLaid() { return eggsLaid; }

    public void update(double deltaTime) {
        requireAmount(deltaTime);
        if (!isActive() || deltaTime == 0) return;
        growOlder(deltaTime);
        eggTimer = Math.min(EGG_INTERVAL, eggTimer + deltaTime);
        if (eggTimer >= EGG_INTERVAL && hive.layEgg()) {
            eggsLaid++;
            eggTimer = 0;
        }
    }

    public Map<String, String> getStats() {
        Map<String, String> stats = super.getStats();
        stats.put("Eggs laid", Integer.toString(eggsLaid));
        stats.put("Next egg", amount(EGG_INTERVAL - eggTimer) + " s");
        stats.put("Food per egg", amount(Hive.EGG_COST) + " honey");
        return stats;
    }
}
