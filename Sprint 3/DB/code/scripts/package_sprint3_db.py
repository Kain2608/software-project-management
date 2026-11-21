#!/usr/bin/env python3
"""Generate/check the Sprint 3 DB handoff snapshot from canonical repo sources.

Only the allowlisted source files are copied. Edit their canonical repo paths,
then rerun this script; files under Sprint 3/DB/code are generated snapshots.
--check is read-only. Unexpected package entries are never removed or replaced.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import sys


def repository_root() -> Path:
    # A copy of this script lives in the snapshot too. Locate the actual project
    # rather than treating that nested code directory as a second repository.
    for candidate in Path(__file__).resolve().parents:
        if ((candidate / '.gitattributes').is_file()
                and (candidate / 'backend/pom.xml').is_file()
                and (candidate / 'database/migrations').is_dir()):
            return candidate
    raise ValueError('Cannot locate the canonical project repository')


JAVA = 'backend/src/main/java/com/smartelectronicsupermarket'
JAVA_TEST = 'backend/src/test/java/com/smartelectronicsupermarket'
EXACT_SOURCES = (
    '.github/workflows/sprint3.yml',
    'database/migrations/V006__logistics_and_warranty.sql',
    'database/migrations/V007__sms_outbox.sql',
    'database/sprint3/runtime-roles.sql',
    'database/sprint3/demo.sql',
    'database/sprint3/schema.dbml',
    'database/sprint3/erd.mmd',
    'database/sprint3/data-dictionary.md',
    'database/tests/sprint3/logistics_and_warranty.sql',
    'scripts/validate_sprint3.py',
    'scripts/validate_sprint2.py',
    'scripts/validate_environment.py',
    'scripts/package_sprint3_db.py',
    f'{JAVA}/security/config/SasmsJwtDecoderFactory.java',
    f'{JAVA}/security/config/SasmsJwtProperties.java',
    f'{JAVA_TEST}/security/config/SasmsJwtDecoderFactoryTest.java',
)
# Bounded globs with exact expected names prevent silently including unrelated
# files or silently losing a source when the package changes in the future.
SOURCE_GROUPS = {
    f'{JAVA}/sms/*.java': {
        'HttpSmsAdapter.java', 'SmsAdapter.java', 'SmsConfiguration.java',
        'SmsOutboxWorker.java', 'SmsProperties.java', 'SmsRequest.java',
        'SmsResult.java', 'SmsWorkerConfiguration.java',
    },
    f'{JAVA_TEST}/sms/*.java': {
        'SmsAdapterTest.java', 'SmsOutboxWorkerTest.java',
        'SmsPostgreSqlIntegrationTest.java',
    },
}
PRESERVED_FILES = {'README.md'}


def normalized(raw: bytes) -> bytes:
    raw.decode('utf-8')
    if b'\0' in raw:
        raise ValueError('Only UTF-8 text sources may enter the snapshot')
    return raw.replace(b'\r\n', b'\n').replace(b'\r', b'\n')


def is_link(path: Path) -> bool:
    return path.is_symlink() or getattr(path, 'is_junction', lambda: False)()


def safe_path(root: Path, path: Path) -> None:
    try:
        relative = path.relative_to(root)
        path.resolve().relative_to(root.resolve())
    except ValueError as error:
        raise ValueError(f'Path leaves the repository: {path}') from error
    current = root
    for part in relative.parts:
        current /= part
        if is_link(current):
            raise ValueError(f'Symlinks/junctions are not allowed in package paths: {current}')


def source_files(root: Path) -> dict[str, bytes]:
    paths = set(EXACT_SOURCES)
    for pattern, expected_names in SOURCE_GROUPS.items():
        found = sorted(root.glob(pattern))
        names = {path.name for path in found if path.is_file()}
        if names != expected_names:
            raise ValueError(f'Source allowlist changed for {pattern}: '
                             f'missing={sorted(expected_names-names)}, '
                             f'unexpected={sorted(names-expected_names)}')
        paths.update(path.relative_to(root).as_posix() for path in found)
    result = {}
    for relative in sorted(paths):
        path = root / relative
        safe_path(root, path)
        if not path.is_file():
            raise ValueError(f'Required source file is missing: {relative}')
        result[relative] = normalized(path.read_bytes())
    return result


def unexpected_entries(output: Path, expected_files: set[str]) -> list[str]:
    allowed_files = expected_files | PRESERVED_FILES
    allowed_dirs = {'.'}
    for name in allowed_files:
        allowed_dirs.update(parent.as_posix() for parent in Path(name).parents)
    unexpected = []
    if not output.exists():
        return unexpected
    if not output.is_dir():
        raise ValueError('The package output path must be a directory')
    for directory, dirs, files in os.walk(output, followlinks=False):
        base = Path(directory)
        for name in list(dirs):
            path = base / name
            relative = path.relative_to(output).as_posix()
            if is_link(path) or relative not in allowed_dirs:
                unexpected.append(relative + '/')
                dirs.remove(name)
        for name in files:
            path = base / name
            relative = path.relative_to(output).as_posix()
            if is_link(path) or relative not in allowed_files:
                unexpected.append(relative)
    return sorted(unexpected)


def manifest_bytes(sources: dict[str, bytes]) -> bytes:
    manifest = {
        'format_version': 1,
        'canonical_root': '.',
        'package_root': 'Sprint 3/DB/code',
        'line_endings': 'LF; source and snapshot comparisons normalize CRLF/CR to LF',
        'preserved_metadata': ['code/README.md'],
        'files': [
            {'canonical_path': path, 'package_path': 'code/' + path,
             'sha256': hashlib.sha256(content).hexdigest()}
            for path, content in sources.items()
        ],
    }
    return (json.dumps(manifest, ensure_ascii=False, indent=2) + '\n').encode('utf-8')


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true', help='Read-only comparison; do not create or change files')
    args = parser.parse_args()
    try:
        root = repository_root()
        output = root / 'Sprint 3/DB/code'
        manifest = root / 'Sprint 3/DB/code-manifest.json'
        safe_path(root, output)
        safe_path(root, manifest)
        sources = source_files(root)
        extras = unexpected_entries(output, set(sources))
        if extras:
            raise ValueError('Unexpected package entries; inspect them manually (nothing removed): '
                             + ', '.join(extras))
        if manifest.exists() and not manifest.is_file():
            raise ValueError('The manifest path must be a regular file')
        expected_manifest = manifest_bytes(sources)
        if args.check:
            issues = []
            for relative, content in sources.items():
                path = output / relative
                if not path.is_file():
                    issues.append('missing: ' + relative)
                elif normalized(path.read_bytes()) != content:
                    issues.append('source/snapshot mismatch: ' + relative)
            if not manifest.is_file():
                issues.append('missing: code-manifest.json')
            elif normalized(manifest.read_bytes()) != expected_manifest:
                issues.append('manifest file list, paths or SHA-256 values differ')
            if issues:
                raise ValueError('; '.join(issues))
            print(f'Sprint 3 DB snapshot verified: {len(sources)} files, LF-normalized SHA-256 and manifest match.')
            return 0
        output.mkdir(parents=True, exist_ok=True)
        for relative, content in sources.items():
            path = output / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            if not path.is_file() or path.read_bytes() != content:
                path.write_bytes(content)
        manifest.parent.mkdir(parents=True, exist_ok=True)
        if not manifest.is_file() or manifest.read_bytes() != expected_manifest:
            manifest.write_bytes(expected_manifest)
        print(f'Sprint 3 DB snapshot generated: {len(sources)} files; preserved code/README.md and canonical runtime sources.')
        return 0
    except (OSError, UnicodeError, ValueError) as error:
        print('Sprint 3 DB packaging failed: ' + str(error), file=sys.stderr)
        return 1


if __name__ == '__main__':
    raise SystemExit(main())
