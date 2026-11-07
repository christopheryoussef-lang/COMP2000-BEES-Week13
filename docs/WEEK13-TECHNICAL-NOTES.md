# Week 13 technical notes

These are factual implementation notes to support the worksheet and a code demonstration. They do not supply the student's personal experience, in-class comparisons, inspiration, future ambitions, logbook or feedback history.

## 1. Design patterns

### Strategy — flower selection

Participants and locations:

| Participant | Source / method | Responsibility |
| --- | --- | --- |
| Strategy interface | `src/FlowerSelectionStrategy.java`, `choose` | Rank a supplied flower list for a worker position |
| Concrete strategies | `src/NearestFlowerStrategy.java`, `src/RichestFlowerStrategy.java` | Nearest available flower; or highest remaining nectar, then nearest distance |
| Context | `src/Field.java`, `reserveFlower` and `setSelectionStrategy` | Delegate ranking, validate field membership and reserve the chosen flower |
| Policy selection | `src/ForagingPolicy.java`, `src/Simulation.java`, `setForagingPolicy` | Map a named policy to a stateless strategy and install it |
| Consumer | `src/BeeWorker.java`, `update`, RESTING branch | Request a flower when beginning a trip |
| Clients | `src/SceneRenderer.java`, `changePolicy`; `src/App.java`; `src/SimulationExperiment.java` | Select a policy interactively, via arguments or per experiment trial |

The key interaction in `Field.reserveFlower` is:

```java
Flower best = selectionStrategy.choose(getFlowers(), bee.getPosition());
return best != null && flowers.contains(best) && best.reserve(bee) ? best : null;
```

`Simulation.setForagingPolicy` installs `policy.strategy()` through `field.setSelectionStrategy(...)`. The field continues to own reservation validation, and a worker continues to own its flight/collection state. Switching policies leaves current reservations intact and affects the next booking.

Strategy was chosen because the selection algorithm varies independently of the worker lifecycle. A `switch` inside `BeeWorker.update` would mix ranking with movement, collection and defense. A subclass per worker-policy combination would duplicate lifecycle behavior. Strategy adds types and an indirect call, but makes each rule independently testable and interchangeable. The nearest policy and Strategy seam existed in the supplied code; runtime switching and the richest strategy are the Week 13 extension.

### Simple Factory — adult creation

`src/BeeFactory.java` contains `Kind` and the static `create` method. `Hive.addBee` checks the population cap and duplicate-queen rule, creates a unique name, delegates construction and registers the returned bee:

```java
Bee bee = BeeFactory.create(kind,
        (kind == BeeFactory.Kind.QUEEN ? "Queen " : "Worker ") + nextBeeId++, this);
bees.add(bee);
```

The factory returns the shared `Bee` type:

```java
return switch (Objects.requireNonNull(kind)) {
    case WORKER -> new BeeWorker(name, hive);
    case QUEEN -> new BeeQueen(name, hive);
};
```

Initial population, the add-worker control and mature larvae all reach this construction path through `Hive.addBee`. `Hive.update` chooses a queen if the current queen is missing, otherwise a worker.

The alternative is scattered constructors in reset, controls and hatching code. Centralization makes product creation consistent. The trade-off is editing the enum/switch when a new bee kind is introduced. Population rules belong to `Hive`, not the factory. This is **Simple Factory**, not GoF Factory Method: there is no overridable creator hierarchy. It was already implemented in the supplied code and is retained rather than claimed as a newly introduced pattern.

[The diagram PDF](design-patterns.pdf) shows the actual participants and interactions. `diagrams/*.dot` are editable Graphviz sources. A worker's enum-based lifecycle is a finite-state machine; it is not claimed as an implementation of the GoF State pattern.

## 2. Streams, lambdas and parallelism

### File inventory

Every application source file containing lambda expressions or Streams API calls:

| File | Use |
| --- | --- |
| `src/RichestFlowerStrategy.java` | Stream filtering, comparator composition, maximum selection; lambda distance tie-break |
| `src/SimulationExperiment.java` | Seed/policy trial generation, mapping, optional parallel stream, immutable list collection, downstream grouping/summarizing collectors, CSV joining |
| `src/SceneRenderer.java` | Swing timer/action callbacks, object/inspector filtering, honey-history aggregation |
| `src/MeadowPainter.java` | Projected face depth aggregation, drawing callbacks, depth sorting and filtering the original ground mesh |
| `src/Field.java` | Lambda removal of depleted flowers; stream sum of available nectar |
| `src/Hive.java` | Worker count, queen presence, living count, removal of inactive bees |
| `src/Simulation.java` | Lambda removal of inactive enemies |

Test code also uses lambdas/streams in `tests/SimulationTests.java` and `tests/Week13Tests.java`. `switch` case arrows in other files are **not** lambda expressions. `FlowerSelectionStrategy` is a functional interface; its declaration alone is not an expression using a lambda.

### Most sophisticated use — paired parallel experiments

`SimulationExperiment.compare` builds a stable ordered stream of both policies for each seed:

```java
Stream<Trial> trials = IntStream.range(0, runs).boxed()
        .flatMap(i -> Stream.of(ForagingPolicy.values())
                .map(policy -> new Trial(policy, firstSeed + i)));
if (parallel) trials = trials.parallel();
return trials.map(trial -> run(trial, seconds, threats)).toList();
```

