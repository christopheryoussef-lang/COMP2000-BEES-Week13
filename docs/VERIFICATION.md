# Week 13 verification

Verified locally on Linux using OpenJDK / Temurin 17.0.18. The evidence in the top-level `evidence/` folder was generated for this Week 13 source, not copied from the earlier archive. Historical Week 7 notes are stored separately under `docs/week7-reference/`.

| Check | Observed result | Evidence |
| --- | --- | --- |
| Standard Java build | Pass: Java 17 release, UTF-8, annotation processing disabled, all compiler warnings treated as errors, no external classpath | `build.sh`; `evidence/behavior-tests.txt` |
| Runtime module dependencies | Only `java.base` and `java.desktop` | `evidence/dependencies.txt` |
| Original model regression suite | 25 passed, including a 30-minute simulated run | `evidence/behavior-tests.txt` |
| Week 13 suite | 11 passed: distinct policies, candidate filtering, safe strategy replacement, reset, exact serial/parallel agreement, real colony divergence, report statistics, validation, projection/picking, near-plane clipping, original assets | `evidence/behavior-tests.txt` |
| Native desktop integration | 35 checks passed under Xvfb at 1280x800 and 1100x720 | `evidence/gui-checks.txt`, `evidence/gui-smoke.log` |
| Smaller screen startup | 9 checks passed on 1366x768, 1024x720 and 1366x680 virtual screens; full window fits, bottom controls work, shortcuts/screenshot/exit succeed | `evidence/screen-fit-checks.txt`, `evidence/desktop-screen-*.png` |
| Launch and argument handling | 20 checks passed: invalid input, headless mode, paired CSV, no display, missing assets, paths containing spaces, standard modules | `evidence/launch-checks.txt` |
| Policy experiment | Four seeds, 180 simulated seconds each, both policies, threats off | `evidence/policy-comparison.csv` |
| Pattern PDF | Two pages generated; participant names checked in extracted PDF text; both diagrams visually inspected | `docs/design-patterns.pdf` |
| Clean archive extraction | Compiles with no prebuilt classes; headless and desktop runs succeed; only standard module dependencies | `evidence/archive-checks.txt`, `evidence/archive-desktop.png` |

Desktop checks exercise the actual Swing application with mouse and keyboard input: pause, worker/flower/threat buttons, exact flower picking, policy shortcut/button, completed background comparison, inspector shortcuts, orbit/zoom, resizing, reset, speed and queen/larva maturation. Screenshots were visually inspected, including the comparison and minimum-size layout. The final desktop log contains no warnings, errors or exceptions detected by the smoke test.

Native desktop automation uses Xvfb and xdotool because this is a Java Swing application, not a website. No browser-based behavior is claimed. Under Xvfb without a window manager, the test clicks the content area to establish keyboard focus before sending shortcuts.

A follow-up review reproduced a startup sizing issue: the original 1280x800 window extended 32 pixels below a 1366x768 screen. `SceneRenderer.fitToScreen` now caps the outer window to the available monitor area and respects OS insets; the inspector uses the available vertical space. All 35 desktop checks were rerun after the fix, plus nine screen-size checks. The smaller-screen screenshots were visually inspected. These virtual Linux screens do not constitute a native Windows/macOS test.

The current app uses shaded polygons and sprites, not a GPU depth buffer. Original sprite and tree assets and perspective camera controls are retained; overlapping geometry can still have painter-order limitations. The replacement terrain surface is drawn behind world entities, and polygons are clipped at the near plane. Picking tests cover projection/ray consistency after orbit and resizing. Static terrain is cached per camera/view size; the simulation does not depend on this cache.

After the visual refresh, all 36 Java tests, 35 desktop checks and nine smaller-screen checks were rerun successfully. The final desktop, policy-comparison and short-screen screenshots were visually inspected. The refresh adds larger sprites, directional tree shading, shadows, hover/selection feedback, comparison bars and live honey history. Model source files were not changed by this presentation update.

The serial/parallel equality tests cover four seeds for 120 simulated seconds with and without threats. The displayed example compares four seeds for 180 seconds with threats off. These observations support deterministic execution for the tested cases; they do not establish that either policy always performs better.

Windows batch files and macOS-compatible shell launchers are supplied. Native Windows/macOS graphical execution has not been performed here. No client computer, GitHub publication, university upload, logbook, personal reflection or grade was verified by these tests.
