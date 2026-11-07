# Changes from the supplied Week 7 archive

Base: `COMP2000-BEES-Completed (1).zip`. The original archive and extracted reference were preserved separately. These notes describe changes to that supplied source, without claiming who wrote the earlier code or reconstructing commit history.

| Area | Supplied code | Week 13 update |
| --- | --- | --- |
| Graphics | Jaylib/raylib native binding and 17 MB JAR | Swing/Java2D, standard `ImageIO`, small OBJ/MTL reader; no external Java dependency |
| World view | Perspective meadow, sprites, selectable objects | Original assets and perspective camera retained; projection, near-plane clipping and ray picking implemented with Java maths |
| Window sizing | Fixed initial dimensions | Fit startup window to usable monitor area; adapt inspector spacing on smaller laptop screens |
| Visual presentation | Flat meadow colors and small sprites | Shaded terrain and tree, shadows, larger sprites, hover/selection feedback, grouped inspector, key hints and live honey history; static terrain caching |
| Foraging | Injected `FlowerSelectionStrategy`, nearest implementation | Richest implementation, named policies, runtime switching without breaking reservations |
| Simulation construction | One default policy | Default remains nearest; constructor accepts a policy and reset preserves it |
| Experiments | No paired policy comparison | Seeded serial/parallel trials, immutable results, grouped statistics, CSV, background desktop comparison |
| Pattern foundations | Simple Factory and Strategy seam already present | Factory retained; Strategy integrated across live controls and experiments; accurate PDF diagrams and rationale |
| Model | Workers, queen, larvae, flowers, two threat types, fixed-step updates | Core lifecycle, resource accounting, capacities and enemy behavior retained |
| Regression tests | 25 behavior cases | All retained; 11 additional Week 13 cases; updated desktop and launch checks |
| Build | Java 17 plus Jaylib classpath and native macOS flag | Java 17 only; Linux/macOS and Windows scripts; clean CSV stdout |
| Documentation | Earlier walkthrough, TODOs and verification | Current README, technical notes, design PDF and evidence; old notes clearly archived |

New source files: `ForagingPolicy.java`, `RichestFlowerStrategy.java`, `SimulationExperiment.java`, `CameraProjection.java`, `MeadowPainter.java`.

Changed source files: `Field.java`, `Simulation.java`, `App.java`, `SceneAssets.java`, `SceneRenderer.java`. Other model classes are retained from the supplied archive.

The old `lib/jaylib-6.0.1-0.jar` is excluded from this delivery. No simulated historical commits, personal logbook entries, Week 7 feedback or student reflections were added.
