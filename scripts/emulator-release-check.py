"""Exercise explicit install consent on the dedicated Phase 4 API 29 emulator."""
import argparse
import atexit
import json
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--adb', required=True)
parser.add_argument('--serial', default='emulator-5558')
parser.add_argument('--baseline-apk', type=Path, required=True)
parser.add_argument('--test-apk', type=Path, required=True)
parser.add_argument('--out', type=Path, required=True)
args = parser.parse_args()
if not re.fullmatch(r'emulator-\d+', args.serial):
    raise SystemExit('Refusing a non-emulator serial')
args.out.mkdir(parents=True, exist_ok=True)
records = []
active_drivers = []
runner = 'app.swtv.home.test/androidx.test.runner.AndroidJUnitRunner'
retention = 'app.swtv.home.platform.PreferencesRetentionInstrumentedTest'
driver = 'app.swtv.home.platform.UpdateFlowInstrumentedTest#interactiveUpdate'


def adb(*parts, timeout=60):
    result = subprocess.run([args.adb, '-s', args.serial, *parts], text=True, encoding='utf-8',
                            errors='replace', capture_output=True, timeout=timeout)
    records.append(dict(args=list(parts), exit=result.returncode, stdout=result.stdout, stderr=result.stderr))
    (args.out / 'commands.json').write_text(json.dumps(records, indent=2), encoding='utf-8')
    if result.returncode:
        raise RuntimeError(result.stderr or result.stdout)
    return result.stdout


def close_failed_drivers():
    for process, logfile in active_drivers:
        if process.poll() is None:
            try:
                adb('shell', 'am', 'force-stop', 'app.swtv.home')
                process.wait(timeout=10)
            except (RuntimeError, subprocess.SubprocessError):
                process.terminate()
        logfile.close()


atexit.register(close_failed_drivers)


if adb('shell', 'getprop', 'ro.kernel.qemu').strip() != '1' or adb('shell', 'getprop', 'ro.build.version.sdk').strip() != '29':
    raise SystemExit('Requires a verified API29 emulator')
if adb('emu', 'avd', 'name').splitlines()[0] != 'SWTV_PHASE4_SIGNED_API29':
    raise SystemExit('Requires the isolated SWTV_PHASE4_SIGNED_API29 AVD')
if not args.baseline_apk.is_file() or not args.test_apk.is_file():
    raise SystemExit('Baseline or test APK missing')


def version():
    value = adb('shell', 'dumpsys', 'package', 'app.swtv.home')
    found = re.search(r'\bversionCode=(\d+)\b', value)
    return int(found[1]) if found else None


def screen(name):
    adb('shell', 'uiautomator', 'dump', '/sdcard/swtv-phase4.xml')
    xml = adb('exec-out', 'cat', '/sdcard/swtv-phase4.xml')
    (args.out / f'{name}.xml').write_text(xml, encoding='utf-8')
    return ET.fromstring(xml)


def screenshot(name):
    adb('shell', 'screencap', '-p', '/sdcard/swtv-phase4.png')
    adb('pull', '/sdcard/swtv-phase4.png', str(args.out / f'{name}.png'))


def label(node):
    return node.get('text') or node.get('content-desc', '')


def find(tree, text):
    return next((n for n in tree.iter('node') if label(n).casefold() == text.casefold() and n.get('enabled') == 'true'), None)


