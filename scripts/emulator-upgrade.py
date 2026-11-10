"""Verify Phase2 -> Phase3 preference preservation on API29 emulator only."""
import argparse
import json
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser()
parser.add_argument('--adb', required=True)
parser.add_argument('--serial', default='emulator-5556')
parser.add_argument('--apk', required=True)
parser.add_argument('--out', required=True)
args = parser.parse_args()
if not re.fullmatch(r'emulator-\d+', args.serial):
    raise SystemExit('Refusing a non-emulator serial')
out = Path(args.out)
out.mkdir(parents=True, exist_ok=True)
history = []

def adb(*parts, timeout=90):
    result = subprocess.run([args.adb, '-s', args.serial, *parts], capture_output=True,
                            text=True, encoding='utf-8', errors='replace', timeout=timeout)
    history.append({'args': list(parts), 'exit': result.returncode,
                    'stdout': result.stdout, 'stderr': result.stderr})
    (out / 'commands.json').write_text(json.dumps(history, indent=2), encoding='utf-8')
    if result.returncode:
        raise RuntimeError(result.stderr or result.stdout)
    return result.stdout

if adb('shell', 'getprop', 'ro.kernel.qemu').strip() != '1':
    raise SystemExit('Target did not prove it is an emulator')
if adb('shell', 'getprop', 'ro.build.version.sdk').strip() != '29':
    raise SystemExit('Expected API29 emulator')

def version():
    text = adb('shell', 'dumpsys', 'package', 'app.swtv.home')
    match = re.search(r'\bversionCode=(\d+)\b', text)
    return int(match.group(1)) if match else None

def prefs(name):
    text = adb('exec-out', 'run-as', 'app.swtv.home', 'cat', 'shared_prefs/home.xml')
    (out / (name + '.xml')).write_text(text, encoding='utf-8')
    entry = ET.fromstring(text).find("string[@name='config']")
    if entry is None:
        raise AssertionError('Phase2 configuration missing; refusing to invent a baseline')
    value = json.loads(entry.text)
    (out / (name + '.json')).write_text(json.dumps(value, indent=2), encoding='utf-8')
    return json.dumps(value, sort_keys=True, ensure_ascii=False, separators=(',', ':'))

old_version = version()
if old_version != 1:
    raise SystemExit('Expected installed Phase2 code1; no downgrade or data reset will be attempted')
before = prefs('before')
if not Path(args.apk).is_file():
    raise SystemExit('New development APK missing')
adb('install', '-r', args.apk)
if version() != 2:
    raise AssertionError('New application version is not code2')
startup = adb('shell', 'am', 'start', '-W', '-S', '-n', 'app.swtv.home/.MainActivity')
(out / 'startup.txt').write_text(startup, encoding='utf-8')
time.sleep(.5)
after = prefs('after')
if before != after:
    raise AssertionError('Existing home/config schema1 data changed across upgrade')
(out / 'result.json').write_text(json.dumps({'status': 'passed', 'target': args.serial,
    'fromVersionCode': old_version, 'toVersionCode': 2,
    'checks': ['same-package update accepted', 'schema1 preference values and types preserved',
               'updated activity started without clearing app data'],
    'limits': ['Emulator-only ordinary debug update; not a production rollback or physical-TV deployment test.']}, indent=2), encoding='utf-8')
print('PASS: Phase2 code1 -> Phase3 code2 preserves existing configuration')
