#!/usr/bin/env python3
"""Optional Linux checks that all controls fit on smaller laptop displays."""
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
PROBE = r'''
import struct,subprocess,sys,time
from pathlib import Path
width,height=map(int,sys.argv[1:3])
root=Path.cwd()
name=f'desktop-screen-{width}x{height}'
log=root/'evidence'/f'{name}.log'
shot=root/'evidence'/f'{name}.png'
with log.open('w') as handle:
    app=subprocess.Popen(['java','-Xmx384m','-cp','build/classes','App','--screenshot',str(shot)],stdout=handle,stderr=subprocess.STDOUT)
def xdo(*args):
    return subprocess.check_output(['xdotool',*map(str,args)],text=True).strip()
def wait_for(predicate):
    end=time.monotonic()+12
    while time.monotonic()<end:
        if predicate(): return
        if app.poll() is not None: raise AssertionError(log.read_text())
        time.sleep(.05)
    raise AssertionError('Timed out: '+log.read_text())
try:
    window=None
    end=time.monotonic()+12
    while time.monotonic()<end:
        found=subprocess.run(['xdotool','search','--onlyvisible','--pid',str(app.pid),'--name','^Bee Simulator$'],text=True,capture_output=True)
        if found.returncode==0:
            window=found.stdout.splitlines()[0];break
        time.sleep(.05)
    assert window is not None,'No window'
    geometry=dict(line.split('=',1) for line in xdo('getwindowgeometry','--shell',window).splitlines())
    x,y,w,h=(int(geometry[key]) for key in ['X','Y','WIDTH','HEIGHT'])
    assert x>=0 and y>=0 and x+w<=width and y+h<=height,geometry
    print(f'PASS {width}x{height}: entire {w}x{h} window fits on screen.',flush=True)
    xdo('windowfocus','--sync',window)
    cell=(w-40)//8
    xdo('mousemove','--window',window,20+4*cell+(cell-8)//2,h-67,'click','1')
    wait_for(lambda:'ACTION PAUSE' in log.read_text())
    print(f'PASS {width}x{height}: bottom pause button is visible and clickable.',flush=True)
    xdo('key','p');wait_for(lambda:'POLICY richest' in log.read_text())
    xdo('key','h');wait_for(lambda:'SELECT Hive / Meadow hive' in log.read_text())
    xdo('key','Escape');app.wait(timeout=12)
    assert app.returncode==0,log.read_text()
    data=shot.read_bytes()
    assert data[:8]==b'\x89PNG\r\n\x1a\n' and struct.unpack('>II',data[16:24])==(w,h)
    assert 'Exception' not in log.read_text(),log.read_text()
    print(f'PASS {width}x{height}: policy shortcut, hive inspector, screenshot and clean exit.',flush=True)
finally:
    if app.poll() is None: app.terminate();app.wait(timeout=5)
'''
output=[]
for width,height in [(1366,768),(1024,720),(1366,680)]:
    result=subprocess.run(['xvfb-run','-a','-s',f'-screen 0 {width}x{height}x24','python3','-c',PROBE,str(width),str(height)],
                          cwd=ROOT,capture_output=True,text=True,timeout=45)
    if result.returncode:
        raise AssertionError(result.stdout+result.stderr)
    output.append(result.stdout.strip())
output.append('PASS: 9 screen-fit checks.')
(ROOT/'evidence/screen-fit-checks.txt').write_text('\n'.join(output)+'\n')
print('\n'.join(output))