def tap(node):
    bounds = re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.get('bounds', ''))
    if not bounds:
        raise AssertionError('Control bounds unavailable')
    x1,y1,x2,y2 = map(int,bounds.groups())
    adb('shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2))


def wait_control(text, name, system=False, timeout=60):
    end = time.monotonic()+timeout
    index = 0
    while time.monotonic() < end:
        tree = screen(f'{name}-{index}')
        node = find(tree, text)
        if node is not None and (not system or node.get('package') in ('com.google.android.packageinstaller','com.android.packageinstaller')):
            return tree, node
        time.sleep(.3)
        index += 1
    raise AssertionError(f'Timed out waiting for {text}')


def instrument(method, name):
    result = adb('shell', 'am', 'instrument', '-w', '-r', '-e', 'class', retention+'#'+method,
                 '-e', 'swtvFixtureOptIn', 'true', '-e', 'swtvEmulator', 'phase4', runner)
    (args.out / f'{name}.txt').write_text(result, encoding='utf-8')
    if not re.search(r'OK \(\d+ tests?\)', result) or 'FAILURES!!!' in result:
        raise AssertionError('Preference instrumentation failed')


def start_driver(fixture, name):
    logfile = (args.out / f'{name}-instrumentation.txt').open('w', encoding='utf-8')
    process = subprocess.Popen([args.adb, '-s', args.serial, 'shell', 'am', 'instrument', '-w', '-r',
        '-e','class',driver,'-e','swtvAllowInstall','true','-e','swtvEmulator','phase4',
        '-e','swtvFixture',fixture,'-e','swtvDriverTimeoutMs','300000',runner],stdout=logfile,stderr=subprocess.STDOUT)
    active_drivers.append((process, logfile))
    tree, button = wait_control('Check for updates',name+'-check')
    tap(button)
    tree, button = wait_control('Download update',name+'-available')
    tap(button)
    tree, button = wait_control('Install update',name+'-verified')
    screenshot(name+'-verified')
    return process, logfile, button


def finish_driver(process, logfile, force=False):
    if force:
        adb('shell','am','force-stop','app.swtv.home')
    try:
        process.wait(timeout=15)
    except subprocess.TimeoutExpired:
        process.terminate()
        process.wait(timeout=10)
    logfile.close()
    active_drivers.remove((process, logfile))


def wait_version(expected):
    end = time.monotonic()+60
    while time.monotonic() < end:
        if version() == expected:
            return
        time.sleep(.5)
    raise AssertionError(f'Expected installed version code {expected}')


adb('install','-r',str(args.baseline_apk))
adb('install','-r','-t',str(args.test_apk))
assert version()==3, 'Baseline must be code3'
instrument('seedPreferences','seed-preferences')

process, logfile, button = start_driver('update','permission')
tap(button)
tree, settings = wait_control('Allow this source in Settings','source-denied')
assert version()==3, 'Version changed before user source consent'
screenshot('source-denied')
tap(settings)
end = time.monotonic()+45
switch = None
while time.monotonic()<end:
    tree=screen('source-settings')
    text=' '.join(label(n) for n in tree.iter('node'))
    if 'SWTV Home' in text:
        parents={child:parent for parent in tree.iter() for child in parent}
        source=next((n for n in tree.iter('node') if label(n)=='SWTV Home'),None)
        while source is not None:
            choices=[n for n in source.iter('node') if n.get('checkable')=='true' and n.get('enabled')=='true']
            if len(choices)==1:
                switch=choices[0]
                break
            source=parents.get(source)
        if switch is not None:
            break
    time.sleep(.3)
if switch is None:
    raise AssertionError('Source permission Settings did not expose the launcher switch')
screenshot('source-settings-before')
if switch.get('checked')!='true':
    tap(switch)
screenshot('source-settings-after')
adb('shell','input','keyevent','KEYCODE_BACK')
tree, button=wait_control('Install update','permission-return')
tap(button)
tree, cancel=wait_control('Cancel','system-cancel',system=True)
screenshot('system-cancel')
assert version()==3, 'Version changed before Android confirmation'
tap(cancel)
time.sleep(.5)
assert version()==3, 'Cancelling system installation changed installed version'
finish_driver(process,logfile,force=True)
instrument('assertPreferences','preferences-after-cancel')

for fixture, before, after in [('update',3,4),('recovery',4,5)]:
    assert version()==before
    process, logfile, button=start_driver(fixture,fixture)
    tap(button)
    tree, install=wait_control('Install',fixture+'-system-confirm',system=True)
    screenshot(fixture+'-system-confirm')
    assert version()==before, 'Version changed before explicit system Install'
    tap(install)
    wait_version(after)
    finish_driver(process,logfile)
    instrument('assertPreferences','preferences-after-'+fixture)
    (args.out / f'{fixture}-version.txt').write_text(str(version()),encoding='ascii')

resolver=adb('shell','cmd','package','resolve-activity','--brief','-a','android.intent.action.MAIN','-c','android.intent.category.HOME')
assert 'com.google.android.tvlauncher/.MainActivity' in resolver
(args.out/'home-resolver.txt').write_text(resolver,encoding='utf-8')
(args.out/'result.json').write_text(json.dumps(dict(status='passed',target=args.serial,api=29,
    checks=['signed-baseline','preferences-seeded','source-permission-consent','system-cancel-keeps-version-and-data',
            'system-confirm-update-3-to-4','update-preferences-retained','higher-code-known-good-recovery-4-to-5',
            'recovery-preferences-retained','stock-home']),indent=2),encoding='utf-8')
print('PASS: API29 user-confirmed signed update and recovery with preferences retained')
