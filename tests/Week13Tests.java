import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import Components.Vec3;

/** Behavioral checks for policy choices, isolated parallel trials and camera picking. */
public final class Week13Tests {
    private static int passed;
    private Week13Tests() { }

    public static void main(String[] args) throws Exception {
        test("policies make distinct choices on depleted versus rich patches", () -> {
            Flower near = new Flower("Near", new Vec3(1, 0, 0), 10);
            Flower rich = new Flower("Rich", new Vec3(5, 0, 0), 100);
            List<Flower> flowers = List.of(near, rich);
            check(ForagingPolicy.NEAREST.strategy().choose(flowers, Vec3.ZERO) == near, "Nearest ignored distance");
            check(ForagingPolicy.RICHEST.strategy().choose(flowers, Vec3.ZERO) == rich, "Richest ignored nectar");
        });
        test("richest strategy excludes depleted and reserved flowers and breaks ties by distance", () -> {
            Hive hive = new Simulation(1).getHive();
            BeeWorker worker = (BeeWorker) hive.getBees().get(1);
            Flower reserved = new Flower("Reserved", Vec3.ZERO, 200);
            check(reserved.reserve(worker), "Reservation setup failed");
            Flower empty = new Flower("Empty", Vec3.ZERO, 0);
            Flower far = new Flower("Far", new Vec3(10, 0, 0), 100);
            Flower near = new Flower("Near", new Vec3(2, 0, 0), 100);
            FlowerSelectionStrategy strategy = new RichestFlowerStrategy();
            check(strategy.choose(List.of(reserved, empty, far, near), Vec3.ZERO) == near, "Wrong candidate");
            check(strategy.choose(List.of(reserved, empty), Vec3.ZERO) == null, "Selected unavailable flower");
            check(strategy.choose(List.of(), Vec3.ZERO) == null, "Empty field selection failed");
        });
        test("policy changes preserve in-flight reservations and affect the next booking", () -> {
            Field field = new Field(Vec3.ZERO, 5, new Random(1));
            field.addFlowers(3);
            Hive hive = new Hive(Vec3.ZERO, field, new ArrayList<>());
            BeeWorker first = (BeeWorker) hive.addBee(BeeFactory.Kind.WORKER);
            BeeWorker second = (BeeWorker) hive.addBee(BeeFactory.Kind.WORKER);
            Flower booked = field.reserveFlower(first);
            Flower requested = field.getFlowers().stream().filter(flower -> flower != booked).findFirst().orElseThrow();
            field.setSelectionStrategy((flowers, position) -> requested);
            check(booked.isOccupied(), "Policy change discarded an existing reservation");
            check(field.reserveFlower(second) == requested, "New policy was not applied");
            close(booked.harvest(first, 10), 10);
        });
        test("reset keeps the selected policy while reproducing its seeded initial world", () -> {
            Simulation world = new Simulation(77);
            world.setForagingPolicy(ForagingPolicy.RICHEST);
            String initial = world.summary();
            List<Vec3> positions = world.getField().getFlowers().stream().map(Flower::getPosition).toList();
            world.advance(80);
            world.reset();
            check(world.getForagingPolicy() == ForagingPolicy.RICHEST, "Policy was lost");
            check(initial.equals(world.summary()), "Reset did not restore initial state");
            check(positions.equals(world.getField().getFlowers().stream().map(Flower::getPosition).toList()), "Seed changed");
        });
        test("sequential and parallel trials agree exactly, with and without threats", () -> {
            for (boolean threats : new boolean[]{false, true}) {
                List<SimulationExperiment.Result> serial = SimulationExperiment.compare(2000, 4, 120, threats, false);
                List<SimulationExperiment.Result> parallel = SimulationExperiment.compare(2000, 4, 120, threats, true);
                check(serial.equals(parallel), "Parallel trial state leaked or order changed");
                check(serial.size() == 8, "Missing trial");
                for (int i = 0; i < serial.size(); i += 2) {
                    check(serial.get(i).seed() == serial.get(i + 1).seed(), "Policies did not share a starting seed");
                    check(serial.get(i).policy() != serial.get(i + 1).policy(), "Repeated policy");
                }
            }
        });
        test("policy comparison exhibits a different outcome in a real seeded colony", () -> {
            List<SimulationExperiment.Result> results = SimulationExperiment.compare(2000, 4, 180, false, false);
            boolean different = false;
            for (int i = 0; i < results.size(); i += 2) {
                different |= results.get(i).delivered() != results.get(i + 1).delivered();
            }
            check(different, "Policies never affected the actual simulation");
            String csv = SimulationExperiment.csv(results);
            check(csv.lines().count() == 9, "CSV missing a trial");
            check(csv.startsWith("policy,seed,seconds,auto_threats,"), "CSV header missing");
        });
        test("grouped statistics preserve policy counts and observed totals", () -> {
            List<SimulationExperiment.Result> results = List.of(
                    new SimulationExperiment.Result(ForagingPolicy.NEAREST, 1, 10, false, 10, 0, 6, 0),
                    new SimulationExperiment.Result(ForagingPolicy.RICHEST, 1, 10, false, 40, 0, 6, 0),
                    new SimulationExperiment.Result(ForagingPolicy.NEAREST, 2, 10, false, 30, 0, 6, 0));
            var grouped = SimulationExperiment.deliveredByPolicy(results);
            check(grouped.get(ForagingPolicy.NEAREST).getCount() == 2, "Wrong nearest count");
            close(grouped.get(ForagingPolicy.NEAREST).getAverage(), 20);
            close(grouped.get(ForagingPolicy.RICHEST).getSum(), 40);
            check(SimulationExperiment.deliveredByPolicy(List.of()).isEmpty(), "Empty report was not empty");
        });
        test("invalid experiment inputs fail before launching trials", () -> {
            rejects(() -> SimulationExperiment.compare(0, 0, 10, false, true));
            rejects(() -> SimulationExperiment.compare(0, 33, 10, false, false));
            rejects(() -> SimulationExperiment.compare(0, 1, Double.NaN, false, false));
            rejects(() -> SimulationExperiment.compare(0, 1, -1, false, false));
            rejects(() -> SimulationExperiment.compare(0, 1, 3601, false, false));
            rejects(() -> ForagingPolicy.parse("missing"));
            check(SimulationExperiment.compare(0, 1, 0, false, false).get(0).delivered() == 0, "Zero duration advanced");
        });
        test("camera projection and inverse ray select the same world object after orbit and resize", () -> {
            Flower flower = new Flower("Target", new Vec3(2, 0.5, -3), 100);
            ObjectPicker<Flower> picker = new ObjectPicker<>();
            for (double yaw : new double[]{0.45, -1.4, 3}) {
                for (int width : new int[]{790, 970}) {
                    CameraProjection camera = new CameraProjection(width, 518, yaw, 0.66, 33);
                    CameraProjection.Point point = camera.project(flower.getPosition());
                    check(picker.pick(List.of(flower), camera.origin(), camera.rayDirection(point.x(), point.y())).orElseThrow() == flower,
                            "Projection and picking disagree");
                }
            }
        });
        test("near-plane clipping keeps close-view ground visible and finite", () -> {
            CameraProjection camera = new CameraProjection(970, 598, 0, 0.22, 8);
            List<CameraProjection.Point> points = camera.projectFace(List.of(new Vec3(-10, 0, -12),
                    new Vec3(10, 0, -12), new Vec3(10, 0, 12), new Vec3(-10, 0, 12)));
            check(points.size() >= 3, "Ground vanished when zooming");
            check(points.stream().allMatch(point -> point.depth() >= 0.1 - 1e-8
                    && Double.isFinite(point.x()) && Double.isFinite(point.y())), "Invalid clipped polygon");
        });
        SceneAssets assets = new SceneAssets();
        check(assets.bee().getWidth() == 64 && assets.flower().getWidth() == 64 && !assets.tree().isEmpty(), "Original assets failed to load");
        passed++;
        System.out.println("PASS original image and mesh assets load through standard Java");
        System.out.println("PASS: " + passed + " Week 13 tests.");
    }

    private static void test(String name, Runnable run) { run.run(); passed++; System.out.println("PASS " + name); }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void close(double actual, double expected) { check(Math.abs(actual - expected) < 1e-8, actual + " != " + expected); }
    private static void rejects(Runnable run) {
        try { run.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Expected an IllegalArgumentException");
    }
}
