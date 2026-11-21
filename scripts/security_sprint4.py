"""Collect current dependency/limited secret-scan evidence; fail incomplete gates.

The Maven tree must be produced by dependency:tree -DoutputType=json from the
same checkout. OSV only covers published application dependency coordinates,
not Maven plugins, the JDK, operating system or an installed cloud environment.
No runtime environment variables or matching secret values are written.
"""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
PATTERNS = {
    "private_key": re.compile(r"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----"),
    "github_token": re.compile(r"\b(?:gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{30,})\b"),
    "aws_access_key_id": re.compile(r"\b(?:AKIA|ASIA)[A-Z0-9]{16}\b"),
    "provider_token": re.compile(r"\bsk-(?:proj-)?[A-Za-z0-9_-]{32,}\b"),
}
SUFFIXES = {".java", ".properties", ".js", ".jsx", ".json", ".sql", ".py", ".ps1", ".yml", ".yaml", ".xml", ".conf"}
OSV_API = "https://api.osv.dev/v1/"


def write_json(path: Path, value: object) -> None:
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def api_json(endpoint: str, payload: dict | None = None) -> dict:
    request = urllib.request.Request(
        OSV_API + endpoint,
        data=json.dumps(payload).encode() if payload is not None else None,
        headers={"Content-Type": "application/json", "User-Agent": "sasms-sprint4-security/1"},
    )
    with urllib.request.urlopen(request, timeout=45) as response:
        return json.load(response)


def source_scan() -> dict:
    sources = set()
    for directory in ("backend/src", "frontend/src", "database", "scripts", ".github/workflows", "infra"):
        sources.update(p for p in (ROOT / directory).rglob("*")
                       if p.is_file() and p.suffix in SUFFIXES
                       and not {"evidence", ".local", "__pycache__"}.intersection(p.parts))
    sources.update(ROOT / p for p in ("backend/pom.xml", "frontend/package.json", "frontend/package-lock.json"))
    hashes, matches = {}, []
    for path in sorted(sources):
        raw = path.read_bytes()
        relative = path.relative_to(ROOT).as_posix()
        hashes[relative] = hashlib.sha256(raw.replace(b"\r\n", b"\n")).hexdigest()
        for number, line in enumerate(raw.decode("utf-8-sig").splitlines(), 1):
            for rule, pattern in PATTERNS.items():
                if pattern.search(line):
                    matches.append({"rule": rule, "path": relative, "line": number, "value": "[REDACTED]"})
    return {"status": "PASS" if not matches else "FAIL", "files": len(hashes),
            "patterns": list(PATTERNS), "matches": matches, "source_sha256_lf": hashes,
            "limitations": "Four token/key patterns in current textual source/config only; no entropy, Git history, Office files or runtime secrets scan."}


def audit_frontend(output: Path) -> dict:
    npm = shutil.which("npm.cmd" if os.name == "nt" else "npm")
    if not npm:
        return {"status": "ERROR", "error": "npm executable unavailable"}
    command = [npm, "audit", "--json", "--cache", str(ROOT / "database/.local/npm-cache")]
    try:
        result = subprocess.run(command, cwd=ROOT / "frontend", capture_output=True,
                                text=True, encoding="utf-8", timeout=120)
        (output / "npm-audit-stderr.log").write_text(result.stderr, encoding="utf-8")
        raw = json.loads(result.stdout)
        write_json(output / "npm-audit.json", raw)
        counts = raw.get("metadata", {}).get("vulnerabilities")
        if counts is None or "error" in raw:
            return {"status": "ERROR", "exit_code": result.returncode, "error": "npm audit did not return vulnerability metadata"}
        return {"status": "PASS" if not counts.get("critical", 0) and not counts.get("high", 0) else "FAIL",
                "exit_code": result.returncode, "counts": counts,
                "scope": "Complete package-lock graph, including development dependencies"}
    except (OSError, subprocess.TimeoutExpired, json.JSONDecodeError) as error:
        return {"status": "ERROR", "error": type(error).__name__}


def maven_coordinates(tree: dict) -> list[dict]:
    coordinates = {}
    def visit(node: dict, root: bool = False) -> None:
        if not root and node.get("groupId") and node.get("artifactId") and node.get("version"):
            name = node["groupId"] + ":" + node["artifactId"]
            coordinates[(name, node["version"])] = {"name": name, "version": node["version"], "scope": node.get("scope", "compile")}
        for child in node.get("children", []):
            visit(child)
    visit(tree, True)
    return sorted(coordinates.values(), key=lambda p: (p["name"], p["version"]))


