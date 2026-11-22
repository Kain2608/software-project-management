# -*- coding: utf-8 -*-
"""Build the Sprint 3 S3-03 (DS3) test summary from real Maven Surefire XML reports.

Usage (repo root, after `backend/mvnw.cmd test` with SASMS_AI_EVIDENCE_DIR_S3 set):
    python "Sprint 3/AI/scripts/generate_ds3_summary.py"

Nothing is hard-coded: counts come from backend/target/surefire-reports/*.xml; latency, scenario and sample
numbers come from the evidence files written by AiSprint3EvidenceTest.
"""
import glob
import json
import os
import xml.etree.ElementTree as ET
from datetime import datetime, timezone

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
REPORTS = os.path.join(ROOT, "backend", "target", "surefire-reports")
EVIDENCE = os.path.join(ROOT, "Sprint 3", "AI", "evidence")

S3_CLASSES = (
    "ai.summary.SpecSummaryValidatorTest",
    "ai.summary.AiSpecSummaryServiceTest",
    "ai.AiSpecSummaryControllerTest",
    "ai.AiSprint3EvidenceTest",
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


def load(name):
    with open(os.path.join(EVIDENCE, name), encoding="utf-8") as fh:
        return json.load(fh)


def main():
    total = {"PASS": 0, "FAIL": 0, "SKIPPED": 0}
    s3 = {}
    for path in sorted(glob.glob(os.path.join(REPORTS, "TEST-*.xml"))):
        name, cases = read_suite(path)
        for case in cases:
            total[case["status"]] += 1
        short = name.replace("com.smartelectronicsupermarket.", "")
        if short in S3_CLASSES:
            s3[short] = {
                "tests": len(cases),
                "passed": sum(c["status"] == "PASS" for c in cases),
                "failed": sum(c["status"] == "FAIL" for c in cases),
                "skipped": sum(c["status"] == "SKIPPED" for c in cases),
                "cases": cases,
            }
    bench = load("ds3-ai-summary-latency-benchmark.json")
    scenarios = load("ds3-ai-summary-scenarios.json")
    samples = load("ds3-ai-summary-samples.json")
    summary = {
        "task": "S3-03 TSK-03-01 Prompt AI tom tat thong so san pham (RAG chong bia)",
        "deliverable": "DS3 Phan mem tang truong Sprint 3 (AI)",
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "full_backend_suite": total,
        "s3_suites": s3,
        "scenarios": {"passed": scenarios["passed"], "total": scenarios["total"]},
        "category_samples": {"grounded_ai_samples": samples["grounded_ai_samples"], "total": samples["total"]},
        "benchmark": {
            "requests": bench["requests"],
            "concurrency": bench["concurrency"],
            "latency_ms": bench["latency_ms"],
            "p95_under_3000_ms": bench["sla_p95_under_3000_ms"],
            "source_breakdown": bench["source_breakdown"],
            "llm_calls_executed": bench["llm_calls_executed"],
            "distinct_cache_keys": bench["distinct_cache_keys"],
        },
        "not_verified": [
            "Real OpenAI/Gemini provider (the model is a deterministic stand-in behind SpecSummaryLlm)",
            "Hallucination rate of a real model (validator is exercised with scripted fabrications only)",
            "Redis L2 cache (in-memory cache only)",
            "Frontend POS spec-summary panel (no UI in this task)",
        ],
    }
    out = os.path.join(EVIDENCE, "ds3-ai-engine-summary.json")
    with open(out, "w", encoding="utf-8") as fh:
        json.dump(summary, fh, ensure_ascii=False, indent=2)
        fh.write("\n")
    print("wrote", out)
    print(json.dumps({k: summary[k] for k in ("full_backend_suite", "scenarios", "category_samples", "benchmark")},
                     ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
