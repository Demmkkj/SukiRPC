#!/usr/bin/env python3
"""Fail closed if an APK is debuggable, has wrong identity or wrong signer."""
import os, re, subprocess, sys
from pathlib import Path

def verify(apk, package):
    tools = Path(os.environ['ANDROID_HOME']) / 'build-tools/35.0.0'
    expected = os.environ['EXPECTED_SIGNER']
    if not re.fullmatch(r'[0-9a-f]{64}', expected):
        raise ValueError('Missing expected signing fingerprint')
    out = subprocess.check_output([str(tools/'apksigner'), 'verify', '--print-certs', apk], text=True)
    signers = re.findall(r'Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]{64})', out)
    if [v.lower() for v in signers] != [expected]:
        raise ValueError('Unexpected APK signer')
    out = subprocess.check_output([str(tools/'aapt'), 'dump', 'badging', apk], text=True)
    if not re.search(r"^package: name='" + re.escape(package) + "' ", out, re.M):
        raise ValueError('Unexpected package')
    if re.search(r'^application-debuggable\b', out, re.M):
        raise ValueError('Debuggable APK rejected')
    print('Release identity verified:', apk)

if __name__ == '__main__':
    verify(*sys.argv[1:])
