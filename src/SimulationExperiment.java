import java.util.DoubleSummaryStatistics;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/** Independent seeded trials; no task ever reads or mutates the displayed world. */
public final class SimulationExperiment {
    private SimulationExperiment() { }

    public record Result(ForagingPolicy policy, long seed, double seconds, boolean threats,
            double delivered, double honey, long workers, int deaths) { }

    private record Trial(ForagingPolicy policy, long seed) { }

    public static List<Result> compare(long firstSeed, int runs, double seconds,
            boolean threats, boolean parallel) {
        if (runs < 1 || runs > 32) throw new IllegalArgumentException("--runs must be between 1 and 32.");
        if (!Double.isFinite(seconds) || seconds < 0 || seconds > 3600) {
            throw new IllegalArgumentException("Comparison duration must be between 0 and 3600 seconds.");
        }
        // The same seed is paired across policies; each map invocation owns a
        // complete Simulation, including its Random, bees, flowers and enemies.
        Stream<Trial> trials = IntStream.range(0, runs).boxed()
                .flatMap(i -> Stream.of(ForagingPolicy.values())
                        .map(policy -> new Trial(policy, firstSeed + i)));
        if (parallel) trials = trials.parallel();
        return trials.map(trial -> run(trial, seconds, threats)).toList();
    }

    private static Result run(Trial trial, double seconds, boolean threats) {
        Simulation world = new Simulation(trial.seed(), trial.policy());
        if (threats) world.toggleAutoThreats();
        // Chunking allows a sequential background run to respond to interruption.
        double remaining = seconds;
        while (remaining > 0) {
            if (Thread.currentThread().isInterrupted()) throw new CancellationException("Trial interrupted.");
            double chunk = Math.min(1, remaining);
            world.advance(chunk);
            remaining -= chunk;
        }
        Hive hive = world.getHive();
        return new Result(trial.policy(), trial.seed(), seconds, threats,
                hive.getTotalCollected(), hive.getHoney(), hive.getWorkerCount(), hive.getDeaths());
    }

    public static Map<ForagingPolicy, DoubleSummaryStatistics> deliveredByPolicy(List<Result> results) {
        return results.stream().collect(Collectors.groupingBy(Result::policy,
                () -> new EnumMap<>(ForagingPolicy.class),
                Collectors.summarizingDouble(Result::delivered)));
    }

    public static String csv(List<Result> results) {
        return "policy,seed,seconds,auto_threats,delivered,honey,workers,deaths\n"
                + results.stream().map(result -> String.format(Locale.ROOT,
                        "%s,%d,%.2f,%s,%.2f,%.2f,%d,%d",
                        result.policy().key(), result.seed(), result.seconds(), result.threats(),
                        result.delivered(), result.honey(), result.workers(), result.deaths()))
                        .collect(Collectors.joining("\n", "", "\n"));
    }
}