def cvss_v3_score(vector: str) -> float | None:
    """Compute CVSS3 base score when OSV does not provide a severity label."""
    import math
    if not vector.startswith(("CVSS:3.0/", "CVSS:3.1/")):
        return None
    metrics = dict(part.split(":", 1) for part in vector.split("/")[1:])
    try:
        changed = metrics["S"] == "C"
        av = {"N": .85, "A": .62, "L": .55, "P": .2}[metrics["AV"]]
        ac = {"L": .77, "H": .44}[metrics["AC"]]
        pr = {"N": .85, "L": .68 if changed else .62, "H": .5 if changed else .27}[metrics["PR"]]
        ui = {"N": .85, "R": .62}[metrics["UI"]]
        c, i, a = ({"N": 0, "L": .22, "H": .56}[metrics[k]] for k in ("C", "I", "A"))
        iss = 1 - (1 - c) * (1 - i) * (1 - a)
        impact = (7.52 * (iss - .029) - 3.25 * (iss - .02) ** 15) if changed else 6.42 * iss
        if impact <= 0:
            return 0
        base = min((1.08 if changed else 1) * (impact + 8.22 * av * ac * pr * ui), 10)
        return math.ceil(round(base, 10) * 10) / 10
    except (KeyError, ValueError):
        return None


def severity(vulnerability: dict) -> str:
    label = str(vulnerability.get("database_specific", {}).get("severity", "")).upper()
    if label in {"LOW", "MODERATE", "MEDIUM", "HIGH", "CRITICAL"}:
        return "MODERATE" if label == "MEDIUM" else label
    scores = [cvss_v3_score(item.get("score", "")) for item in vulnerability.get("severity", [])]
    scores = [score for score in scores if score is not None]
    if not scores:
        return "UNKNOWN"
    score = max(scores)
    return "CRITICAL" if score >= 9 else "HIGH" if score >= 7 else "MODERATE" if score >= 4 else "LOW"


def audit_backend(tree_path: Path, output: Path) -> dict:
    try:
        tree_bytes = tree_path.read_bytes()
        packages = maven_coordinates(json.loads(tree_bytes.decode("utf-8-sig")))
        if not packages:
            return {"status": "ERROR", "error": "Resolved Maven tree contains no dependencies"}
        query = {"queries": [{"package": {"ecosystem": "Maven", "name": p["name"]}, "version": p["version"]} for p in packages]}
        batch = api_json("querybatch", query)
        if len(batch.get("results", [])) != len(packages):
            raise ValueError("OSV result count mismatch")
        ids = sorted({v["id"] for item in batch["results"] for v in item.get("vulns", [])})
        records = {identifier: api_json("vulns/" + identifier) for identifier in ids}
        findings = []
        for package, item in zip(packages, batch["results"]):
            for match in item.get("vulns", []):
                record = records[match["id"]]
                if record.get("withdrawn"):
                    continue
                findings.append({"package": package, "id": match["id"], "aliases": record.get("aliases", []),
                                 "severity": severity(record), "summary": record.get("summary"),
                                 "references": record.get("references", []), "modified": record.get("modified")})
        # Store attribution, versions and affected ranges without copying long
        # third-party advisory descriptions into the project handoff.
        for record in records.values():
            record.pop("details", None)
        write_json(output / "backend-osv.json", {"packages": packages, "batch_response": batch, "advisories": records, "findings": findings})
        counts = {level: sum(f["severity"] == level for f in findings) for level in ("LOW", "MODERATE", "HIGH", "CRITICAL", "UNKNOWN")}
        return {"status": "FAIL" if any(counts[k] for k in ("HIGH", "CRITICAL", "UNKNOWN")) else "PASS",
                "packages": len(packages), "counts": counts, "tree_sha256": hashlib.sha256(tree_bytes).hexdigest(),
                "scope": "Resolved Maven application dependencies, including test scope; plugins/JDK/OS/container/cloud excluded"}
    except (OSError, ValueError, urllib.error.URLError) as error:
        return {"status": "ERROR", "error": type(error).__name__, "detail": "Could not complete Maven dependency/OSV query; do not treat as a clean audit"}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=ROOT / "Sprint 4/DB/evidence/security")
    options = parser.add_mutually_exclusive_group()
    options.add_argument("--maven-tree", type=Path)
    options.add_argument("--frontend-only", action="store_true", help="Subset gate; backend remains explicitly NOT_RUN")
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    frontend = audit_frontend(args.output)
    backend = audit_backend(args.maven_tree, args.output) if args.maven_tree else {"status": "NOT_RUN", "reason": "No resolved Maven dependency tree supplied"}
    scan = source_scan()
    write_json(args.output / "limited-secret-scan.json", scan)
    required = [frontend["status"], scan["status"]]
    if not args.frontend_only:
        required.append(backend["status"])
    result = {"collected_at_utc": datetime.now(timezone.utc).isoformat(),
              "scope": "FRONTEND_SUBSET" if args.frontend_only else "APPLICATION_DEPENDENCIES_AND_LIMITED_SOURCE_SCAN",
              "gate": "PASS" if all(status == "PASS" for status in required) else "FAIL_OR_INCOMPLETE",
              "frontend": frontend, "backend": backend,
              "limited_secret_scan": {k: v for k, v in scan.items() if k != "source_sha256_lf"},
              "limitations": "Not DAST, penetration testing, browser E2E, cloud TLS/IdP/rate-limit validation or approval for Production."}
    write_json(args.output / "summary.json", result)
    print(json.dumps(result, ensure_ascii=False))
    return int(result["gate"] != "PASS")


if __name__ == "__main__":
    raise SystemExit(main())
