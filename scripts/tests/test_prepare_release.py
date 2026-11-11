import argparse
import importlib.util
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

SCRIPT = Path(__file__).resolve().parents[1] / 'prepare-release.py'
SPEC = importlib.util.spec_from_file_location('prepare_release', SCRIPT)
release = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(release)

PIN = 'a' * 64
FEED = 'https://github.com/example-owner/swtv-home/releases/latest/download/swtv-home-update-v1.json'
BADGING = """package: name='app.swtv.home' versionCode='3' versionName='0.3.0' platformBuildVersionName='15'
sdkVersion:'29'
targetSdkVersion:'35'
uses-permission: name='android.permission.INTERNET'
uses-permission: name='android.permission.REQUEST_INSTALL_PACKAGES'
"""
TREE = f'''E: manifest (line=1)
  E: application (line=10)
    E: meta-data (line=11)
      A: http://schemas.android.com/apk/res/android:name(0x01010003)="app.swtv.home.update.FEED_URL"
      A: http://schemas.android.com/apk/res/android:value(0x01010024)="{FEED}"
    E: meta-data (line=12)
      A: http://schemas.android.com/apk/res/android:name(0x01010003)="app.swtv.home.update.CERT_SHA256"
      A: http://schemas.android.com/apk/res/android:value(0x01010024)="{PIN}"
    E: activity (line=13)
'''


class ReleaseMetadataTest(unittest.TestCase):
    def test_rejects_debug_apks_and_incompatible_package_versions_and_permissions(self):
        self.assertEqual((3, '0.3.0'), release.inspect_badging(BADGING))
        invalid = [BADGING + 'application-debuggable\n',
                   BADGING.replace('app.swtv.home', 'other.app'),
                   BADGING.replace("sdkVersion:'29'", "sdkVersion:'30'"),
                   BADGING.replace("targetSdkVersion:'35'", "targetSdkVersion:'34'"),
                   BADGING.replace("versionCode='3'", "versionCode='0'"),
                   BADGING.replace("versionName='0.3.0'", "versionName='0.3.0-beta'"),
                   BADGING + "uses-permission: name='android.permission.READ_CONTACTS'\n",
                   BADGING + "uses-permission-sdk-23: name='android.permission.READ_CONTACTS'\n",
                   BADGING.replace("uses-permission: name='android.permission.REQUEST_INSTALL_PACKAGES'\n", '')]
        for value in invalid:
            with self.subTest(value=value), self.assertRaises(ValueError):
                release.inspect_badging(value)

    def test_requires_unique_literal_application_trust_metadata(self):
        self.assertEqual({'app.swtv.home.update.FEED_URL': FEED,
                          'app.swtv.home.update.CERT_SHA256': PIN}, release.inspect_update_metadata(TREE))
        for value in [TREE.replace('E: application', 'E: activity'),
                      TREE.replace('http://schemas.android.com/apk/res/android:', 'http://example.invalid/attributes:'),
                      TREE.replace(f'"{PIN}"', '@string/certificate'),
                      TREE.replace('app.swtv.home.update.CERT_SHA256', 'unrelated')]:
            with self.subTest(value=value), self.assertRaises(ValueError):
                release.inspect_update_metadata(value)
        duplicate = TREE.replace('    E: activity (line=13)',
                                 '    E: meta-data\n      A: http://schemas.android.com/apk/res/android:name(0x01010003)="app.swtv.home.update.CERT_SHA256"\n'
                                 f'      A: http://schemas.android.com/apk/res/android:value(0x01010024)="{PIN}"\n    E: activity')
        with self.assertRaises(ValueError):
            release.inspect_update_metadata(duplicate)

    def test_signature_requires_one_approved_certificate(self):
        valid = f'Verifies\nNumber of signers: 1\nSigner #1 certificate SHA-256 digest: {PIN}\n'
        release.check_signature(valid, PIN)
        for value in [valid.replace(PIN, 'b' * 64), valid.replace('signers: 1', 'signers: 2'),
                      valid + f'Signer #2 certificate SHA-256 digest: {PIN}\n']:
            with self.subTest(value=value), self.assertRaises(ValueError):
                release.check_signature(value, PIN)

    def test_tampered_ci_input_never_invokes_tools_or_publishes_bundle(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            apk, key, tools, output = (root / name for name in ('unsigned.apk', 'qa.p12', 'tools', 'release'))
            apk.write_bytes(b'changed after review')
            key.write_bytes(b'disposable test data')
            (tools / 'lib').mkdir(parents=True)
            suffix = '.exe' if os.name == 'nt' else ''
            for file in (tools / ('aapt2' + suffix), tools / ('zipalign' + suffix), tools / 'lib/apksigner.jar'):
                file.touch()
            args = argparse.Namespace(unsigned_apk=apk, unsigned_sha256='0' * 64, build_tools=tools,
                                      keystore=key, alias='qa', certificate_sha256=PIN,
                                      repository='example-owner/swtv-home', source_commit='b' * 40,
                                      previous_version_code=2, out=output)
            with patch.dict(os.environ, {'JAVA_HOME': str(root / 'java')}), patch.object(release, 'capture') as capture:
                with self.assertRaisesRegex(ValueError, 'checksum'):
                    release.prepare(args)
                capture.assert_not_called()
            self.assertFalse(output.exists())
            self.assertEqual(b'changed after review', apk.read_bytes())
            self.assertFalse(list(root.glob('.swtv-release-*')))


if __name__ == '__main__':
    unittest.main()
