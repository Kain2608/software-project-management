# -*- coding: utf-8 -*-
"""Build Sprint 2 S2-08 (DS2) test summary from real Maven Surefire XML reports.

Usage (from repo root, after `backend/mvnw.cmd test` with SASMS_AI_EVIDENCE_DIR set):
    python "Sprint 2/AI/scripts/generate_ds2_summary.py"

Nothing is hard-coded: counts come from backend/target/surefire-reports/*.xml and
latency numbers from Sprint 2/AI/evidence/ds2-ai-latency-benchmark.json.
"""
import glob
import json
import os
import xml.etree.ElementTree as ET
from datetime import datetime, timezone

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
REPORTS = os.path.join(ROOT, "backend", "target", "surefire-reports")
EVIDENCE = os.path.join(ROOT, "Sprint 2", "AI", "evidence")

AI_CLASSES = (
    "ai.AiCrossSellControllerTest",
    "ai.AiSprint2EvidenceTest",
    "ai.data.JdbcAiCrossSellDataAccessTest",
    "ai.service.AiCircuitBreakerTest",
    "ai.service.AiCrossSellServiceResilienceTest",
    "security.config.SecurityFailClosedIntegrationTest",
)


def read_suite(path):
    root = ET.parse(path).getroot()
    cases = []
    for case in root.iter("testcase"):
        status = "PASS"
        if case.find("failure") is not None or case.find("error") is not None:
            status = "FAIL"
        elif case.find("skipped") is not None:
            status = "SKIPPED"
        cases.append({"name": case.get("name"), "time_s": float(case.get("time", 0)), "status": status})
    return root.get("name"), cases


def main():
    suites = {}
    total = {"PASS": 0, "FAIL": 0, "SKIPPED": 0}
    for path in sorted(glob.glob(os.path.join(REPORTS, "TEST-*.xml"))):
        name, cases = read_suite(path)
        for case in cases:
            total[case["status"]] += 1
        suites[name] = cases

    ai_suites = {}
    for name, cases in suites.items():
        short = name.replace("com.smartelectronicsupermarket.", "")
        if short in AI_CLASSES:
            ai_suites[short] = {
                "tests": len(cases),
                "passed": sum(c["status"] == "PASS" for c in cases),
                "failed": sum(c["status"] == "FAIL" for c in cases),
                "cases": cases,
            }

    with open(os.path.join(EVIDENCE, "ds2-ai-latency-benchmark.json"), encoding="utf-8") as fh:
        bench = json.load(fh)
    with open(os.path.join(EVIDENCE, "ds2-ai-demo-scenarios.json"), encoding="utf-8") as fh:
        demo = json.load(fh)

    summary = {
        "task": "S2-08 TSK-02-07 AI Recommendation Engine",
        "deliverable": "DS2 Phan mem tang truong Sprint 2 (AI)",
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "full_backend_suite": total,
        "ai_related_suites": ai_suites,
        "demo_scenarios": {"passed": demo["passed"], "total": demo["total"]},
        "benchmark": {
            "requests": bench["requests"],
            "concurrency": bench["concurrency"],
            "latency_ms": bench["latency_ms"],
            "p95_under_3000_ms": bench["sla_p95_under_3000_ms"],
            "source_breakdown": bench["source_breakdown"],
        },
        "not_verified": [
            "PostgreSQL 16 (SQL of JdbcAiCrossSellDataAccess only checked structurally; Docker daemon was not running)",
            "Real OpenAI/Gemini provider calls (adapters are deterministic mocks)",
            "Redis L2 cache (in-memory cache only)",
            "Frontend POS end-to-end with live JWT",
        ],
    }
    out = os.path.join(EVIDENCE, "ds2-ai-engine-summary.json")
    with open(out, "w", encoding="utf-8") as fh:
        json.dump(summary, fh, ensure_ascii=False, indent=2)
        fh.write("\n")
    print("wrote", out)
    print(json.dumps({k: summary[k] for k in ("full_backend_suite", "demo_scenarios", "benchmark")},
                     ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
