"""Generate D57 DBML, Mermaid ERD and dictionary from the live PostgreSQL catalog.

Uses only Python stdlib and psql. Never prints the connection string.
SQL remains authoritative: CHECKs, partial indexes, generated columns and routines
are described in notes; DBML is not an executable migration.
"""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
QUERY = r"""
SELECT json_build_object(
 'tables', (SELECT json_agg(t ORDER BY t.name) FROM (
   SELECT c.relname AS name, obj_description(c.oid) AS comment,
    (SELECT json_agg(a ORDER BY a.position) FROM (
      SELECT att.attname AS name, att.attnum AS position,
       format_type(att.atttypid, att.atttypmod) AS type,
       att.attnotnull AS not_null, att.attgenerated <> '' AS generated,
       pg_get_expr(ad.adbin, ad.adrelid) AS default_expr,
       col_description(c.oid, att.attnum) AS comment
      FROM pg_attribute att LEFT JOIN pg_attrdef ad
        ON ad.adrelid=att.attrelid AND ad.adnum=att.attnum
      WHERE att.attrelid=c.oid AND att.attnum>0 AND NOT att.attisdropped
    ) a) AS columns,
    (SELECT json_agg(k ORDER BY k.name) FROM (
      SELECT con.conname AS name, con.contype AS kind,
        pg_get_constraintdef(con.oid) AS definition,
        ARRAY(SELECT attname FROM unnest(con.conkey) WITH ORDINALITY AS key(attnum,pos)
          JOIN pg_attribute att ON att.attrelid=c.oid AND att.attnum=key.attnum ORDER BY key.pos) AS columns,
        CASE WHEN con.contype='f' THEN con.confrelid::regclass::text END AS target,
        CASE WHEN con.contype='f' THEN ARRAY(
          SELECT attname FROM unnest(con.confkey) WITH ORDINALITY AS key(attnum,pos)
          JOIN pg_attribute att ON att.attrelid=con.confrelid AND att.attnum=key.attnum ORDER BY key.pos) END AS target_columns
      FROM pg_constraint con WHERE con.conrelid=c.oid
    ) k) AS constraints,
    (SELECT json_agg(pg_get_indexdef(i.indexrelid) ORDER BY i.indexrelid::regclass::text)
      FROM pg_index i WHERE i.indrelid=c.oid) AS indexes
   FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
   WHERE n.nspname='sasms' AND c.relkind='r'
 ) t)
);
"""


def quote(value: str) -> str:
    return '"' + value.replace('"', '\\"') + '"'


def typestr(value: str) -> str:
    return {'timestamp with time zone': 'timestamptz', 'boolean': 'boolean',
            'character varying': 'varchar'}.get(value, value).replace('character varying(', 'varchar(')


