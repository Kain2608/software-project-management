#!/usr/bin/env python3
"""Sprint 4 release helpers. Local packaging by default; AWS needs --execute.

No database migration, database drop, traffic promotion or secret output occurs
here. App Runner updates preserve the existing runtime configuration and record
only image identifiers needed for application rollback.
"""
from __future__ import annotations

import argparse
from copy import deepcopy
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import time
from urllib.error import HTTPError
from urllib.parse import urlsplit
from urllib.request import HTTPRedirectHandler, Request, build_opener


def write_json(path: Path, value: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')


def https_origin(value: str) -> str:
    parsed = urlsplit(value)
    if (parsed.scheme != 'https' or not parsed.hostname or parsed.username or parsed.password
            or parsed.query or parsed.fragment or parsed.path not in ('', '/')
            or parsed.port not in (None, 443)):
        raise ValueError('Use an HTTPS origin without credentials, path, query or custom port')
    if not re.fullmatch(r'[A-Za-z0-9.-]+', parsed.hostname) or '.' not in parsed.hostname:
        raise ValueError('Use a fully qualified HTTPS hostname')
    return 'https://' + parsed.hostname.lower()


def prepare_vercel(args) -> dict:
    origin = https_origin(args.backend_origin)
    if bool(args.org_id) != bool(args.project_id):
        raise ValueError('Vercel org ID and project ID must be supplied together')
    source, output = args.dist.resolve(), args.output.resolve()
    if not (source / 'index.html').is_file():
        raise ValueError('Built frontend dist/index.html is required')
    if output.exists():
        raise ValueError('Deployment staging directory already exists; use a new path')
    if source == output or source in output.parents or output in source.parents:
        raise ValueError('Source and deployment staging directories must not overlap')
    files = list(source.rglob('*'))
    if any(path.is_symlink() for path in files):
        raise ValueError('Frontend artifact must not contain symbolic links')
    project = output / '.vercel'
    static = project / 'output/static'
    shutil.copytree(source, static)
    config = {'version': 3, 'routes': [
        {'src': '/api(?:/(.*))?', 'dest': origin + '/api/$1',
         'headers': {'Cache-Control': 'private, no-store'}},
        {'src': '/assets/(.*)', 'headers': {'Cache-Control': 'public, max-age=31536000, immutable'}, 'continue': True},
        {'src': '/(.*)', 'headers': {'X-Content-Type-Options': 'nosniff',
                                   'Referrer-Policy': 'strict-origin-when-cross-origin',
                                   'X-Frame-Options': 'DENY'}, 'continue': True},
        {'handle': 'filesystem'},
        {'src': '/.*', 'dest': '/index.html', 'headers': {'Cache-Control': 'no-cache'}}]}
    write_json(project / 'output/config.json', config)
    if args.org_id:
        write_json(project / 'project.json', {'orgId': args.org_id, 'projectId': args.project_id})
    manifest = {'action': 'prepare-vercel', 'backend_origin': origin,
                'source_commit': args.commit, 'created_at_utc': datetime.now(timezone.utc).isoformat(),
                'files': {str(path.relative_to(source)).replace('\\', '/'):
                          hashlib.sha256(path.read_bytes()).hexdigest()
                          for path in files if path.is_file()},
                'deployment_status': 'NOT_DEPLOYED'}
    write_json(output / 'artifact-manifest.json', manifest)
    return manifest


def aws_json(arguments: list[str]) -> dict:
    result = subprocess.run(['aws', *arguments, '--output', 'json', '--no-cli-pager'],
                            capture_output=True, text=True, encoding='utf-8', timeout=60)
    if result.returncode:
        # Provider errors can echo source configuration. Retain no runtime
        # environment values or credential-bearing command output in evidence.
        raise RuntimeError('AWS command failed; inspect the restricted provider operation log')
    return json.loads(result.stdout)


def source_for_image(service: dict, image: str) -> dict:
    source = deepcopy(service['SourceConfiguration'])
    if source.get('ImageRepository', {}).get('ImageRepositoryType') != 'ECR':
        raise ValueError('An existing private ECR App Runner service is required')
    old_image = source['ImageRepository']['ImageIdentifier']
    repository = re.sub(r'(?::[^/:]+|@sha256:[a-f0-9]{64})$', '', old_image)
    if not image.startswith(repository + ':') and not image.startswith(repository + '@sha256:'):
        raise ValueError('Release and rollback images must stay in the existing ECR repository')
    source['ImageRepository']['ImageIdentifier'] = image
    source['AutoDeploymentsEnabled'] = False
    return source


def update_apprunner(args) -> dict:
    if not re.fullmatch(r'arn:aws(?:-[a-z]+)?:apprunner:[a-z0-9-]+:\d{12}:service/[A-Za-z0-9_-]+/[a-f0-9]+', args.service_arn):
        raise ValueError('A concrete App Runner service ARN is required')
    if not re.fullmatch(r'\d{12}\.dkr\.ecr\.[a-z0-9-]+\.amazonaws\.com(?:\.cn)?/[a-z0-9._/-]+(?::[a-f0-9]{40}|@sha256:[a-f0-9]{64})', args.image):
        raise ValueError('Use a private ECR image pinned to a full Git commit tag or image digest')
    report = {'action': 'apprunner-update', 'service_arn': args.service_arn, 'requested_image': args.image,
              'status': 'PLAN_ONLY', 'created_at_utc': datetime.now(timezone.utc).isoformat(),
              'preserve_existing_runtime_configuration': True, 'database_migrations': 'NOT_EXECUTED'}
    if not args.execute:
        return report
    args.evidence_dir.mkdir(parents=True, exist_ok=True)
    if (args.evidence_dir / 'apprunner-deployment.json').exists():
        raise ValueError('Deployment evidence already exists; use a new evidence directory')
    try:
        service = aws_json(['apprunner', 'describe-service', '--service-arn', args.service_arn])['Service']
        if service['Status'] != 'RUNNING':
            raise ValueError('Existing App Runner service must be RUNNING before deployment')
        report['previous_image'] = service['SourceConfiguration']['ImageRepository']['ImageIdentifier']
        source = source_for_image(service, args.image)
        # Only this short-lived private file includes existing runtime values.
        with tempfile.TemporaryDirectory(prefix='sasms-apprunner-') as temporary:
            payload = Path(temporary) / 'source.json'
            descriptor = os.open(payload, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
            with os.fdopen(descriptor, 'w', encoding='utf-8') as stream:
                json.dump(source, stream)
            operation = aws_json(['apprunner', 'update-service', '--service-arn', args.service_arn,
                                  '--source-configuration', payload.resolve().as_uri()])
        operation_id = operation['OperationId']
        report['operation_id'] = operation_id
        report['status'] = 'DEPLOYING'
        write_json(args.evidence_dir / 'apprunner-deployment.json', report)
        deadline = time.monotonic() + args.timeout_seconds
        while time.monotonic() < deadline:
            operations = aws_json(['apprunner', 'list-operations', '--service-arn', args.service_arn])
            matching = [item for item in operations['OperationSummaryList'] if item['Id'] == operation_id]
            if matching:
                state = matching[0]['Status']
                report['operation_status'] = state
                if state == 'SUCCEEDED':
                    break
                if state in ('FAILED', 'ROLLBACK_FAILED', 'ROLLBACK_SUCCEEDED'):
                    raise RuntimeError('App Runner deployment did not succeed: ' + state)
            time.sleep(min(15, max(0, deadline - time.monotonic())))
        else:
            raise RuntimeError('App Runner operation timed out; inspect operation ID before any retry')
        current = aws_json(['apprunner', 'describe-service', '--service-arn', args.service_arn])['Service']
        if current['Status'] != 'RUNNING' or current['SourceConfiguration']['ImageRepository']['ImageIdentifier'] != args.image:
            raise RuntimeError('App Runner image/status differs after successful operation')
        report['backend_origin'] = https_origin('https://' + current['ServiceUrl'])
        report['status'] = 'DEPLOYED_AWAITING_SMOKE'
    except Exception:
        report['status'] = 'FAILED_REQUIRES_OPERATOR_REVIEW'
        raise
    finally:
        report['finished_at_utc'] = datetime.now(timezone.utc).isoformat()
        write_json(args.evidence_dir / 'apprunner-deployment.json', report)
    return report


def smoke(args) -> dict:
    origin = https_origin(args.base_url)
    headers = {}
    bypass = os.environ.get('VERCEL_AUTOMATION_BYPASS_SECRET')
    if bypass:
        if not urlsplit(origin).hostname.endswith('.vercel.app'):
            raise ValueError('Vercel bypass credential may only be sent to a vercel.app deployment')
        headers['x-vercel-protection-bypass'] = bypass
    report = {'action': 'http-smoke', 'origin': origin, 'checks': {}, 'scope':
              'Liveness and unauthenticated access denial only; does not prove DB readiness, JWT/role E2E or UAT'}
    class NoRedirects(HTTPRedirectHandler):
        def redirect_request(self, request, fp, code, message, headers, newurl):
            return None

    opener = build_opener(NoRedirects())
    for path, expected in [('/api/health', 200), ('/api/v1/devices/00000000-0000-4000-8000-000000000000/history', 401)]:
        request = Request(origin + path, headers=headers)
        try:
            with opener.open(request, timeout=30) as response:
                status, body = response.status, response.read(65536)
        except HTTPError as error:
            status, body = error.code, error.read(65536)
        if status != expected:
            raise RuntimeError(f'Smoke {path}: expected HTTP {expected}, got {status}')
        if path == '/api/health' and json.loads(body).get('status') != 'UP':
            raise RuntimeError('Health response does not report UP')
        report['checks'][path] = {'status': status, 'passed': True}
    report['status'] = 'PASSED'
    report['finished_at_utc'] = datetime.now(timezone.utc).isoformat()
    write_json(args.evidence, report)
    return report


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest='command', required=True)
    prepare = commands.add_parser('prepare-vercel', help='Package existing frontend bytes as Vercel Build Output v3')
    prepare.add_argument('--dist', type=Path, required=True)
    prepare.add_argument('--output', type=Path, required=True)
    prepare.add_argument('--backend-origin', required=True)
    prepare.add_argument('--commit', required=True)
    prepare.add_argument('--org-id', default=os.environ.get('VERCEL_ORG_ID'))
    prepare.add_argument('--project-id', default=os.environ.get('VERCEL_PROJECT_ID'))
    update = commands.add_parser('apprunner-update', help='Plan an image change, or execute against an existing service')
    update.add_argument('--service-arn', required=True)
    update.add_argument('--image', required=True)
    update.add_argument('--execute', action='store_true')
    update.add_argument('--timeout-seconds', type=int, default=900)
    update.add_argument('--evidence-dir', type=Path, default=Path('release-evidence'))
    check = commands.add_parser('smoke', help='Check public health and private-route authentication boundary')
    check.add_argument('--base-url', required=True)
    check.add_argument('--evidence', type=Path, required=True)
    args = parser.parse_args()
    if getattr(args, 'timeout_seconds', 1) < 1:
        parser.error('Timeout must be positive')
    if args.command == 'prepare-vercel' and not re.fullmatch(r'[a-f0-9]{40}', args.commit):
        parser.error('Source commit must be a full 40-character SHA')
    try:
        result = {'prepare-vercel': prepare_vercel, 'apprunner-update': update_apprunner, 'smoke': smoke}[args.command](args)
        print(json.dumps(result, ensure_ascii=False, indent=2))
        return 0
    except (ValueError, OSError, RuntimeError, KeyError, subprocess.TimeoutExpired) as error:
        # Never print AWS CLI stderr or runtime configuration.
        print(str(error), file=sys.stderr)
        return 1


if __name__ == '__main__':
    raise SystemExit(main())
