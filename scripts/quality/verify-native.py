"""Verify sanitizers reached the packaged native library without uploading APKs."""
import json
import sys
from pathlib import Path
from zipfile import ZipFile

reports = []
for variant, marker in (("hwasan", b"__hwasan"), ("ubsan", b"__ubsan_handle")):
    folder = Path("app/build/outputs/apk") / variant
    apk = folder / ("app-" + variant + ".apk")
    with ZipFile(apk) as bundle:
        library = bundle.read("lib/arm64-v8a/libnuvio_rpc.so")
        if marker not in library:
            raise SystemExit(f"{variant}: native instrumentation marker missing")
        if variant == "hwasan" and b"LD_HWASAN=1" not in bundle.read("lib/arm64-v8a/wrap.sh"):
            raise SystemExit("HWASan launcher not packaged")
    reports.append({"variant": variant, "abi": "arm64-v8a", "status": "instrumented binary verified"})
Path(sys.argv[1]).write_text(json.dumps(reports, indent=2) + "\n")
print("Both sanitizer builds contain the expected native instrumentation")