def render(tables: list[dict], output_dir: Path | None = None) -> dict[Path, str]:
    count = len(tables)
    model_note = ('D57 rebuilt 37-table Sprint 0 proposal.' if output_dir is None
                  else f'Sprint 1 upgraded schema, {count} business tables; migration history excluded.')
    dbml = ["// Generated from PostgreSQL catalogs; do not edit by hand.",
            "Project SASMS {", "  database_type: 'PostgreSQL'",
            "  Note: '" + model_note + " SQL controls CHECKs, partial indexes, NULLS NOT DISTINCT, generated columns, triggers and routines.'", "}", ""]
    erd = [f"%% Generated from PostgreSQL catalogs; full {count}-table physical model.", "erDiagram"]
    dictionary = ["# D51 — Từ điển dữ liệu vật lý", "",
                  "Sinh từ catalog PostgreSQL bằng `scripts/generate_db_artifacts.py`. `schema.sql` là nguồn thực thi. "
                  "Mọi khóa ngoại dùng NO ACTION mặc định; không xóa dây chuyền chứng từ. "
                  "Các CHECK, index và cột sinh được ghi dưới từng bảng.", ""]
    if output_dir is not None:
        dictionary[0] = '# Sprint 1 — Từ điển dữ liệu sau migration'
        dictionary[2] = dictionary[2].replace('`schema.sql`', '`schema.sql` + `database/migrations/*.sql`')
    relationships = []
    for table in tables:
        name = table['name']
        cons = table['constraints'] or []
        pk = next((c['columns'] for c in cons if c['kind'] == 'p'), [])
        fk = {col for c in cons if c['kind'] == 'f' for col in c['columns']}
        dbml.append(f'Table sasms.{name} {{')
        erd.append(f'  {name} {{')
        dictionary += [f'## `{name}`', '', '| Cột | Kiểu PostgreSQL | NULL | Mặc định / cột sinh | Khóa |',
                       '|---|---|---|---|---|']
        for col in table['columns']:
            attrs = ['not null'] if col['not_null'] else []
            if len(pk) == 1 and col['name'] in pk:
                attrs.append('pk')
            if col['default_expr'] and not col['generated']:
                attrs.append('default: `' + col['default_expr'] + '`')
            if col['generated']:
                attrs.append("note: 'GENERATED ALWAYS AS: " + col['default_expr'].replace("'", "\\'") + "'")
            suffix = ' [' + ', '.join(attrs) + ']' if attrs else ''
            dbml.append(f'  {col["name"]} {typestr(col["type"])}{suffix}')
            key = ', '.join(k for k, yes in [('PK', col['name'] in pk), ('FK', col['name'] in fk)] if yes)
            # Render key fields and state identifiers only to keep the 37-table ERD readable.
            if key or col['name'] in ('code', 'sku', 'serial', 'imei', 'status'):
                mermaid_type = typestr(col['type']).split('(')[0].replace(' ', '_')
                erd.append(f'    {mermaid_type} {col["name"]}' + (' ' + key.replace(' ', '') if key else ''))
            expr = col['default_expr'] or '—'
            if col['generated']:
                expr = 'GENERATED: ' + expr
            dictionary.append('| `' + col['name'] + '` | `' + col['type'] + '` | ' + ('Không' if col['not_null'] else 'Có') +
                              ' | ' + expr.replace('|', '\\|').replace('\n', ' ') + ' | ' + (key or '—') + ' |')
        indexes = [c for c in cons if c['kind'] == 'u' or (c['kind'] == 'p' and len(pk) > 1)]
        if indexes:
            dbml.append('  indexes {')
            for con in indexes:
                fields = '(' + ', '.join(con['columns']) + ')' if len(con['columns']) > 1 else con['columns'][0]
                dbml.append(f'    {fields} [' + ('pk' if con['kind'] == 'p' else 'unique') + ', name: ' + quote(con['name']) + ']')
            dbml.append('  }')
        notes = [c['definition'] for c in cons if c['kind'] == 'c']
        notes += table['indexes'] or []
        if notes:
            dbml += ["  Note: '''", *('    ' + note for note in notes), "  '''"]
        dbml += ['}', '']
        erd.append('  }')
        dictionary += ['', '```sql', *(c['definition'] for c in cons), *(table['indexes'] or []), '```', '']
        for con in cons:
            if con['kind'] != 'f':
                continue
            target = con['target'].split('.')[-1]
            source_cols = '(' + ', '.join(con['columns']) + ')' if len(con['columns']) > 1 else con['columns'][0]
            target_cols = '(' + ', '.join(con['target_columns']) + ')' if len(con['target_columns']) > 1 else con['target_columns'][0]
            unique_child = any(c['kind'] in ('p', 'u') and set(c['columns']).issubset(con['columns']) for c in cons)
            # DBML treats the right endpoint as the FK for an external 1:1 Ref.
            if unique_child:
                relationships.append(f'Ref {con["name"]}: sasms.{target}.{target_cols} - sasms.{name}.{source_cols}')
            else:
                relationships.append(f'Ref {con["name"]}: sasms.{name}.{source_cols} > sasms.{target}.{target_cols}')
            nullable = any(not c['not_null'] for c in table['columns'] if c['name'] in con['columns'])
            erd.append(f'  {target} ' + ('|o' if nullable else '||') + ('--o|' if unique_child else '--o{') +
                       f' {name} : "' + ','.join(con['columns']) + '"')
    dbml += relationships
    return {(output_dir/'schema.dbml' if output_dir else ROOT/'database/schema.dbml'): '\n'.join(dbml) + '\n',
            (output_dir/'erd.mmd' if output_dir else ROOT/'database/erd.mmd'): '\n'.join(erd) + '\n',
            (output_dir/'data-dictionary.md' if output_dir else ROOT/'Sprint 0/DB/D51-tu-dien-du-lieu.md'): '\n'.join(dictionary).rstrip() + '\n'}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--psql', default='psql')
    parser.add_argument('--dsn', default=os.environ.get('DATABASE_URL'), required=not os.environ.get('DATABASE_URL'))
    parser.add_argument('--check', action='store_true', help='Fail if committed artifacts differ from live schema')
    parser.add_argument('--expected-tables', type=int, default=37)
    parser.add_argument('--output-dir', type=Path, help='Separate artifacts directory for upgraded schemas; baseline defaults remain unchanged')
    args = parser.parse_args()
    if args.expected_tables != 37 and args.output_dir is None:
        parser.error('--output-dir is required for upgraded schemas to preserve Sprint 0 artifacts')
    result = subprocess.run([args.psql, '-X', '--dbname', args.dsn, '-v', 'ON_ERROR_STOP=1', '-At', '-c', QUERY],
                            capture_output=True, text=True, encoding='utf-8')
    if result.returncode:
        print('Could not inspect PostgreSQL schema (check connection and permissions).', file=sys.stderr)
        return 1
    tables = json.loads(result.stdout)['tables'] or []
    if len(tables) != args.expected_tables:
        print(f'Expected {args.expected_tables} SASMS tables, got {len(tables)}.', file=sys.stderr)
        return 1
    mismatches = []
    output_dir = (ROOT/args.output_dir).resolve() if args.output_dir is not None else None
    for path, content in render(tables, output_dir).items():
        if args.check:
            if not path.exists() or path.read_text(encoding='utf-8') != content:
                mismatches.append(str(path.relative_to(ROOT)))
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(content, encoding='utf-8', newline='\n')
    if mismatches:
        print('Outdated artifacts: ' + ', '.join(mismatches), file=sys.stderr)
        return 1
    print(f'D57 artifacts match {len(tables)} PostgreSQL tables.' if args.check else
          f'Generated DBML, Mermaid ERD and D51 data dictionary ({len(tables)} tables).')
    return 0


if __name__ == '__main__':
    sys.exit(main())
