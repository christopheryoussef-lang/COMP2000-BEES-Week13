# Completed repository checklist

Implemented against the supplied project notes and task board. See [VERIFICATION.md](VERIFICATION.md) for the checks performed and [CODE_WALKTHROUGH.md](CODE_WALKTHROUGH.md) for explanations.

## Classes

- [x] Base Bee Class — abstract `Bee`: hive membership, health, age and movement
- [x] Worker Bee — `BeeWorker`: foraging, collection, return, rest and defense
- [x] Queen Bee — `BeeQueen`: timed, food-limited egg production
- [x] Larvae Class — `Larvae`: maturation and queen replacement
- [x] Base Location Class — abstract `Location`
- [x] Field Class — `Field`: flower generation, selection and removal
- [x] Flower Class — `Flower`: bounded pollen and exclusive reservations
- [x] Hive Class — `Hive`: honey, independent population, brood and population limits
- [x] Base Enemy Class — abstract `Enemy`: health, target selection and visit lifetime
- [x] Wasp Class — `Wasp`: attacks and honey raids
- [x] Exterminator Class — `Exterminator`: spray affects bees and wasps

## Programming requirements

- [x] Generics — `ObjectPicker<T extends Inspectable>`, `Optional<T>`, typed collections
- [x] Exception Handling — invalid input, missing assets, native load failures and cleanup
- [x] Design Patterns — `BeeFactory` and `FlowerSelectionStrategy` / `NearestFlowerStrategy`
- [x] Interfaces — `Inspectable`, `Updatable`, `FlowerSelectionStrategy`
- [x] Abstract Classes — `SceneObject`, `Bee`, `Location`, `Enemy`

## Functional work and rework

- [x] Scene Drawing — original models/sprites, camera, inspector, legend and controls
- [x] Object Picker — independent collision boxes, nearest active hit and stale-selection cleanup
- [x] Worker Bee rework — time-based movement and collection; reservation release; no resource overdraw
- [x] Base Bee rework — shared behavior with encapsulated state; graphics removed from model constructors
- [x] Flower Generator — replenishment, manual additions and limits
- [x] Build and run — Java 17 shell/Windows launchers and VS Code setup
- [x] Verification — behavioral tests and real desktop interaction checks

## References

- [Jaylib](https://github.com/electronstudio/jaylib)
- [Raylib graphics reference](https://www.raylib.com/cheatsheet/cheatsheet.html)
- [Raymath reference](https://www.raylib.com/cheatsheet/raymath_cheatsheet.html)

These checkmarks describe the implemented and tested repository scope. The archive contained no formal marking rubric.
