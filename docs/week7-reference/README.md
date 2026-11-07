# COMP2000 Bee Simulation

Team: WeLoveMQ

A Java desktop bee-hive simulation with an interactive 3D meadow. Workers collect nectar, queens raise brood, larvae mature, and visitors can threaten the colony. The implementation covers the repository's TODO checklist and includes a code walkthrough and repeatable tests.

![Bee simulator desktop](evidence/desktop-completed.png)

## Run

Install a **JDK 17 or newer** and make sure both `java` and `javac` are on PATH. The supplied `lib/jaylib-6.0.1-0.jar` contains the graphics binding and native libraries; Maven and Gradle are not needed.

**Linux / macOS**

```sh
./run.sh
```

**Windows**

```bat
run.bat
```

The launchers compile the source and start from the correct asset directory. On macOS, the shell launcher supplies `-XstartOnFirstThread`. A desktop with compatible OpenGL graphics is needed for the window. See the [Jaylib platform documentation](https://github.com/electronstudio/jaylib#platforms) for the bundled library's platform support.

For VS Code, open this directory and run `src/App.java`; the included configuration sets the project root as the working directory. Source output is `build/classes`. The old supplied `bin` files are not used.

## Controls

| Action | Keyboard / mouse |
|---|---|
| Add a worker | B or Worker button |
| Add ten flowers | F or Flowers button |
| Spawn a wasp | W or Wasp button |
| Spawn an exterminator | E or Visitor button |
| Pause / resume | Space |
| Cycle 1x, 2x, 4x speed | X |
| Toggle automatic threats | T |
| Reset the colony | R |
| Inspect an object | Left click |
| Inspect hive / queen | H / Q |
| Cycle through objects | Tab |
| Orbit | Left / right arrows or right mouse drag |
| Zoom | Up / down arrows or mouse wheel |
| Tilt | Page Up / Page Down |
| Reset camera | Home |
| Show worker destination lines | G |
| Save a screenshot | F12, to `screenshots/bee-simulator.png` |
| Close | Escape or window close |

Buttons and inspector clicks are separate from meadow selection. The picker chooses the closest intersected active object. When a selected object dies, hatches, or is removed, selection clears.

## Simulation rules

- A new colony starts with one queen, six workers, 50 flowers, and 20 honey.
- Workers reserve one available flower, collect at 10 units per second up to a carrying capacity of 50, and return to deposit it. They wait if no flower is available and defend against nearby wasps.
- A queen can lay a larva every 12 seconds. Each larva costs 8 honey and matures after 15 seconds. If the queen has died, the next maturing larva becomes the replacement queen.
- The colony limit of 60 includes adults and brood. Empty, unoccupied flowers are removed safely. One flower grows every two seconds while the field has fewer than 50 flowers; manual additions are capped at 75.
- Wasps pursue bees, damage them, and steal available honey near the hive. Workers can kill wasps. Exterminators spray both bees and wasps within a horizontal radius.
- Wasps leave after at most 55 simulation seconds; exterminators leave after 25. At most 12 enemies can exist together.
- Automatic threats start **off**. When enabled, a visitor arrives every 25 simulation seconds; every fourth arrival is an exterminator.
- Adding workers and visitors is a sandbox control and has no honey cost. If the colony loses all adults and brood, reset starts a new one.
- Pollen/nectar is one collectable resource in this model and converts to honey one-for-one. This is an educational simulation with configurable constants, not a biological model.

## Verification

```sh
./test.sh
./run.sh --headless --seconds 300
```

On Windows use `test.bat` and `run.bat --headless --seconds 300`.

The behavioral tests run without a window or a native graphics dependency. They cover resource conservation, reservations, the bee lifecycle, enemy behavior, collection limits, fixed time steps, reset, and a 30-minute simulation.

On Linux, with Python 3, Xvfb, and xdotool installed, exercise the actual desktop:

```sh
./build.sh
xvfb-run -a -s '-screen 0 1440x1000x24' python3 tests/gui_smoke.py
```

A deterministic screenshot run is also available:

```sh
./run.sh --frames 180 --screenshot evidence/desktop.png
```

Use `--seed 2000` to reproduce the initial meadow. `--headless --seconds N` advances the same model without loading graphics. `--help` lists command options.

Recorded results and platform boundaries are in [docs/VERIFICATION.md](docs/VERIFICATION.md).

## Project guide

- [TODO checklist and implementation mapping](docs/TODOS.md)
- [Task board](docs/Progress%20Tracker%20KanBan%20%28obsidian%29.md)
- [Code walkthrough and design decisions](docs/CODE_WALKTHROUGH.md)
- [Verification evidence](docs/VERIFICATION.md)

The original bee and flower sprites and tree geometry come from the supplied project; the grass material was softened for visibility. The two original PNG diagrams are retained as planning documents. The walkthrough describes the implemented design.

The archive did not include a formal assignment brief or marking rubric. Completion here refers to the supplied repository checklist and documented simulation behavior.
