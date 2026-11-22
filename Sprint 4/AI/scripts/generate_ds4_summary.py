# -*- coding: utf-8 -*-
"""Build the Sprint 4 S4-03 (DS4) summary from Maven Surefire XML and the PostgreSQL reconciliation evidence.

Usage (repo root), after `backend/mvnw.cmd test` (SASMS_AI_EVIDENCE_DIR[_S3] set so evidence tests run) and
`python scripts/validate_ai_sprint34.py ...` against an isolated sasms_s4_* database:
    python "Sprint 4/AI/scripts/generate_ds4_summary.py"
"""
import glob
import json
import os
import xml.etree.ElementTree as ET
from datetime import datetime, timezone

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
REPORTS = os.path.join(ROOT, "backend", "target", "surefire-reports")
EVIDENCE = os.path.join(ROOT, "Sprint 4", "AI", "evidence")

S4_CLASSES = (
    "ai.service.AiSingleFlightTest",
    "ai.service.AiRecommendationRecordingTest",
    "ai.data.JdbcAiRecommendationRecorderTest",
    "reporting.service.StoreDashboardServiceTest",
    "reporting.data.JdbcStoreDashboardDataAccessTest",
    "reporting.controller.StoreDashboardControllerTest",
    "ai.service.AiCrossSellServiceResilienceTest",
    "ai.AiCrossSellControllerTest",
    "ai.AiSprint2EvidenceTest",
)


def read_suite(path):
    root = ET.parse(path).getroot()
    counts = {"PASS": 0, "FAIL": 0, "SKIPPED": 0}
    for case in root.iter("testcase"):
        if case.find("failure") is not None or case.find("error") is not None:
            counts["FAIL"] += 1
        elif case.find("skipped") is not None:
            counts["SKIPPED"] += 1
        else:
            counts["PASS"] += 1
    return root.get("name"), counts


def main():
    total = {"PASS": 0, "FAIL": 0, "SKIPPED": 0}
    s4 = {}
    for path in sorted(glob.glob(os.path.join(REPORTS, "TEST-*.xml"))):
        name, counts = read_suite(path)
        for key, value in counts.items():
            total[key] += value
        short = name.replace("com.smartelectronicsupermarket.", "")
        if short in S4_CLASSES:
            s4[short] = counts
    with open(os.path.join(EVIDENCE, "ds4-ai-dashboard-reconciliation.json"), encoding="utf-8") as fh:
        recon = json.load(fh)
    with open(os.path.join(EVIDENCE, "ds4-ai-crosssell-latency-benchmark.json"), encoding="utf-8") as fh:
        bench = json.load(fh)
    summary = {
        "task": "S4-03 TSK-04-02 BI Analytics Dashboard (revenue, stock, AI effectiveness)",
        "deliverable": "DS4 Phan mem tang truong Sprint 4 (AI / BI)",
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "full_backend_suite": total,
        "s4_suites": s4,
        "postgresql_reconciliation": {
            "database": recon["database"],
            "passed": recon["passed"],
            "total": recon["total"],
            "failed_checks": [c["check"] for c in recon["checks"] if c["result"] != "PASS"],
        },
        "ai_crosssell_regression_benchmark": {
            "requests": bench["requests"],
            "concurrency": bench["concurrency"],
            "latency_ms": bench["latency_ms"],
            "source_breakdown": bench["source_breakdown"],
            "llm_calls_executed": bench["llm_calls_executed"],
            "distinct_cache_keys": bench["distinct_cache_keys"],
        },
        "not_verified": [
            "PostgreSQL 16 (reconciliation ran on PostgreSQL 18.7; the project CI uses 16)",
            "Dashboard screenshot / browser rendering against a live backend",
            "Real OpenAI/Gemini provider calls",
            "Production deployment and PO sign-off",
        ],
    }
    out = os.path.join(EVIDENCE, "ds4-ai-engine-summary.json")
    with open(out, "w", encoding="utf-8") as fh:
        json.dump(summary, fh, ensure_ascii=False, indent=2)
        fh.write("\n")
    print("wrote", out)
    print(json.dumps({k: summary[k] for k in ("full_backend_suite", "postgresql_reconciliation")},
                     ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
