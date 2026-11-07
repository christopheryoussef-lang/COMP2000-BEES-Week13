import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;
import Components.Vec3;

/** Owns the world and advances it in deterministic 1/60-second steps. */
public final class Simulation {
    public static final double STEP = 1.0 / 60;
    public static final int MAX_ENEMIES = 12;
    private final long seed;
    private final List<Enemy> enemies = new ArrayList<>();
    private Random random;
    private Field field;
    private Hive hive;
    private double accumulator;
    private double elapsed;
    private double threatTimer;
    private int nextEnemyId;
    private int wave;
    private int speed = 1;
    private boolean paused;
    private boolean autoThreats;
    private ForagingPolicy foragingPolicy;

    public Simulation(long seed) {
        this(seed, ForagingPolicy.NEAREST);
    }

    public Simulation(long seed, ForagingPolicy policy) {
        this.seed = seed;
        foragingPolicy = Objects.requireNonNull(policy);
        reset();
    }

    public void reset() {
        enemies.clear();
        random = new Random(seed);
        field = new Field(Vec3.ZERO, 8.5, random, foragingPolicy.strategy());
        field.addFlowers(Field.NATURAL_FLOWERS);
        hive = new Hive(new Vec3(0.4, 2.1, -1), field, enemies);
        hive.addBee(BeeFactory.Kind.QUEEN);
        for (int i = 0; i < 6; i++) hive.addBee(BeeFactory.Kind.WORKER);
        accumulator = elapsed = threatTimer = 0;
        nextEnemyId = 1;
        wave = 0;
        speed = 1;
        paused = autoThreats = false;
    }

    public Field getField() { return field; }
    public long getSeed() { return seed; }
    public ForagingPolicy getForagingPolicy() { return foragingPolicy; }
    public void setForagingPolicy(ForagingPolicy policy) {
        foragingPolicy = Objects.requireNonNull(policy);
        field.setSelectionStrategy(policy.strategy());
    }
    public Hive getHive() { return hive; }
    public List<Enemy> getEnemies() { return Collections.unmodifiableList(enemies); }
    public double getElapsed() { return elapsed; }
    public boolean isPaused() { return paused; }
    public int getSpeed() { return speed; }
    public boolean hasAutoThreats() { return autoThreats; }
    public void togglePause() { paused = !paused; }
    public void cycleSpeed() { speed = speed == 4 ? 1 : speed * 2; }
    public void toggleAutoThreats() { autoThreats = !autoThreats; threatTimer = 0; }

    public List<SceneObject> getSceneObjects() {
        List<SceneObject> objects = new ArrayList<>();
        objects.add(hive);
        objects.addAll(field.getFlowers());
        objects.addAll(hive.getBees());
        objects.addAll(hive.getLarvae());
        objects.addAll(enemies);
        return objects;
    }

    public Enemy spawnWasp() { return spawnEnemy(false); }
    public Enemy spawnExterminator() { return spawnEnemy(true); }

    private Enemy spawnEnemy(boolean exterminator) {
        if (enemies.size() >= MAX_ENEMIES) return null;
        double angle = random.nextDouble() * Math.PI * 2;
        Vec3 position = new Vec3(Math.cos(angle) * 9, 1.8, Math.sin(angle) * 9);
        Enemy enemy = exterminator
                ? new Exterminator("Exterminator " + nextEnemyId++, position, hive, enemies)
                : new Wasp("Wasp " + nextEnemyId++, position, hive, enemies);
        enemies.add(enemy);
        return enemy;
    }

    public void advance(double realSeconds) {
        if (!Double.isFinite(realSeconds) || realSeconds < 0) {
            throw new IllegalArgumentException("Elapsed time must be finite and non-negative.");
        }
        if (paused) return;
        accumulator += realSeconds * speed;
        while (accumulator + 1e-10 >= STEP) {
            tick();
            accumulator = Math.max(0, accumulator - STEP);
        }
    }

    private void tick() {
        elapsed += STEP;
        if (autoThreats) {
            threatTimer += STEP;
            if (threatTimer >= 25) {
                threatTimer -= 25;
                wave++;
                if (wave % 4 == 0) spawnExterminator();
                else spawnWasp();
            }
        }
        for (Enemy enemy : List.copyOf(enemies)) enemy.update(STEP);
        hive.update(STEP);
        field.update(STEP);
        enemies.removeIf(enemy -> !enemy.isActive());
    }

    public String summary() {
        return String.format(Locale.ROOT,
                "time=%.2f workers=%d queen=%s larvae=%d honey=%.2f delivered=%.2f flowers=%d enemies=%d hatched=%d deaths=%d",
                elapsed, hive.getWorkerCount(), hive.hasQueen(), hive.getLarvae().size(), hive.getHoney(),
                hive.getTotalCollected(), field.getFlowers().size(), enemies.size(), hive.getHatchedCount(), hive.getDeaths());
    }
}
