import java.io.IOException;
import java.nio.file.Path;

/** Entry point. The same model runs interactively or without any graphics. */
public final class App {
    private App() { }

    public static void main(String[] args) {
        try {
            Options options = Options.parse(args);
            if (options.help) {
                System.out.println("""
                        Bee Simulator - Java 17+
                        ./run.sh                         Open the desktop simulation
                        ./run.sh --headless --seconds 120 Run the model without a display
                        Options:
                          --seed N          Reproducible world seed (default 2000)
                          --seconds N       Headless simulation duration (default 120)
                          --policy NAME     nearest (default) or richest
                          --threats         Enable automatic threats
                          --compare         Print paired policy trials as CSV (no display)
                          --runs N          Seeds per policy, 1..32 (default 4)
                          --parallel        Run independent comparison trials in parallel
                          --frames N        Close the desktop after N frames
                          --screenshot PATH Save the final desktop frame as a PNG
                          --help            Show these instructions
                        """);
                return;
            }
            if (options.compare) {
                System.out.print(SimulationExperiment.csv(SimulationExperiment.compare(options.seed,
                        options.runs, options.seconds, options.threats, options.parallel)));
                return;
            }
            Simulation simulation = new Simulation(options.seed, options.policy);
            if (options.threats) simulation.toggleAutoThreats();
            if (options.headless) {
                simulation.advance(options.seconds);
                System.out.println(simulation.summary());
            } else {
                new SceneRenderer().run(simulation, options.frames, options.screenshot);
            }
        } catch (IllegalArgumentException | IllegalStateException | IOException error) {
            System.err.println("Bee Simulator: " + error.getMessage());
            System.exit(1);
        }
    }

    private static final class Options {
        boolean headless;
        boolean help;
        boolean compare;
        boolean parallel;
        boolean threats;
        int runs = 4;
        ForagingPolicy policy = ForagingPolicy.NEAREST;
        long seed = 2000;
        double seconds = 120;
        int frames;
        Path screenshot;

        static Options parse(String[] args) {
            Options options = new Options();
            for (int i = 0; i < args.length; i++) {
                String argument = args[i];
                if (argument.equals("--headless")) options.headless = true;
                else if (argument.equals("--help")) options.help = true;
                else if (argument.equals("--compare")) options.compare = true;
                else if (argument.equals("--parallel")) options.parallel = true;
                else if (argument.equals("--threats")) options.threats = true;
                else {
                    if (i + 1 >= args.length) throw new IllegalArgumentException("Missing value after " + argument);
                    String value = args[++i];
                    switch (argument) {
                        case "--seed" -> options.seed = Long.parseLong(value);
                        case "--seconds" -> options.seconds = Double.parseDouble(value);
                        case "--frames" -> options.frames = Integer.parseInt(value);
                        case "--runs" -> options.runs = Integer.parseInt(value);
                        case "--policy" -> options.policy = ForagingPolicy.parse(value);
                        case "--screenshot" -> options.screenshot = Path.of(value).toAbsolutePath();
                        default -> throw new IllegalArgumentException("Unknown option: " + argument);
                    }
                }
            }
            if (!Double.isFinite(options.seconds) || options.seconds < 0 || options.seconds > 86400) {
                throw new IllegalArgumentException("--seconds must be between 0 and 86400.");
            }
            if (options.frames < 0) throw new IllegalArgumentException("--frames cannot be negative.");
            if (options.parallel && !options.compare) throw new IllegalArgumentException("--parallel requires --compare.");
            if (options.runs < 1 || options.runs > 32) throw new IllegalArgumentException("--runs must be between 1 and 32.");
            if (options.compare && options.seconds > 3600) throw new IllegalArgumentException("Comparison duration cannot exceed 3600 seconds.");
            return options;
        }
    }
}
