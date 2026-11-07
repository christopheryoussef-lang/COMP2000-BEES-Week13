#!/usr/bin/env python3
"""Optional Linux launch checks; application and Java tests need only a JDK."""
from pathlib import Path
import csv
import io
import json
import os
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
checks = []

def check(condition, description):
    if not condition:
        raise AssertionError(description)
    checks.append('PASS ' + description)
    print(checks[-1], flush=True)

def run(arguments, cwd=ROOT, timeout=30):
    return subprocess.run(['java', '-Xmx384m', '-cp', str(ROOT / 'build/classes'), 'App', *arguments],
                          cwd=cwd, capture_output=True, text=True, timeout=timeout)

for name in ['.vscode/settings.json', '.vscode/launch.json']:
    json.loads((ROOT / name).read_text())
    check(True, 'valid JSON: ' + name)
for arguments in [['--headless', '--seconds', '-1'], ['--headless', '--seconds', 'NaN'],
                  ['--invalid', 'value'], ['--seed'], ['--policy', 'unknown'],
                  ['--compare', '--runs', '0'], ['--compare', '--runs', '33'],
                  ['--compare', '--seconds', '3601'], ['--parallel'], ['--frames', '-1']]:
    result = run(arguments)
    check(result.returncode == 1 and 'Bee Simulator:' in result.stderr,
          'invalid arguments rejected: ' + ' '.join(arguments))
result = run(['--headless', '--policy', 'richest', '--seconds', '300'])
check(result.returncode == 0 and 'time=300.00' in result.stdout, 'richest-policy headless run completes')
serial = run(['--compare', '--runs', '3', '--seconds', '120', '--threats'])
parallel = run(['--compare', '--runs', '3', '--seconds', '120', '--threats', '--parallel'])
check(serial.returncode == parallel.returncode == 0 and serial.stdout == parallel.stdout,
      'serial and parallel CLI reports are byte-identical')
rows = list(csv.DictReader(io.StringIO(serial.stdout)))
check(len(rows) == 6 and {r['policy'] for r in rows} == {'nearest', 'richest'}, 'CSV contains every paired trial')
check(not list((ROOT / 'src').rglob('*.jar')) and not (ROOT / 'lib').exists(), 'delivery has no library directory or source JAR')
result = subprocess.run(['java', '-Djava.awt.headless=true', '-cp', str(ROOT / 'build/classes'), 'App'],
                        capture_output=True, text=True, timeout=15)
check(result.returncode == 1 and 'A desktop display is required' in result.stderr, 'no-display launch gives clear headless guidance')
with tempfile.TemporaryDirectory(prefix='bees launch checks ') as temporary:
    result = subprocess.run(['xvfb-run', '-a', 'java', '-cp', str(ROOT / 'build/classes'), 'App', '--frames', '1'],
                            cwd=temporary, capture_output=True, text=True, timeout=30)
    check(result.returncode == 1 and 'Missing Assets/Bee.png' in result.stdout + result.stderr,
          'missing assets produce a clear error before opening a window')
    # The distributed launcher must handle its own folder and paths containing spaces.
    result = subprocess.run([str(ROOT / 'run.sh'), '--compare', '--runs', '1', '--seconds', '1'],
                            cwd=temporary, capture_output=True, text=True, timeout=90)
    check(result.returncode == 0 and result.stdout.startswith('policy,seed,seconds,'),
          'launcher works from another directory and keeps build output out of CSV')
result = subprocess.run(['jdeps', '-summary', '-recursive', str(ROOT / 'build/classes')],
                        capture_output=True, text=True, timeout=30)
check(result.returncode == 0 and all(line.rsplit(' -> ', 1)[-1] in {'java.base', 'java.desktop'}
                                   for line in result.stdout.splitlines() if ' -> ' in line),
      'compiled application depends only on java.base and java.desktop')
(ROOT / 'evidence/dependencies.txt').write_text(result.stdout)
(ROOT / 'evidence/launch-checks.txt').write_text('\n'.join(checks) + f'\nPASS: {len(checks)} launch checks.\n')
