#!/usr/bin/env python3
"""Linux desktop integration checks: run under xvfb-run to isolate the window."""
from pathlib import Path
import math
import os
import re
import shutil
import struct
import subprocess
import time

ROOT = Path(__file__).resolve().parents[1]
EVIDENCE = ROOT / "evidence"
EVIDENCE.mkdir(exist_ok=True)
checks = []

def check(condition, message):
    if not condition:
        raise AssertionError(message)
    checks.append(message)
    print("PASS " + message, flush=True)

def xdo(*args):
    return subprocess.run(["xdotool", *map(str, args)], check=True,
                          text=True, capture_output=True).stdout.strip()

def wait_for(predicate, message, timeout=8):
    until = time.monotonic() + timeout
    while time.monotonic() < until:
        if predicate():
            return
        if app.poll() is not None:
            raise AssertionError("App exited: " + log.read_text()[-1500:])
        time.sleep(0.05)
    raise AssertionError("Timed out: " + message)

def press(key):
    xdo("key", "--clearmodifiers", "--delay", 100, key)
    time.sleep(0.22)

def click(x, y):
    xdo("mousemove", "--window", window, round(x), round(y), "mousedown", "1")
    time.sleep(0.1)
    xdo("mouseup", "1")
    time.sleep(0.22)

def capture(name):
    source = ROOT / "screenshots" / "bee-simulator.png"
    before = source.stat().st_mtime_ns if source.exists() else 0
    press("F12")
    wait_for(lambda: source.exists() and source.stat().st_mtime_ns > before, "screenshot")
    target = EVIDENCE / name
    shutil.copyfile(source, target)
    header = target.read_bytes()[:24]
    check(header[:8] == b"\x89PNG\r\n\x1a\n", name + " is a PNG")
    return struct.unpack(">II", header[16:24])

def action_count(action):
    return log.read_text().count("ACTION " + action + " ")

def action(name, callback):
    before = action_count(name)
    callback()
    wait_for(lambda: action_count(name) == before + 1, name)
    check(action_count(name) == before + 1, name + " control responds")

def flower_click_position():
    subprocess.run(["javac", "--release", "17", "-cp", "build/classes", "-d",
                    "build/test-classes", "tests/PickingFixture.java"], cwd=ROOT, check=True)
    fixture = subprocess.check_output(["java", "-cp", "build/classes:build/test-classes",
                                      "PickingFixture"], cwd=ROOT, text=True)
    yaw, pitch, distance = 0.45, 0.66, 33
    camera = (distance * math.cos(pitch) * math.sin(yaw),
              0.5 + distance * math.sin(pitch),
              distance * math.cos(pitch) * math.cos(yaw))
    forward = (-math.cos(pitch) * math.sin(yaw), -math.sin(pitch),
               -math.cos(pitch) * math.cos(yaw))
    right = (math.cos(yaw), 0, -math.sin(yaw))
    up = (-math.sin(pitch) * math.sin(yaw), math.cos(pitch),
          -math.sin(pitch) * math.cos(yaw))
    dot = lambda a, b: sum(x * y for x, y in zip(a, b))
    scale = 598 / (2 * math.tan(math.radians(22.5)))
    points = []
    for line in fixture.splitlines():
        name, *values = line.split("\t")
        p = tuple(float(value) - origin for value, origin in zip(values, camera))
        depth = dot(p, forward)
        points.append((name, 485 + dot(p, right) * scale / depth,
                       391 - dot(p, up) * scale / depth))
    candidates = [p for p in points if 100 < p[1] < 850 and 240 < p[2] < 610]
    return max(candidates, key=lambda p: min(math.hypot(p[1] - q[1], p[2] - q[2])
                                           for q in points if q != p))

if not os.environ.get("DISPLAY"):
    raise SystemExit("Run under xvfb-run with a 1440x1000 screen.")
log = EVIDENCE / "gui-smoke.log"
with log.open("w") as output:
    app = subprocess.Popen(["java", "-Xmx384m", "-cp",
                            "build/classes", "App"],
                           cwd=ROOT, stdout=output, stderr=subprocess.STDOUT)
