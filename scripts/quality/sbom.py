"""Build an OSV-compatible CycloneDX SBOM from the resolved Maven inventory."""
import json
import sys
from pathlib import Path
from urllib.parse import quote

folder = Path(sys.argv[1])
packages = {
    (p["group"], p["name"], p["version"])
    for name in ("app", "collector")
    for p in json.loads((folder / (name + ".json")).read_text())["packages"]
}
if not packages:
    raise SystemExit("Empty Maven inventory")
test_groups = {"junit", "org.robolectric", "io.mockk", "com.squareup.leakcanary", "androidx.benchmark", "app.cash.paparazzi"}
for group, name, version in packages:
    if group in test_groups or name in {"mockwebserver", "kotlinx-coroutines-test", "kotlinx-coroutines-test-jvm"}:
        raise SystemExit(f"Test or diagnostic dependency reached releaseRuntimeClasspath: {group}:{name}:{version}")
components = []
for group, name, version in sorted(packages):
    purl = "pkg:maven/" + quote(group, safe="") + "/" + quote(name, safe="") + "@" + quote(version, safe="")
    components.append({"type": "library", "group": group, "name": name, "version": version,
                       "bom-ref": purl, "purl": purl})
(folder / "runtime.cdx.json").write_text(json.dumps({
    "bomFormat": "CycloneDX", "specVersion": "1.5", "version": 1, "components": components
}, indent=2) + "\n")
