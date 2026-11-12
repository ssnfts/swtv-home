"""Repeatable development smoke checks; refuses every non-emulator target."""
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
parser.add_argument('--extended', action='store_true')
parser.add_argument('--phase3', '--check-status', dest='check_status', action='store_true')
parser.add_argument('--expected-version', default='0.4.0')
args = parser.parse_args()
if not re.fullmatch(r'emulator-\d+', args.serial):
    raise SystemExit('Refusing a non-emulator serial')
out = Path(args.out)
out.mkdir(parents=True, exist_ok=True)
history = []

def adb(*parts, timeout=40):
    result = subprocess.run([args.adb, '-s', args.serial, *parts], capture_output=True,
                            text=True, encoding='utf-8', errors='replace', timeout=timeout)
    history.append({'args': list(parts), 'exit': result.returncode, 'stdout': result.stdout, 'stderr': result.stderr})
    (out / 'commands.json').write_text(json.dumps(history, indent=2), encoding='utf-8')
    if result.returncode:
        raise RuntimeError(result.stderr or result.stdout)
    return result.stdout

# These checks precede install, input injection, process stop or any other mutation.
if adb('shell', 'getprop', 'ro.kernel.qemu').strip() != '1':
    raise SystemExit('Target did not prove it is an emulator')
if adb('shell', 'getprop', 'ro.build.version.sdk').strip() != '29':
    raise SystemExit('This test requires API29')
if not Path(args.apk).is_file():
    raise SystemExit('APK not found')

def screen(name):
    time.sleep(.3)
    adb('shell', 'uiautomator', 'dump', '/sdcard/swtv-smoke.xml')
    xml = adb('exec-out', 'cat', '/sdcard/swtv-smoke.xml')
    (out / (name + '.xml')).write_text(xml, encoding='utf-8')
    return ET.fromstring(xml)

def screenshot(name):
    adb('shell', 'screencap', '-p', '/sdcard/swtv-smoke.png')
    adb('pull', '/sdcard/swtv-smoke.png', str(out / (name + '.png')))

def labels(tree):
    return '\n'.join(n.get('text', '') + ' ' + n.get('content-desc', '') for n in tree.iter('node'))

def focus(tree):
    return [(n.get('text'), n.get('content-desc'), n.get('bounds')) for n in tree.iter('node') if n.get('focused') == 'true']

