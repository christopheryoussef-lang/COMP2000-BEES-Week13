import java.util.Map;

/** Finite state machine for foraging, depositing, resting and defending. */
public final class BeeWorker extends Bee {
    public enum State { RESTING, FETCHING, COLLECTING, RETURNING, DEFENDING }
    public static final double CAPACITY = 50;
    public static final double COLLECTION_RATE = 10;
    private State state = State.RESTING;
    private Flower target;
    private double nectar;
    private double restTimer = 0.4;

    public BeeWorker(String name, Hive hive) { super(name, hive, 2.5, 0.3); }
    public String getType() { return "Worker bee"; }
    public State getState() { return state; }
    public double getNectar() { return nectar; }
    public Flower getTarget() { return target; }

    public void update(double deltaTime) {
        requireAmount(deltaTime);
        if (!isActive() || deltaTime == 0) return;
        growOlder(deltaTime);
        Wasp threat = hive.nearestWasp(getPosition(), 1.5);
        if (threat != null) {
            releaseFlower();
            state = State.DEFENDING;
            if (getPosition().distanceTo(threat.getPosition()) <= 0.8) threat.takeDamage(28 * deltaTime);
            else moveTowards(threat.getPosition(), deltaTime);
            return;
        }
        if (state == State.DEFENDING) state = nectar > 0 ? State.RETURNING : State.RESTING;

        switch (state) {
            case RESTING -> {
                restTimer -= deltaTime;
                if (restTimer <= 0) {
                    target = hive.getField().reserveFlower(this);
                    if (target != null) state = State.FETCHING;
                    else restTimer = 0.5;
                }
            }
            case FETCHING -> {
                if (target == null || !target.isActive()) returnHome();
                else if (moveTowards(target.getPosition().add(0, 0.3, 0), deltaTime)) state = State.COLLECTING;
            }
            case COLLECTING -> {
                if (target == null || !target.isActive()) returnHome();
                else {
                    nectar += target.harvest(this, Math.min(CAPACITY - nectar, COLLECTION_RATE * deltaTime));
                    if (nectar >= CAPACITY - 1e-9 || !target.isActive()) returnHome();
                }
            }
            case RETURNING -> {
                if (moveTowards(hive.getPosition(), deltaTime)) {
                    hive.deposit(nectar);
                    nectar = 0;
                    state = State.RESTING;
                    restTimer = 0.75;
                }
            }
            case DEFENDING -> { /* handled before the switch */ }
        }
    }

    private void returnHome() {
        releaseFlower();
        state = State.RETURNING;
    }

    private void releaseFlower() {
        if (target != null) target.release(this);
        target = null;
    }

    protected void onDeath() { releaseFlower(); }

    public Map<String, String> getStats() {
        Map<String, String> stats = super.getStats();
        stats.put("State", state.toString());
        stats.put("Nectar carried", amount(nectar) + " / 50");
        stats.put("Destination", target == null ? "Hive / resting" : target.getName());
        return stats;
    }
}
