# Verification

Verified locally on 6 September 2026.

## Results

| Check | Result | Evidence |
|---|---|---|
| Compile | Pass, Java 17 with all compiler warnings treated as errors | [Behavior/build log](../evidence/behavior-tests.txt) |
| Behavior tests | 25 passed | [Behavior log](../evidence/behavior-tests.txt) |
| Simulation soak | 30 simulated minutes with automatic threats; bounded resources and populations | [Behavior log](../evidence/behavior-tests.txt) |
| Actual desktop interactions | 31 checks passed | [Desktop checklist](../evidence/gui-checks.txt) |
| Native graphics log | No warnings, errors, exceptions or native crashes during the desktop run | [Native log](../evidence/gui-smoke.log) |
| Startup and errors | Invalid arguments rejected; missing assets reported with clean shutdown; headless mode runs without Jaylib on the classpath | [Launch checks](../evidence/launch-checks.txt) |
| Visual review | Inspected default view, flower selection and minimum window layout | Images below |

## Desktop evidence

- [Completed colony with honey, brood and a newly hatched worker](../evidence/desktop-completed.png)
- [Mouse-selected flower and its information](../evidence/desktop-flower-picker.png)
- [Paused hive view](../evidence/desktop-paused.png)
- [Spawned enemies](../evidence/desktop-enemies.png)
- [Camera orbit and path view](../evidence/desktop-orbit.png)
- [1100 x 720 resized window](../evidence/desktop-resized.png)

The desktop driver starts a real Jaylib window, focuses it, uses mouse/keyboard events, captures PNGs through the application's screenshot shortcut, and checks resulting action/selection logs. It verifies all eight action buttons, closest-object flower picking, pause behavior, inspector shortcuts, window resizing, reset, and queen/larva progression. It then closes the application normally.

The 31 desktop checks include control responses, state assertions, and screenshot-file checks; they are not 31 separate feature scenarios.

## Reproduce

From the project root on Linux:

```sh
./test.sh
xvfb-run -a -s '-screen 0 1440x1000x24' python3 tests/gui_smoke.py
python3 tests/launch_checks.py
```

The last two commands require Python 3 and Xvfb; the desktop driver also requires xdotool. No Python packages are required.

The behavior tests use no graphics or test framework dependencies:

```sh
java -cp build/classes App --headless --seconds 300
```

The recorded seed-2000 result at 300 seconds was:

```text
time=300.00 workers=29 queen=true larvae=2 honey=17920.00 delivered=18100.00 flowers=5 enemies=0 hatched=23 deaths=0
```

## Verification boundary

The runtime tested was Temurin OpenJDK 17.0.18 on Linux x86_64, using an Xvfb desktop and Mesa llvmpipe software OpenGL. The bundled Jaylib 6.0.1-0 library reports its native version as raylib 6.1-dev.

This is a native desktop application, so there is no web page, browser console, HTTP status, or frontend network layer to test with Playwright.

Windows and macOS launchers are included, but those operating systems and physical graphics devices were not exercised here. The supplied archive contained no formal assignment brief or assessment rubric; this verification covers the repository TODOs and the documented behavior.