try:
    window = None
    until = time.monotonic() + 12
    while time.monotonic() < until and window is None:
        result = subprocess.run(["xdotool", "search", "--onlyvisible", "--pid",
                                 str(app.pid), "--name", "^Bee Simulator$"],
                                capture_output=True, text=True)
        if result.returncode == 0:
            window = result.stdout.strip().splitlines()[0]
        else:
            time.sleep(0.1)
    check(window is not None, "native desktop window is visible")
    xdo("windowfocus", "--sync", window)
    time.sleep(0.7)
    # With no window manager, AWT establishes keyboard focus on the first click.
    click(30, 180)
    action("PAUSE", lambda: press("space"))
    check(capture("desktop-paused.png") == (1280, 800), "desktop renders at 1280 x 800")
    press("p")
    wait_for(lambda: "POLICY richest" in log.read_text(), "policy keyboard control")
    check("POLICY richest" in log.read_text(), "policy shortcut selects richest strategy")
    click(1100, 668)
    wait_for(lambda: "COMPARE complete" in log.read_text(), "policy comparison", timeout=30)
    check("nearest,2000,180.00,false" in log.read_text() and "richest,2003,180.00,false" in log.read_text(),
          "background comparison completes all paired seeds")
    capture("desktop-policy-comparison.png")
    click(1100, 536)
    wait_for(lambda: "POLICY nearest" in log.read_text(), "policy mouse control")
    check("POLICY nearest" in log.read_text(), "policy button restores nearest strategy")
    click(30, 180)
    wait_for(lambda: "SELECT none" in log.read_text(), "clear selection")
    name, x, y = flower_click_position()
    click(x, y)
    wait_for(lambda: "SELECT Flower / " + name in log.read_text(), "flower picking")
    check("SELECT Flower / " + name in log.read_text(), "mouse ray selects the expected flower")
    capture("desktop-flower-picker.png")

    action("WORKER", lambda: click(90, 733))
    check("workers=7" in log.read_text().split("ACTION WORKER")[-1], "worker button adds a bee")
    action("FLOWERS", lambda: click(240, 733))
    check("flowers=60" in log.read_text().split("ACTION FLOWERS")[-1], "flower button adds ten flowers")
    action("WASP", lambda: click(400, 733))
    action("VISITOR", lambda: click(555, 733))
    check("enemies=2" in log.read_text().split("ACTION VISITOR")[-1], "both enemy types spawn")
    capture("desktop-enemies.png")
    paused_time = re.findall(r"ACTION PAUSE time=([0-9.]+)", log.read_text())[-1]
    action("SPEED", lambda: click(860, 733))
    speed_time = re.findall(r"ACTION SPEED time=([0-9.]+)", log.read_text())[-1]
    check(paused_time == speed_time, "world time stays frozen while paused")
    action("THREATS", lambda: click(1015, 733))
    action("PAUSE", lambda: click(710, 733))
    time.sleep(0.7)
    press("h")
    wait_for(lambda: "SELECT Hive / Meadow hive" in log.read_text(), "hive shortcut")
    press("q")
    wait_for(lambda: "SELECT Queen bee / Queen 1" in log.read_text(), "queen shortcut")
    press("Tab")
    check("SELECT Queen bee / Queen 1" in log.read_text(), "inspector shortcuts respond")

    xdo("keydown", "Right")
    time.sleep(0.4)
    xdo("keyup", "Right")
    xdo("keydown", "Up")
    time.sleep(0.3)
    xdo("keyup", "Up")
    press("g")
    capture("desktop-orbit.png")
    press("Home")
    xdo("windowsize", window, 1100, 720)
    time.sleep(0.6)
    press("h")
    check(capture("desktop-resized.png") == (1100, 720), "resizing renders at 1100 x 720")
    xdo("windowsize", window, 1280, 800)
    time.sleep(0.4)
    action("RESET", lambda: click(1175, 733))
    reset_line = [line for line in log.read_text().splitlines() if line.startswith("ACTION RESET")][-1]
    check("time=0.00 workers=6 queen=true larvae=0 honey=20.00" in reset_line, "reset restores initial colony")
    action("SPEED", lambda: press("x"))
    action("SPEED", lambda: press("x"))
    time.sleep(8)
    action("PAUSE", lambda: press("space"))
    press("h")
    capture("desktop-completed.png")
    hatches = re.findall(r"hatched=(\d+)", log.read_text().split("ACTION PAUSE")[-1])
    check(hatches and int(hatches[-1]) >= 1, "queen and larva lifecycle progresses in the desktop app")
    press("Escape")
    app.wait(timeout=8)
    check(app.returncode == 0, "desktop exits cleanly")
    failures = [line for line in log.read_text().splitlines()
                if "WARNING:" in line or "ERROR:" in line or "Exception" in line or "SIGSEGV" in line]
    check(not failures, "desktop log has no warnings, errors or exceptions")
    (EVIDENCE / "gui-checks.txt").write_text("\n".join("PASS " + item for item in checks)
                                           + f"\nPASS: {len(checks)} desktop checks.\n")
    print(f"PASS: {len(checks)} desktop checks.", flush=True)
finally:
    if app.poll() is None:
        app.terminate()
        try:
            app.wait(timeout=5)
        except subprocess.TimeoutExpired:
            app.kill()
