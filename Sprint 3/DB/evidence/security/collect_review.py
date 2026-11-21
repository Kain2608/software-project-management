"""Collect reproducible source review evidence without recording secret values.

Run from the repository root after Maven tests. No network calls or DB mutations.
The token-pattern scan is intentionally limited; it is not a secret-scanner audit.
"""
from __future__ import annotations

from datetime import datetime, timezone
import fnmatch
import hashlib
import json
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[4]
OUTPUT = Path(__file__).resolve().parent
SUFFIXES = {'.java', '.properties', '.js', '.jsx', '.json', '.sql', '.py', '.ps1', '.yml', '.yaml'}
PATTERNS = {
    'private-key-header': re.compile(r'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----'),
    'github-token': re.compile(r'\b(?:gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{30,})\b'),
    'aws-access-key-id': re.compile(r'\b(?:AKIA|ASIA)[A-Z0-9]{16}\b'),
    'provider-token': re.compile(r'\bsk-(?:proj-)?[A-Za-z0-9_-]{32,}\b'),
}
SELECTED_TESTS = ('*Security*Test', '*Identity*Test', '*PermissionCheckerTest', 'SasmsJwtDecoderFactoryTest',
                  'ApiExceptionHandlerTest', 'JdbcQrDeviceLookupDataAccessTest',
                  'JdbcReceiptDataAccessTest', 'JdbcInventoryStockDataAccessTest', 'Sms*Test')


def main() -> int:
    sources = []
    for directory in ('backend/src', 'frontend/src', 'database', 'scripts', '.github/workflows'):
        sources.extend(p for p in (ROOT / directory).rglob('*')
                       if p.is_file() and p.suffix in SUFFIXES and not {'evidence', '.local', '__pycache__'}.intersection(p.parts))
    sources.extend(ROOT / p for p in ('backend/pom.xml', 'frontend/package.json', 'frontend/package-lock.json'))
    sources = sorted({p for p in sources if p.exists()})
    hashes, lf_hashes, matches = {}, {}, []
    for path in sources:
        relative = path.relative_to(ROOT).as_posix()
        raw = path.read_bytes()
        hashes[relative] = hashlib.sha256(raw).hexdigest()
        lf_hashes[relative] = hashlib.sha256(raw.replace(b'\r\n', b'\n')).hexdigest()
        for number, line in enumerate(raw.decode('utf-8-sig').splitlines(), 1):
            for name, pattern in PATTERNS.items():
                if pattern.search(line):
                    matches.append({'rule': name, 'path': relative, 'line': number, 'value': '[REDACTED]'})

    auth = subprocess.run(['node', '--test', 'src/app/authContext.test.js'], cwd=ROOT / 'frontend',
                          capture_output=True, text=True, encoding='utf-8', timeout=60)
    (OUTPUT / 'frontend-auth.log').write_text(auth.stdout + auth.stderr, encoding='utf-8')

    selected = []
    all_suites = []
    for report in sorted((ROOT / 'backend/target/surefire-reports').glob('TEST-*.xml')):
        suite = ET.parse(report).getroot()
        name = suite.get('name', '')
        all_suites.append({'suite': name, **{key: int(suite.get(key, '0'))
                                            for key in ('tests', 'failures', 'errors', 'skipped')},
                           'skipped_cases': [
                               {'name': case.get('name'), 'reason': skipped.get('message')
                                or (skipped.text or '').strip().split('\n')[0]}
                               for case in suite.findall('testcase')
                               if (skipped := case.find('skipped')) is not None]})
        if any(fnmatch.fnmatchcase(name.rsplit('.', 1)[-1], pattern) for pattern in SELECTED_TESTS):
            selected.append({'suite': name, **{key: int(suite.get(key, '0'))
                                              for key in ('tests', 'failures', 'errors', 'skipped')},
                             'time_seconds': suite.get('time'),
                             'cases': [case.get('name') for case in suite.findall('testcase')]})
    totals = {key: sum(s[key] for s in selected) for key in ('tests', 'failures', 'errors', 'skipped')}
    full_receipt = OUTPUT / 'full-backend-summary.json'
    if full_receipt.exists():
        full = json.loads(full_receipt.read_text(encoding='utf-8'))
        full['totals'] = {key: sum(s[key] for s in all_suites) for key in ('tests', 'failures', 'errors', 'skipped')}
        full['passed'] = full['totals']['tests'] - sum(full['totals'][k] for k in ('failures', 'errors', 'skipped'))
        full['suites'] = all_suites
        full['log_sha256'] = hashlib.sha256((OUTPUT / 'full-backend-tests.log').read_bytes()).hexdigest()
        full_receipt.write_text(json.dumps(full, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    (OUTPUT / 'java-test-summary.log').write_text('\n'.join(
        f"{s['suite']}: tests={s['tests']}, failures={s['failures']}, errors={s['errors']}, skipped={s['skipped']}"
        for s in selected) + '\n', encoding='utf-8')
    evidence = {
        'collected_at_utc': datetime.now(timezone.utc).isoformat(),
        'review_date_local': '2026-11-21',
        'scope': 'Source review; local selected Java tests and frontend auth tests. No Staging, live provider, DAST or CVE audit.',
        'java_validation': {
            'scope': 'Security and SMS suites selected from the full backend run, including the opt-in PostgreSQL MOCK worker integration. Full suite result is in full-backend-summary.json.',
            'runtime': 'Zulu OpenJDK 24.0.1', 'maven': '3.9.15',
            'override': '-Djava.version=24', 'project_java_version': 25,
            'limitation': 'Local JDK25 unavailable; rerun unchanged pom with JDK25 in CI before release.',
            'command': 'mvn -o -Dmaven.repo.local=<local-cache> -Djava.version=24 test',
            'sprint3_postgresql_integration_enabled': full.get('sprint3_postgresql_integration_enabled', False) if full_receipt.exists() else False,
            'totals': totals, 'suites': selected,
        },
        'frontend_auth': {'command': 'node --test src/app/authContext.test.js', 'exit_code': auth.returncode},
        'limited_secret_pattern_scan': {
            'patterns': list(PATTERNS), 'scanned_files': len(sources), 'matches': matches,
            'limitations': 'Only current textual source/configuration; no Git history, binaries, Office documents, runtime environment or entropy analysis. No match does not prove absence of secrets.',
        },
        'source_sha256': hashes,
        'source_sha256_lf': lf_hashes,
        'hash_line_endings': 'source_sha256 hashes local bytes; source_sha256_lf normalizes CRLF to LF for comparison with Git text blobs.',
    }
    (OUTPUT / 'summary.json').write_text(json.dumps(evidence, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({'java_tests': totals, 'frontend_auth_exit_code': auth.returncode,
                      'scanned_files': len(sources), 'secret_pattern_matches': len(matches)}))
    return int(auth.returncode != 0 or not selected or totals['failures'] != 0 or totals['errors'] != 0 or bool(matches))


if __name__ == '__main__':
    raise SystemExit(main())
