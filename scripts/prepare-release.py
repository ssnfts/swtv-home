"""Sign a reviewed unsigned launcher APK locally and prepare its release assets.

The Android signing tool reads passwords interactively. This tool accepts only
the public certificate fingerprint and never accepts or stores a password.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile

PACKAGE = 'app.swtv.home'
MANIFEST_NAME = 'swtv-home-update-v1.json'
MAX_APK_BYTES = 32 * 1024 * 1024
HEX = re.compile(r'[0-9a-f]{64}')
VERSION = re.compile(r'(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)')
REPOSITORY = re.compile(r'[A-Za-z0-9](?:[A-Za-z0-9-]{0,37}[A-Za-z0-9])?/[A-Za-z0-9_.-]{1,100}')


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def inspect_badging(text):
    package = re.search(r"^package: name='([^']+)' versionCode='([0-9]+)' versionName='([^']*)'", text, re.M)
    minimum = re.findall(r"^sdkVersion:'([0-9]+)'$", text, re.M)
    target = re.findall(r"^targetSdkVersion:'([0-9]+)'$", text, re.M)
    permissions = re.findall(r"^uses-permission(?:-sdk-23)?: name='([^']+)'", text, re.M)
    if not package or package[1] != PACKAGE or minimum != ['29'] or target != ['35']:
        raise ValueError('APK package or Android SDK configuration is incorrect')
    code, name = int(package[2]), package[3]
    if not 0 < code <= 2147483647 or not VERSION.fullmatch(name):
        raise ValueError('APK version must have a positive version code and numeric major.minor.patch name')
    if 'application-debuggable' in text:
        raise ValueError('Production packaging requires a non-debuggable APK')
    if sorted(permissions) != sorted(['android.permission.INTERNET', 'android.permission.REQUEST_INSTALL_PACKAGES']):
        raise ValueError('Release APK permissions differ from the approved updater permissions')
    return code, name


def inspect_update_metadata(text):
    """Read literal application metadata from the SDK's manifest tree output."""
    stack = []
    current = None
    entries = []
    for line in text.splitlines():
        element = re.match(r'^(\s*)E: ([^\s(]+)', line)
        if element:
            depth = len(element[1])
            while stack and stack[-1][0] >= depth:
                stack.pop()
            if current is not None:
                entries.append(current)
                current = None
            if element[2] == 'meta-data' and stack and stack[-1][1] == 'application':
                current = {}
            stack.append((depth, element[2]))
        elif current is not None:
            attribute = re.search(r'A: http://schemas\.android\.com/apk/res/android:(name|value)\([^)]*\)="([^"]*)"', line)
            if attribute:
                if attribute[1] in current:
                    raise ValueError('Duplicate release metadata attribute')
                current[attribute[1]] = attribute[2]
    if current is not None:
        entries.append(current)
    expected = {'app.swtv.home.update.FEED_URL', 'app.swtv.home.update.CERT_SHA256'}
    result = {}
    for entry in entries:
        if entry.get('name') in expected:
            if entry['name'] in result or 'value' not in entry:
                raise ValueError('Duplicate or missing release trust metadata')
            result[entry['name']] = entry['value']
    if set(result) != expected:
        raise ValueError('Release APK does not contain configured update trust metadata')
    return result


def check_signature(text, expected):
    signers = re.findall(r'^Signer #[0-9]+ certificate SHA-256 digest: ([0-9a-fA-F]{64})$', text, re.M)
    if not re.search(r'^Number of signers: 1$', text, re.M) or [s.lower() for s in signers] != [expected]:
        raise ValueError('Signed APK certificate differs from the approved public fingerprint')


def capture(command):
    result = subprocess.run(command, capture_output=True, text=True, encoding='utf-8', errors='strict', timeout=60)
    if result.returncode:
        raise ValueError('An Android SDK verification command failed')
    if len(result.stdout) > 1024 * 1024:
        raise ValueError('Android SDK metadata output is unexpectedly large')
    return result.stdout


