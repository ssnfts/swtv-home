"""Run Android platform regression tests on an explicitly verified emulator."""
import argparse
import json
from pathlib import Path
import re
import subprocess

parser = argparse.ArgumentParser()
parser.add_argument('--adb', required=True)
parser.add_argument('--serial', default='emulator-5556')
parser.add_argument('--apk', required=True)
parser.add_argument('--test-apk', required=True)
parser.add_argument('--out', required=True)
args = parser.parse_args()
if not re.fullmatch(r'emulator-\d+', args.serial):
    raise SystemExit('Refusing a non-emulator serial')
out = Path(args.out)
out.mkdir(parents=True, exist_ok=True)
history = []

def adb(*parts, timeout=120):
    result = subprocess.run([args.adb, '-s', args.serial, *parts], capture_output=True,
                            text=True, encoding='utf-8', errors='replace', timeout=timeout)
    history.append({'args': list(parts), 'exit': result.returncode,
                    'stdout': result.stdout, 'stderr': result.stderr})
    (out / 'commands.json').write_text(json.dumps(history, indent=2), encoding='utf-8')
    if result.returncode:
        raise RuntimeError(result.stderr or result.stdout)
    return result.stdout

# No install or instrumentation operation precedes these identity checks.
if adb('shell', 'getprop', 'ro.kernel.qemu').strip() != '1':
    raise SystemExit('Target did not prove it is an emulator')
if adb('shell', 'getprop', 'ro.build.version.sdk').strip() != '29':
    raise SystemExit('Expected API29 emulator')
if not Path(args.apk).is_file() or not Path(args.test_apk).is_file():
    raise SystemExit('Application or test APK missing')
adb('install', '-r', '-t', args.apk)
adb('install', '-r', '-t', args.test_apk)
result = adb('shell', 'am', 'instrument', '-w', '-r',
             'app.swtv.home.test/androidx.test.runner.AndroidJUnitRunner')
(out / 'instrumentation.txt').write_text(result, encoding='utf-8')
passed = bool(re.search(r'OK \(\d+ tests?\)', result)) and 'FAILURES!!!' not in result
(out / 'result.json').write_text(json.dumps({'status': 'passed' if passed else 'failed',
    'target': args.serial, 'api': 29, 'runner': 'androidx.test.runner.AndroidJUnitRunner'}, indent=2), encoding='utf-8')
print(result)
if not passed:
    raise SystemExit('Android platform regression tests failed')
