"""Normalize report locations without treating missing reports as clean runs."""
import csv
import json
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
from urllib.parse import unquote, urlparse

ROOT = Path.cwd()
SEVERITY = {"fatal": 0, "critical": 0, "error": 1, "high": 1, "warning": 2, "medium": 2, "moderate": 2,
            "low": 3, "note": 3, "information": 3, "unknown": 4}


def location(value):
    uri = urlparse(value or "")
    if uri.scheme not in ("", "file") or uri.netloc:
        return ""
    path = Path(unquote(uri.path))
    if path.is_absolute():
        return str(path.relative_to(ROOT)) if path.is_relative_to(ROOT) else ""
    return str(path) if value and ".." not in path.parts else ""


def read_lint(report):
    for issue in ET.parse(report).findall("issue"):
        places = issue.findall("location") or [ET.Element("location")]
        for pos in places:
            yield (issue.get("severity", "unknown").lower(), location(pos.get("file")),
                   int(pos.get("line", "0")), issue.get("id", ""), issue.get("message", ""))


def read_sarif(report):
    for run in json.loads(report.read_text())["runs"]:
        for result in run.get("results", []):
            for pos in result.get("locations") or [{}]:
                physical = pos.get("physicalLocation", {})
                yield (result.get("level", "warning"), location(physical.get("artifactLocation", {}).get("uri")),
                       physical.get("region", {}).get("startLine", 0), result.get("ruleId", ""),
                       result.get("message", {}).get("text", ""))


def read_osv(report):
    for issue in json.loads(report.read_text())["findings"]:
        yield (issue["severity"], "", 0, issue["id"],
               f'{issue["package"]}@{issue["version"]} ({", ".join(issue["modules"])})')


def read_clang(report):
    pattern = re.compile(r"^(.+?):(\d+):\d+: (fatal error|error|warning|note): (.*?)(?: \[([^\]]+)\])?$")
    for line in report.read_text(errors="replace").splitlines():
        match = pattern.match(line)
        if match:
            path, number, severity, message, rule = match.groups()
            local = location(path)
            # Exclude proprietary headers, generated files and toolchain output from artifacts.
            if local.startswith("app/src/main/cpp/"):
                yield (severity, local, int(number), rule or "clang", message)


def read_actionlint(report):
    for result in json.loads(report.read_text()) or []:
        yield ("error", location(result.get("filepath")), result.get("line", 0),
               result.get("kind", ""), result.get("message", ""))


def main(output, native_log):
    table = []
    loaders = {
        "lint": (list((output / "lint").glob("*.xml")), read_lint),
        "detekt": ([output / "detekt/report.sarif"], read_sarif),
        "ktlint": ([output / "ktlint/report.sarif"], read_sarif),
        "clang-tidy": ([native_log], read_clang),
        "dependencies": ([output / "dependencies/osv.json"], read_osv),
        **{tool: ([output / tool / "report.sarif"], read_sarif)
           for tool in ("gitleaks", "semgrep", "osv-scanner", "zizmor")},
        "actionlint": ([output / "actionlint/report.json"], read_actionlint),
    }
    status = list(csv.reader((output / "status.tsv").open(), delimiter="\t"))
    count_by_tool = {}
    parse_errors = []
    for tool, code in status[1:]:
        reports, reader = loaders.get(tool, ([], None))
        existing = [p for p in reports if p.is_file()]
        findings = []
        for path in existing:
            try:
                findings.extend(reader(path))
            except (ValueError, KeyError, OSError, ET.ParseError) as error:
                parse_errors.append(f"{tool}/{path.name}: {type(error).__name__}")
        if tool == "clang-tidy":
            (output / "clang-tidy/report.json").write_text(json.dumps([
                dict(zip(("severity", "file", "line", "rule", "message"), row))
                for row in findings
            ], indent=2) + "\n")
        count_by_tool[tool] = len(findings)
        table += [(tool, *item) for item in findings]
    table.sort(key=lambda item: (item[0], SEVERITY.get(item[1], 4), item[2], item[3], item[4]))
    with (output / "findings.tsv").open("w") as out:
        writer = csv.writer(out, delimiter="\t")
        writer.writerow(["tool", "severity", "file", "line", "rule", "message"])
        writer.writerows(table)
    with (output / "summary.md").open("w") as out:
        out.write("# SukiRPC code audit\n\n| Tool | Result | Reported locations |\n| --- | --- | ---: |\n")
        for tool, code in status[1:]:
            outcome = {"0": "completed", "77": "not run (see run.log)"}.get(code, f"exit {code}")
            out.write(f"| {tool} | {outcome} | {count_by_tool[tool]} |\n")
        out.write("\nA nonzero exit may indicate findings, a failing test or an environment error. "
                  "Check each tool report and its status before interpreting the count.\n")
        if parse_errors:
            out.write("\nReport parsing errors:\n")
            for error in parse_errors:
                out.write(f"- {error}\n")
    return 1 if parse_errors else 0


if __name__ == "__main__":
    sys.exit(main(*(Path(value) for value in sys.argv[1:])))