def prepare(args):
    unsigned = args.unsigned_apk.resolve(strict=True)
    key = args.keystore.resolve(strict=True)
    tools = args.build_tools.resolve(strict=True)
    output = args.out.resolve()
    project = Path(__file__).resolve().parents[1]
    if key.is_relative_to(project) or not key.is_file():
        raise ValueError('Keep the private keystore outside the source project')
    if not unsigned.is_file() or not 0 < unsigned.stat().st_size <= MAX_APK_BYTES:
        raise ValueError('Unsigned APK is missing or outside size limits')
    if output.exists() or not output.parent.is_dir():
        raise ValueError('Output must be a new directory inside an existing parent')
    if not HEX.fullmatch(args.unsigned_sha256) or not HEX.fullmatch(args.certificate_sha256):
        raise ValueError('Expected SHA-256 values must contain 64 lowercase hexadecimal digits')
    if not re.fullmatch(r'[0-9a-f]{40}', args.source_commit):
        raise ValueError('Source revision must be the full reviewed Git commit SHA')
    if not REPOSITORY.fullmatch(args.repository) or args.repository.split('/')[1] in ('.', '..'):
        raise ValueError('Expected a GitHub owner/repository')
    if not args.alias or len(args.alias) > 128 or any(ord(c) < 32 for c in args.alias):
        raise ValueError('Invalid signing key alias')
    java_name = 'java.exe' if os.name == 'nt' else 'java'
    java_home = os.environ.get('JAVA_HOME')
    java = str(Path(java_home) / 'bin' / java_name) if java_home else shutil.which(java_name)
    suffix = '.exe' if os.name == 'nt' else ''
    aapt = tools / ('aapt2' + suffix)
    align = tools / ('zipalign' + suffix)
    signer = tools / 'lib/apksigner.jar'
    if not java or not all(p.is_file() for p in (aapt, align, signer)):
        raise ValueError('JDK 17 and Android Build Tools are required')
    feed = f'https://github.com/{args.repository}/releases/latest/download/{MANIFEST_NAME}'
    with tempfile.TemporaryDirectory(prefix='.swtv-release-', dir=output.parent) as temporary:
        scratch = Path(temporary)
        snapshot = scratch / 'reviewed-unsigned.apk'
        shutil.copyfile(unsigned, snapshot)
        if digest(snapshot) != args.unsigned_sha256:
            raise ValueError('Unsigned APK bytes differ from the reviewed build checksum')
        code, version = inspect_badging(capture([str(aapt), 'dump', 'badging', str(snapshot)]))
        if args.previous_version_code is not None and code <= args.previous_version_code:
            raise ValueError('Release version code must increase')
        metadata = inspect_update_metadata(capture([str(aapt), 'dump', 'xmltree', '--file', 'AndroidManifest.xml', str(snapshot)]))
        if metadata['app.swtv.home.update.FEED_URL'] != feed or metadata['app.swtv.home.update.CERT_SHA256'] != args.certificate_sha256:
            raise ValueError('Embedded feed and certificate must match the intended release configuration')
        bundle = scratch / 'bundle'
        bundle.mkdir()
        aligned = scratch / 'aligned.apk'
        signed_name = f'swtv-home-{version}.apk'
        signed = bundle / signed_name
        capture([str(align), '-f', '4', str(snapshot), str(aligned)])
        # Password prompting belongs to apksigner's console. Invoke its JAR directly
        # to avoid passing paths or aliases through Windows batch-shell parsing.
        subprocess.run([java, '-jar', str(signer), 'sign', '--ks', str(key), '--ks-key-alias', args.alias,
                        '--min-sdk-version', '29', '--v4-signing-enabled', 'false', '--out', str(signed), str(aligned)], check=True)
        verification = capture([java, '-jar', str(signer), 'verify', '--verbose', '--print-certs', '--min-sdk-version', '29', str(signed)])
        check_signature(verification, args.certificate_sha256)
        capture([str(align), '-c', '4', str(signed)])
        if not 0 < signed.stat().st_size <= MAX_APK_BYTES:
            raise ValueError('Signed APK exceeds the update size limit')
        signed_hash = digest(signed)
        manifest = dict(schema=1, packageName=PACKAGE, versionCode=code, versionName=version, minSdk=29,
                        apkUrl=f'https://github.com/{args.repository}/releases/download/swtv-home-v{version}/{signed_name}',
                        apkBytes=signed.stat().st_size, apkSha256=signed_hash)
        manifest_file = bundle / MANIFEST_NAME
        manifest_file.write_text(json.dumps(manifest, indent=2) + '\n', encoding='ascii')
        (bundle / 'SHA256SUMS.txt').write_text(f'{signed_hash}  {signed_name}\n{digest(manifest_file)}  {MANIFEST_NAME}\n', encoding='ascii')
        receipt = dict(sourceCommit=args.source_commit, unsignedSha256=args.unsigned_sha256,
                       certificateSha256=args.certificate_sha256, manifest=MANIFEST_NAME,
                       packageName=PACKAGE, versionCode=code, versionName=version, apkSha256=signed_hash)
        (bundle / 'release-receipt.json').write_text(json.dumps(receipt, indent=2) + '\n', encoding='ascii')
        (bundle / 'apk-signature.txt').write_text(verification, encoding='utf-8')
        if output.exists():
            raise ValueError('Output directory appeared while preparing the release')
        bundle.rename(output)
    return output


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--unsigned-apk', required=True, type=Path)
    parser.add_argument('--unsigned-sha256', required=True)
    parser.add_argument('--build-tools', required=True, type=Path)
    parser.add_argument('--keystore', required=True, type=Path)
    parser.add_argument('--alias', required=True)
    parser.add_argument('--certificate-sha256', required=True)
    parser.add_argument('--repository', required=True)
    parser.add_argument('--source-commit', required=True)
    parser.add_argument('--previous-version-code', type=int)
    parser.add_argument('--out', required=True, type=Path)
    args = parser.parse_args()
    try:
        output = prepare(args)
    except (ValueError, OSError, subprocess.SubprocessError) as error:
        parser.exit(1, f'Release preparation failed: {error}\n')
    print(f'Release bundle prepared at {output}')


if __name__ == '__main__':
    main()
