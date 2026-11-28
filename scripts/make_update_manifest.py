#!/usr/bin/env python3
"""Derive update metadata from the signed APKs; never use the release title."""
import hashlib, json, os, re, subprocess, sys
from pathlib import Path

def main():
    dist = Path(sys.argv[1])
    tools = Path(os.environ['ANDROID_HOME']) / 'build-tools/35.0.0'
    build = int(os.environ['GITHUB_RUN_NUMBER'])
    files, identity, signer = {}, None, None
    for abi in ('arm64-v8a', 'armeabi-v7a', 'x86', 'x86_64', 'universal'):
        apk = dist / f'SukiRPC-{abi}.apk'
        badging = subprocess.check_output([str(tools/'aapt'), 'dump', 'badging', str(apk)], text=True)
        m = re.search(r"^package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", badging, re.M)
        if not m: raise ValueError('Missing APK identity')
        item = (m[1], int(m[2]), m[3])
        if item[0] != 'dev.nuvio.presence' or item[1] != 1000 + build or not re.fullmatch(r'\d+\.\d+\.\d+',item[2]): raise ValueError('Unexpected APK identity')
        cert = subprocess.check_output([str(tools/'apksigner'), 'verify', '--print-certs', str(apk)], text=True)
        found = tuple(sorted(re.findall(r'Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]+)', cert)))
        if not found: raise ValueError('Missing certificate')
        if identity is None: identity, signer = item, found
        if item != identity or found != signer: raise ValueError('APK identities or signatures differ')
        with apk.open('rb') as f:
            h = hashlib.sha256()
            for chunk in iter(lambda:f.read(65536), b''): h.update(chunk)
        size = apk.stat().st_size
        if not 0 < size <= 150*1024*1024: raise ValueError('APK size exceeds updater limit')
        files[abi] = {'sha256': h.hexdigest(), 'size': size}
    manifest = {'schema':1,'packageName':identity[0],'versionCode':identity[1],'versionName':identity[2],'build':build,'files':files}
    (dist/'update.json').write_text(json.dumps(manifest,indent=2)+'\n')
    print('Update manifest verified:', identity)
if __name__ == '__main__': main()
