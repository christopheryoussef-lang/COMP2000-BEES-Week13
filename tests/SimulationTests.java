import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import Components.CollisionBox;
import Components.Vec3;

/** Dependency-free behavioral regression tests. No window or native library required. */
public final class SimulationTests {
    private static int passed;

    public static void main(String[] args) {
        test("movement stops at its destination", () -> {
            Vec3 destination = new Vec3(1, 0, 0);
            check(Vec3.ZERO.moveTowards(destination, 20).equals(destination), "Overshot the destination");
            close(Vec3.ZERO.moveTowards(destination, 0.25).x(), 0.25);
        });
        test("invalid coordinates and movement are rejected", () -> {
            rejects(() -> new Vec3(Double.NaN, 0, 0));
            rejects(() -> Vec3.ZERO.moveTowards(new Vec3(1, 0, 0), -1));
            rejects(() -> new CollisionBox(Vec3.ZERO, Vec3.ZERO));
        });
        test("ray picker chooses nearest active object regardless of list order", () -> {
            Flower near = new Flower("Near", new Vec3(0, 0, 2), 100);
            Flower far = new Flower("Far", new Vec3(0, 0, 5), 100);
            Flower empty = new Flower("Empty", new Vec3(0, 0, 1), 0);
            ObjectPicker<Flower> picker = new ObjectPicker<>();
            check(picker.pick(List.of(far, empty, near), Vec3.ZERO, new Vec3(0, 0, 1)).orElseThrow() == near,
                    "Wrong object selected");
            check(picker.pick(List.of(near), Vec3.ZERO, new Vec3(0, 0, -1)).isEmpty(), "Selected behind camera");
        });
        test("collision handles parallel, inside and zero-direction rays", () -> {
            CollisionBox box = new CollisionBox(Vec3.ZERO, new Vec3(1, 1, 1));
            close(box.rayDistance(Vec3.ZERO, new Vec3(1, 0, 0)), 0);
            close(box.rayDistance(new Vec3(-3, 1, 0), new Vec3(1, 0, 0)), 2);
            check(Double.isInfinite(box.rayDistance(new Vec3(-3, 2, 0), new Vec3(1, 0, 0))), "Parallel ray hit");
            check(Double.isInfinite(box.rayDistance(Vec3.ZERO, Vec3.ZERO)), "Zero ray hit");
        });
        test("hives and colliders do not share mutable state", () -> {
            Hive a = hive(0), b = hive(0);
            Bee bee = a.addBee(BeeFactory.Kind.WORKER);
            check(b.getBees().isEmpty(), "Bees leaked between hives");
            check(bee.getCollider() != a.getCollider(), "Shared collider");
            Vec3 before = a.getCollider().center();
            a.getField().addFlowers(1);
            advanceHive(a, 1);
            check(a.getCollider().center().equals(before), "Bee movement changed hive collider");
            rejectsUnsupported(() -> a.getBees().clear());
        });
        test("empty flower field leaves a worker waiting without recursion", () -> {
            Hive hive = hive(0);
            BeeWorker bee = (BeeWorker) hive.addBee(BeeFactory.Kind.WORKER);
            advanceHive(hive, 20);
            check(bee.getState() == BeeWorker.State.RESTING, "Worker did not wait");
            close(bee.getNectar(), 0);
        });
        test("flower reservations prevent multiple workers taking the same flower", () -> {
            Hive hive = hive(1);
            BeeWorker a = (BeeWorker) hive.addBee(BeeFactory.Kind.WORKER);
            BeeWorker b = (BeeWorker) hive.addBee(BeeFactory.Kind.WORKER);
            Flower flower = hive.getField().reserveFlower(a);
            check(flower != null, "Could not reserve available flower");
            check(hive.getField().reserveFlower(b) == null, "Double reservation");
            close(flower.harvest(b, 10), 0);
            flower.release(a);
            check(hive.getField().reserveFlower(b) == flower, "Reservation not released");
        });
        test("field applies its supplied flower selection strategy", () -> {
            Field field = new Field(Vec3.ZERO, 3, new Random(7),
                    (flowers, position) -> flowers.isEmpty() ? null : flowers.get(flowers.size() - 1));
            field.addFlowers(3);
            Hive hive = new Hive(Vec3.ZERO, field, new ArrayList<>());
            BeeWorker bee = (BeeWorker) hive.addBee(BeeFactory.Kind.WORKER);
            check(field.reserveFlower(bee) == field.getFlowers().get(2), "Strategy was ignored");
        });
        test("harvesting cannot overdraw a flower", () -> {
            Hive hive = hive(1);
            BeeWorker worker = (BeeWorker) hive.addBee(BeeFactory.Kind.WORKER);
            Flower flower = hive.getField().reserveFlower(worker);
            close(flower.harvest(worker, 150), 100);
            close(flower.getPollen(), 0);
            close(flower.harvest(worker, 1), 0);
            flower.release(worker);
            hive.getField().update(0);
            check(hive.getField().getFlowers().isEmpty(), "Depleted flower not removed safely");
        });
        test("worker completes multiple trips and conserves nectar", () -> {
            Hive hive = hive(1);
            BeeWorker worker = (BeeWorker) hive.addBee(BeeFactory.Kind.WORKER);
            advanceHive(hive, 40);
            close(hive.getTotalCollected(), 100);
            close(hive.getHoney(), 120);
            close(worker.getNectar(), 0);
            Flower flower = hive.getField().getFlowers().get(0);
            close(flower.getPollen(), 0);
            check(!flower.isOccupied(), "Worker left flower occupied");
        });
        test("death releases a worker's reserved flower", () -> {
            Hive hive = hive(1);
            BeeWorker worker = (BeeWorker) hive.addBee(BeeFactory.Kind.WORKER);
            advanceHive(hive, 0.8);
            Flower flower = worker.getTarget();
            check(flower != null && flower.isOccupied(), "Worker did not reserve flower");
            worker.takeDamage(100);
            check(!flower.isOccupied() && worker.getTarget() == null, "Death leaked reservation");
            hive.update(Simulation.STEP);
            check(hive.getBees().isEmpty() && hive.getDeaths() == 1, "Death not recorded");
        });
        test("queen eggs consume food and larvae mature into workers", () -> {
            Hive hive = hive(0);
            BeeQueen queen = (BeeQueen) hive.addBee(BeeFactory.Kind.QUEEN);
            advanceHive(hive, 50);
            check(queen.getEggsLaid() == 2, "Incorrect egg count with 20 initial honey");
            check(hive.getWorkerCount() == 2 && hive.getHatchedCount() == 2, "Larvae failed to hatch");
            close(hive.getHoney(), 4);
            check(hive.getLarvae().isEmpty(), "Hatched larvae remained in brood");
        });
        test("a maturing larva replaces a dead queen", () -> {
            Hive hive = hive(0);
            Bee queen = hive.addBee(BeeFactory.Kind.QUEEN);
            check(hive.layEgg(), "Could not lay egg");
            queen.takeDamage(100);
            advanceHive(hive, 16);
            check(hive.hasQueen(), "Queen succession failed");
            check(hive.livingCount() == 1, "Unexpected adults");
        });
        test("population cap permits a reserved brood slot to hatch", () -> {
            Hive hive = hive(0);
            hive.addBee(BeeFactory.Kind.QUEEN);
            for (int i = 0; i < 58; i++) hive.addBee(BeeFactory.Kind.WORKER);
            check(hive.layEgg(), "Final brood slot rejected");
            check(hive.addBee(BeeFactory.Kind.WORKER) == null, "Population limit exceeded");
            advanceHive(hive, 16);
            check(hive.livingCount() == 60 && hive.getLarvae().isEmpty(), "Larva stuck at population cap");
            check(hive.addBee(BeeFactory.Kind.QUEEN) == null, "Duplicate queen added");
        });
        test("wasps can kill bees and steal only available honey", () -> {
            List<Enemy> enemies = new ArrayList<>();
            Hive hive = new Hive(Vec3.ZERO, new Field(Vec3.ZERO, 3, new Random(4)), enemies);
            hive.addBee(BeeFactory.Kind.QUEEN);
            Wasp wasp = new Wasp("Wasp", Vec3.ZERO, hive, enemies);
            enemies.add(wasp);
            for (int i = 0; i < 600; i++) wasp.update(Simulation.STEP);
            check(wasp.getBeesKilled() == 1, "Wasp did not attack");
            close(wasp.getHoneyStolen(), 20);
            close(hive.getHoney(), 0);
        });
        test("worker bees defend against nearby wasps", () -> {
            List<Enemy> enemies = new ArrayList<>();
            Hive hive = new Hive(Vec3.ZERO, new Field(Vec3.ZERO, 3, new Random(4)), enemies);
            Bee worker = hive.addBee(BeeFactory.Kind.WORKER);
            Wasp wasp = new Wasp("Wasp", Vec3.ZERO, hive, enemies);
            enemies.add(wasp);
            for (int i = 0; i < 240; i++) { wasp.update(Simulation.STEP); hive.update(Simulation.STEP); }
            check(!wasp.isActive(), "Worker did not defend");
            check(worker.isActive(), "Worker unexpectedly died");
        });
        test("exterminator spray affects both bees and wasps", () -> {
            List<Enemy> enemies = new ArrayList<>();
            Hive hive = new Hive(Vec3.ZERO, new Field(Vec3.ZERO, 3, new Random(4)), enemies);
            Bee worker = hive.addBee(BeeFactory.Kind.WORKER);
            Wasp wasp = new Wasp("Wasp", new Vec3(0, 1, 0), hive, enemies);
            Exterminator visitor = new Exterminator("Visitor", Vec3.ZERO, hive, enemies);
            enemies.add(wasp); enemies.add(visitor);
            for (int i = 0; i < 240; i++) visitor.update(Simulation.STEP);
            check(!worker.isActive() && !wasp.isActive(), "Spray did not hit both types");
            check(visitor.getBeesKilled() == 1 && visitor.getWaspsKilled() == 1, "Spray kills counted incorrectly");
        });
        test("expired enemies are inactive and removed", () -> {
            Simulation simulation = new Simulation(2000);
            Enemy wasp = simulation.spawnWasp();
            Enemy visitor = simulation.spawnExterminator();
            simulation.advance(60);
            check(!wasp.isActive() && !visitor.isActive(), "Enemy outlived visit");
            check(simulation.getEnemies().isEmpty(), "Expired enemy remained in world");
        });
        test("fixed steps produce the same result at different render rates", () -> {
            Simulation a = new Simulation(2000), b = new Simulation(2000);
            a.advance(45);
            for (int i = 0; i < 1350; i++) b.advance(1.0 / 30);
            check(a.summary().equals(b.summary()), "Frame rate changed simulation result");
            for (int i = 0; i < a.getHive().getBees().size(); i++) {
                close(a.getHive().getBees().get(i).getPosition().distanceTo(b.getHive().getBees().get(i).getPosition()), 0);
            }
        });
        test("pause freezes the world and speed changes simulation time", () -> {
            Simulation simulation = new Simulation(1);
            simulation.advance(1);
            String before = simulation.summary();
            simulation.togglePause();
            simulation.advance(10);
            check(simulation.summary().equals(before), "Paused simulation advanced");
            simulation.cycleSpeed();
            simulation.togglePause();
            simulation.advance(2);
            close(simulation.getElapsed(), 5);
        });
        test("reset restores a reproducible initial world", () -> {
            Simulation simulation = new Simulation(2000);
            String before = simulation.summary();
            Vec3 firstFlower = simulation.getField().getFlowers().get(0).getPosition();
            simulation.spawnWasp(); simulation.toggleAutoThreats(); simulation.advance(30);
            simulation.cycleSpeed(); simulation.togglePause(); simulation.reset();
            check(simulation.summary().equals(before), "Reset retained world state");
            check(firstFlower.equals(simulation.getField().getFlowers().get(0).getPosition()), "Reset changed seed");
            check(!simulation.isPaused() && !simulation.hasAutoThreats() && simulation.getSpeed() == 1, "Reset retained controls");
        });
        test("flower and enemy limits prevent unlimited spawning", () -> {
            Simulation simulation = new Simulation(9);
            simulation.getField().addFlowers(1000);
            check(simulation.getField().getFlowers().size() == Field.MAX_FLOWERS, "Flower cap failed");
            for (int i = 0; i < 100; i++) simulation.spawnWasp();
            check(simulation.getEnemies().size() == Simulation.MAX_ENEMIES, "Enemy cap failed");
            check(simulation.spawnExterminator() == null, "Enemy cap bypassed");
        });
        test("invalid amounts and time fail clearly", () -> {
            Simulation simulation = new Simulation(1);
            rejects(() -> simulation.advance(-1));
            rejects(() -> simulation.advance(Double.NaN));
            rejects(() -> simulation.getHive().deposit(-1));
            rejects(() -> simulation.getHive().stealHoney(Double.POSITIVE_INFINITY));
            rejects(() -> simulation.getField().addFlowers(-1));
        });
        test("automatic threats continue in an empty colony without a crash", () -> {
            Simulation simulation = new Simulation(2000);
            for (Bee bee : simulation.getHive().getBees()) bee.takeDamage(100);
            simulation.toggleAutoThreats();
            simulation.advance(120);
            check(simulation.getHive().livingCount() == 0, "Unexpected adults");
            check(!simulation.getEnemies().isEmpty(), "Automatic threats did not spawn");
            check(simulation.getHive().getHoney() >= 0, "Honey became negative");
        });
        test("30-minute simulation keeps resources and populations bounded", () -> {
            Simulation simulation = new Simulation(2000);
            simulation.toggleAutoThreats();
            for (int i = 0; i < 1800; i++) {
                simulation.advance(1);
                check(simulation.getHive().getHoney() >= 0 && Double.isFinite(simulation.getHive().getHoney()), "Invalid honey");
                check(simulation.getHive().livingCount() + simulation.getHive().getLarvae().size() <= Hive.MAX_POPULATION, "Population cap failed");
                check(simulation.getEnemies().size() <= Simulation.MAX_ENEMIES, "Enemy cap failed");
                for (Flower flower : simulation.getField().getFlowers()) check(flower.getPollen() >= 0, "Negative pollen");
            }
            System.out.println("  SOAK " + simulation.summary());
        });
        System.out.println("PASS: " + passed + " behavioral tests.");
    }

    private static Hive hive(int flowers) {
        Field field = new Field(Vec3.ZERO, 3, new Random(7));
        field.addFlowers(flowers);
        return new Hive(Vec3.ZERO, field, new ArrayList<>());
    }

    private static void advanceHive(Hive hive, double seconds) {
        for (int i = 0; i < Math.round(seconds / Simulation.STEP); i++) hive.update(Simulation.STEP);
    }

    private static void test(String name, Runnable action) {
        try {
            action.run();
            passed++;
            System.out.println("PASS " + name);
        } catch (RuntimeException | AssertionError error) {
            throw new AssertionError("FAIL " + name + ": " + error.getMessage(), error);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void close(double actual, double expected) {
        check(Math.abs(actual - expected) < 1e-6, "Expected " + expected + ", got " + actual);
    }

    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Expected IllegalArgumentException");
    }

    private static void rejectsUnsupported(Runnable action) {
        try { action.run(); } catch (UnsupportedOperationException expected) { return; }
        throw new AssertionError("Mutable collection escaped");
    }
}