`run` constructs a fresh `Simulation(trial.seed(), trial.policy())`, advances it locally, and returns an immutable `Result` record. Both policies start from the same seed. The stream describes generation, independent transformation and collection in one pipeline, and the same code supports sequential execution for verification. A loop would also be reasonable for small batches; streams are not assumed faster. Avoiding a manually shared result list makes the parallel version simpler to reason about.

The report uses standard downstream collectors:

```java
return results.stream().collect(Collectors.groupingBy(Result::policy,
        () -> new EnumMap<>(ForagingPolicy.class),
        Collectors.summarizingDouble(Result::delivered)));
```

This computes count, sum, min, max and mean nectar delivered by each policy. The UI displays the means; the CSV preserves every trial. These are **standard collectors**, not a custom collector.

### Thread safety and execution boundary

- The live simulation is confined to Swing's event dispatch thread: timer updates, drawing and input handlers run there.
- `comparePolicies` captures only the seed and automatic-threat flag before starting `SwingWorker`. It never passes the live mutable world to a worker.
- Each parallel trial constructs its own simulation, `Random`, field, hive, bees, larvae, flowers and enemy list. The shared policy instances have no mutable state.
- Each trial executes its own ticks sequentially. Live bees are not updated with `parallelStream()`: they share reservations and hive resources, so that would introduce races.
- `Stream.toList()` collects immutable result records without a shared mutable accumulator supplied by application code. The ordered stream preserves seed/policy row order.
- `SwingWorker.done` receives results on the event dispatch thread. Grouping happens after trials complete. Its `DoubleSummaryStatistics` objects are local to that report, not shared across simulation workers.
- Parallel streams use the runtime's common Fork/Join pool. No custom `ForkJoinTask`, explicit Fork/Join framework algorithm or custom collector is implemented. For tiny batches, parallel overhead can outweigh any benefit.

Tests compare sequential and parallel results for every field, in the same order, both with and without threats. This is evidence for the tested seeds and durations, alongside the ownership argument; it is not a proof of every possible future change.

## 3. Behavior and observed outcomes

### Features to demonstrate

The extension compares two foraging rules under matched starting seeds, without freezing or mutating the live colony. Switching between rules changes collective delivery through travel distance, exclusive flower reservations and finite worker capacity. The original interactions also remain: collecting supports eggs, larvae become adults, defense interrupts foraging, and an exterminator can damage both bees and wasps.

Presentation responsibilities are separated: `SceneRenderer` handles the window, controls, inspector and honey-history display, while `MeadowPainter` draws the world. The painter caches only static terrain and rebuilds it when the camera or viewport changes. Its decorative grass uses a separate fixed random generator and never consumes the simulation's random sequence. Honey-history samples belong to the view on the event dispatch thread; pause stops their simulated time and reset clears them. The data displayed is measured from the live model, not scripted animation.

Whether these go beyond the student's particular in-class activities, and what personally inspired the project, must be described by the student using their actual experience.

### Reproducible observation

Run:

```sh
bash run.sh --compare --seed 2000 --runs 4 --seconds 180 --parallel
```

Fresh observed results, with automatic threats off, are in `evidence/policy-comparison.csv`:

| Seed | Nearest: nectar delivered | Richest: nectar delivered |
| --- | ---: | ---: |
| 2000 | 9850 | 9500 |
| 2001 | 10050 | 9600 |
| 2002 | 10150 | 9650 |
| 2003 | 9900 | 9550 |
| Mean | 9987.5 | 9575.0 |

Choosing the flower with more nectar did not yield more delivery in these trials. Workers carry at most 50 units; a rich flower can require a longer flight, and a half-depleted nearby flower can still fill a worker. Those rules explain why richer patches can be less productive in this scenario. This is an interpretation of these model results, not a universal biological claim or proof that nearest always wins. Different parameters, threats, seeds and durations may change outcomes.

The result arises from existing local interactions rather than a hard-coded policy bonus. In `BeeWorker.update`, collection is bounded by capacity and flower availability:

```java
nectar += target.harvest(this,
        Math.min(CAPACITY - nectar, COLLECTION_RATE * deltaTime));
if (nectar >= CAPACITY - 1e-9 || !target.isActive()) returnHome();
```

`returnHome` releases the reservation; a later RETURNING update deposits carried nectar in the hive. The queen can spend honey on larvae, so the colony changes over time. Automatic threats introduce a further trade-off between defense and collection.

Possible extensions for discussion include seasons, different flower resource profiles, fatigue, and larger seeded experiments. These are ideas, not implemented features or a statement of the student's personal six-week plan.

## Demonstration route

1. Start the desktop; select a bee to explain its current state, target and nectar.
2. Pause, press P and explain why the current target is retained until the next trip.
3. Press C; inspect the paired seed results and explain why the live colony is independent.
4. Show `Field.reserveFlower`, both strategies, and `Hive.addBee` calling `BeeFactory`.
5. Show `SimulationExperiment.compare`, its local world construction and `SwingWorker.done`.
6. Run the Java tests and point to the serial/parallel agreement and capacity/reservation checks.
7. Resume, add a wasp or exterminator, and inspect how defense/visitors alter collection and survival.

## Worksheet and submission boundary

The code and diagram support sections 1–3. The student supplies their name, individual repository/fork URL, personal inspiration and future plan, actual Week 7 feedback, reflection and Weeks 7–13 logbook. Follow the lecturer's instructions for acknowledging AI assistance.

The general brief asks for a worksheet and logbook without a ZIP; the specific worksheet additionally requests a pattern PDF. The PDF is supplied here for that requirement. Use the lecturer's designated upload/attachment arrangement. This code delivery does not record a GitHub publication or assessment submission.
