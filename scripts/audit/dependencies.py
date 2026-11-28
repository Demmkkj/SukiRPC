"""Check resolved public Maven coordinates with OSV. Never send local artifacts."""
import concurrent.futures
import json
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

API = "https://api.osv.dev/v1/"
BATCH_SIZE = 50


def fetch(path, payload=None):
    data = json.dumps(payload).encode() if payload is not None else None
    request = urllib.request.Request(API + path, data=data, headers={"Content-Type": "application/json"})
    for attempt in range(3):
        try:
            with urllib.request.urlopen(request, timeout=25) as response:
                return json.load(response)
        except (TimeoutError, urllib.error.URLError) as error:
            if isinstance(error, urllib.error.HTTPError) and error.code not in {429, 500, 502, 503, 504}:
                raise
            if attempt == 2:
                raise
            time.sleep(attempt + 1)


def vulnerabilities(coordinates):
    queries = [{"package": {"ecosystem": "Maven", "name": name}, "version": version}
               for name, version in coordinates]
    found = [[] for _ in queries]
    for start in range(0, len(queries), BATCH_SIZE):
        batch = queries[start:start + BATCH_SIZE]
        indices = list(range(start, start + len(batch)))
        while batch:
            results = fetch("querybatch", {"queries": batch})["results"]
            if len(results) != len(batch):
                raise ValueError("OSV returned incomplete results")
            next_batch, next_indices = [], []
            for index, query, result in zip(indices, batch, results):
                found[index].extend(v["id"] for v in result.get("vulns", []))
                if token := result.get("next_page_token"):
                    next_batch.append({**query, "page_token": token})
                    next_indices.append(index)
            if next_batch == batch:
                raise ValueError("OSV repeated its page token")
            batch, indices = next_batch, next_indices
    return found


def advisory(identifier):
    if not re.fullmatch(r"[A-Za-z0-9._-]{1,100}", identifier):
        raise ValueError("Invalid OSV advisory identifier")
    item = fetch("vulns/" + urllib.parse.quote(identifier, safe=""))
    severity = item.get("database_specific", {}).get("severity", "unknown")
    return {
        "id": identifier,
        "withdrawn": bool(item.get("withdrawn")),
        "severity": severity.lower() if isinstance(severity, str) else "unknown",
        "cvss": item.get("severity", []),
        "reference": "https://osv.dev/vulnerability/" + identifier,
    }


def main(output_dir):
    modules = [json.loads((output_dir / (module + ".json")).read_text()) for module in ("app", "collector")]
    inventory = {}
    for module in modules:
        for package in module["packages"]:
            key = (package["group"] + ":" + package["name"], package["version"])
            inventory.setdefault(key, set()).add(module["module"])
    coordinates = sorted(inventory)
    matches = vulnerabilities(coordinates)
    ids = sorted({item for matching in matches for item in matching})
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        details = dict(zip(ids, pool.map(advisory, ids)))
    findings = [
        {**details[identifier], "package": name, "version": version, "modules": sorted(inventory[name, version])}
        for (name, version), matching in zip(coordinates, matches)
        for identifier in sorted(set(matching)) if not details[identifier]["withdrawn"]
    ]
    (output_dir / "osv.json").write_text(json.dumps(
        {"scanned": len(coordinates), "findings": findings}, indent=2) + "\n")
    print(f"OSV: {len(coordinates)} resolved Maven versions, {len(findings)} package/advisory matches")
    return 3 if findings else 0


if __name__ == "__main__":
    sys.exit(main(Path(sys.argv[1])))