def tap(tree, label):
    nodes = [n for n in tree.iter('node') if n.get('text', '').casefold() == label.casefold() or n.get('content-desc', '').casefold() == label.casefold()]
    if not nodes and label == 'TV Hub':
        nodes = [n for n in tree.iter('node') if n.get('text', '').startswith('TV Hub')]
    if not nodes and label == 'Inputs':
        nodes = [n for n in tree.iter('node') if n.get('text') == 'HDMI / Inputs']
    if not nodes:
        raise AssertionError('Missing control: ' + label)
    bounds = re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', nodes[0].get('bounds', ''))
    if not bounds:
        raise AssertionError('Invalid bounds: ' + label)
    x1, y1, x2, y2 = map(int, bounds.groups())
    adb('shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2))

adb('install', '-r', args.apk, timeout=120)
startup = adb('shell', 'am', 'start', '-W', '-S', '-n', 'app.swtv.home/.MainActivity')
(out / 'startup.txt').write_text(startup, encoding='utf-8')
tree = screen('home')
text = labels(tree)
for label in ['OwnTV', 'TV Hub', 'Inputs', 'Settings']:
    if label not in text:
        raise AssertionError('Missing home label: ' + label)
screenshot('home')

if args.check_status:
    tap(tree, 'About')
    about = screen('about-updates')
    about_text = labels(about)
    version_pattern = r'(?<![\w.])' + re.escape(args.expected_version) + r'(?![\w.])'
    if not re.search(version_pattern, about_text) or not re.search(r'not configured|aren.?t configured|unconfigured', about_text, re.I):
        raise AssertionError(f'About did not show {args.expected_version} and the unconfigured update state')
    screenshot('about-updates')
    adb('shell', 'input', 'keyevent', 'KEYCODE_BACK')
    tree = screen('before-diagnostics')
    tap(tree, 'Diagnostics')
    diagnostic = screen('diagnostics')
    diagnostic_text = labels(diagnostic)
    if not re.search(r'\b(?:API|SDK)\s*:?\s*29\b', diagnostic_text):
        raise AssertionError('Diagnostics did not report actual emulator API29')
    if not re.search(r'\b(?:RAM|memory)\b', diagnostic_text, re.I):
        raise AssertionError('Diagnostics did not expose memory information')
    screenshot('diagnostics')
    adb('shell', 'input', 'keyevent', 'KEYCODE_BACK')
    tree = screen('after-diagnostics')

# A directional key must produce an actual focus transition, not merely redraw.
adb('shell', 'input', 'keyevent', 'KEYCODE_DPAD_RIGHT')
before = focus(screen('focus-before'))
adb('shell', 'input', 'keyevent', 'KEYCODE_DPAD_RIGHT')
after = focus(screen('focus-after'))
if not before or not after or before == after:
    raise AssertionError('D-pad did not advance between focusable controls')

tree = screen('before-owntv')
tap(tree, 'OwnTV')
own = screen('owntv-dialog')
own_text = labels(own).lower()
if 'github' not in own_text and 'releases' not in own_text:
    raise AssertionError('OwnTV absent-app UI did not offer GitHub/releases')
screenshot('owntv-dialog')
adb('shell', 'input', 'keyevent', 'KEYCODE_BACK')
tree = screen('before-hub')
tap(tree, 'TV Hub')
hub = screen('hub-dialog')
if not any(word in labels(hub).lower() for word in ['future', 'reserved', 'development', 'coming']):
    raise AssertionError('TV Hub reservation was not explained')
adb('shell', 'input', 'keyevent', 'KEYCODE_BACK')
tree = screen('before-settings')
tap(tree, 'Settings')
settings = screen('settings')
screenshot('settings')
if 'theme' not in labels(settings).lower():
    raise AssertionError('Settings did not expose theme configuration')
adb('shell', 'input', 'keyevent', 'KEYCODE_BACK')

if args.extended:
    tree = screen('before-theme')
    tap(tree, 'Settings')
    tree = screen('theme-settings')
    theme_row = next((n.get('text') for n in tree.iter('node') if n.get('text', '').startswith('Theme: ')), None)
    if not theme_row:
        raise AssertionError('Theme row missing')
    tap(tree, theme_row)
    tree = screen('theme-choices')
    tap(tree, 'dark')
    screen('dark-home')
    screenshot('home-dark')

    def saved_config():
        xml = adb('exec-out', 'run-as', 'app.swtv.home', 'cat', 'shared_prefs/home.xml')
        settings_xml = ET.fromstring(xml)
        entry = settings_xml.find("string[@name='config']")
        if entry is None:
            raise AssertionError('No persisted config')
        return json.loads(entry.text)

    if saved_config().get('theme') != 'dark':
        raise AssertionError('Theme selection not persisted')
    tree = screen('before-columns')
    tap(tree, 'Settings')
    tree = screen('column-settings')
    column_row = next(n.get('text') for n in tree.iter('node') if n.get('text', '').startswith('Grid columns:'))
    tap(tree, column_row)
    tree = screen('column-choices')
    tap(tree, '4')
    if saved_config().get('columns') != 4:
        raise AssertionError('Column selection not persisted')
    # Process recreation must preserve app-local settings without setting HOME.
    adb('shell', 'am', 'start', '-W', '-S', '-n', 'app.swtv.home/.MainActivity')
    recreated = screen('recreated')
    if saved_config().get('theme') != 'dark' or saved_config().get('columns') != 4:
        raise AssertionError('Configuration lost on recreation')
    # Exercise long-OK using remote key injection on a focusable app tile.
    # A prior targeted tap can leave Android in touch mode; the first D-pad
    # event then establishes focus. Navigate until an actual app has focus.
    for attempt in range(7):
        adb('shell', 'input', 'keyevent', 'KEYCODE_DPAD_DOWN')
        app_focus = screen('app-focus-' + str(attempt))
        focused = [n for n in app_focus.iter('node') if n.get('focused') == 'true']
        if focused:
            name = focused[0].get('content-desc') or focused[0].get('text', '')
            if name.casefold() not in ['settings', 'system settings', 'about', 'diagnostics', 'owntv', 'tv hub', 'inputs', 'hdmi / inputs', 'add favorite', '+ add favorite', 'add website', '+ add website'] and not name.startswith(('+ Slot ', 'TV Hub')):
                break
    else:
        raise AssertionError('D-pad could not reach an app tile')
    if not focused:
        raise AssertionError('No D-pad focus after traversing home rows')
    # API29's shell --longpress synthesized a short click here. Hold the
    # focused tile with a stationary touch gesture to verify its long-click
    # action; real held-OK remote behavior remains a physical-device check.
    bounds = re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', focused[0].get('bounds', ''))
    x1, y1, x2, y2 = map(int, bounds.groups())
    x, y = str((x1+x2)//2), str((y1+y2)//2)
    adb('shell', 'input', 'swipe', x, y, x, y, '900')
    menu = screen('app-menu')
    if 'Pin to dock' not in labels(menu) and 'Unpin from dock' not in labels(menu):
        raise AssertionError('Long-press did not open app context menu')
    tap(menu, 'Pin to dock' if 'Pin to dock' in labels(menu) else 'Unpin from dock')
    screen('pin-result')
    screenshot('home-configured')

(out / 'memory.txt').write_text(adb('shell', 'dumpsys', 'meminfo', 'app.swtv.home'), encoding='utf-8')
(out / 'graphics.txt').write_text(adb('shell', 'dumpsys', 'gfxinfo', 'app.swtv.home'), encoding='utf-8')
log = adb('logcat', '-d', '-t', '400', '-v', 'brief')
(out / 'logcat.txt').write_text(log, encoding='utf-8')
if re.search(r'FATAL EXCEPTION[\s\S]{0,600}Process: app\.swtv\.home', log):
    raise AssertionError('Launcher fatal exception in emulator log')
(out / 'result.json').write_text(json.dumps({'status':'passed', 'target':args.serial, 'api':29,
    'checks':['install','startup','home-controls','dpad-focus-transition','owntv-absent-path','hub-reservation','settings-theme-entry','no-observed-fatal-exception'] + (['theme-persistence','grid-columns-persistence','process-recreation','dpad-row-traversal','long-press-app-menu'] if args.extended else []) + (['dynamic-build-identity','unconfigured-update-provider','read-only-diagnostics-api-memory'] if args.check_status else []),
    'limits':['Targeted taps were used for dialog entry; comprehensive D-pad traversal requires additional testing.',
              'App long-click was tested with a stationary touch hold because API29 shell held-key synthesis did not reproduce a held remote OK.',
              'No physical-TV navigation, HDMI switching or installed-OwnTV playback was tested.']}, indent=2), encoding='utf-8')
print('PASS: Android29 TV emulator smoke checks')
