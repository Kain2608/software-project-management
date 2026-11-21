#!/usr/bin/env python3
"""Extract Kien's assignment as source data; never execute workbook contents."""
from __future__ import annotations

import argparse
import csv
import hashlib
import json
from pathlib import Path
import shutil
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
NS = {'m': 'http://schemas.openxmlformats.org/spreadsheetml/2006/main'}
PERSON = 'Trần Trung Kiên'


def worksheets(path: Path):
    with zipfile.ZipFile(path) as archive:
        shared = []
        if 'xl/sharedStrings.xml' in archive.namelist():
            shared = [''.join(item.itertext()) for item in ET.fromstring(archive.read('xl/sharedStrings.xml'))]
        relations = {item.attrib['Id']: item.attrib['Target']
                     for item in ET.fromstring(archive.read('xl/_rels/workbook.xml.rels'))}
        workbook = ET.fromstring(archive.read('xl/workbook.xml'))
        for sheet in workbook.find('m:sheets', NS):
            target = relations[sheet.attrib['{http://schemas.openxmlformats.org/officeDocument/2006/relationships}id']]
            member = target.lstrip('/') if target.startswith('/') else 'xl/' + target
            rows = []
            for row in ET.fromstring(archive.read(member)).findall('.//m:sheetData/m:row', NS):
                cells = {}
                for cell in row.findall('m:c', NS):
                    value = cell.find('m:v', NS)
                    text = value.text or '' if value is not None else ''
                    if cell.attrib.get('t') == 's':
                        text = shared[int(text)] if text else ''
                    elif cell.attrib.get('t') == 'inlineStr':
                        inline = cell.find('m:is', NS)
                        text = ''.join(inline.itertext()) if inline is not None else ''
                    cells[''.join(filter(str.isalpha, cell.attrib['r']))] = text
                rows.append((int(row.attrib['r']), cells))
            yield sheet.attrib['name'], rows


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('workbook', type=Path)
    args = parser.parse_args()
    destination = ROOT/'Sprint 4/DB/inputs'
    destination.mkdir(parents=True, exist_ok=True)
    name, rows = next(worksheets(args.workbook))
    assignments = []
    for row, cells in rows:
        if not cells.get('B', '').startswith('S4-'):
            continue
        primary = PERSON in cells.get('F', '')
        support = PERSON in cells.get('G', '')
        if primary or support:
            assignments.append({
                'id': cells['B'], 'role': 'primary' if primary else 'support',
                'task': cells.get('E', ''), 'owner': cells.get('F', ''),
                'support': cells.get('G', ''), 'inputs': cells.get('H', ''),
                'outputs': cells.get('I', ''), 'evidence': cells.get('J', ''),
                'start_planned': cells.get('K', ''), 'due_planned': cells.get('L', ''),
                'owner_hours_planned': cells.get('M', ''),
                'output_status_at_source': cells.get('N', ''),
                'calendar_status_at_2026_11_05': cells.get('O', ''),
                'source_sheet': name, 'source_row': row})
    primary = [item for item in assignments if item['role'] == 'primary']
    if len(primary) != 6 or sum(int(item['owner_hours_planned']) for item in primary) != 116:
        raise ValueError('Unexpected assignment; review workbook version/person before proceeding')
    with (destination/'phan-cong-sprint-4.csv').open('w', encoding='utf-8-sig', newline='') as stream:
        writer = csv.DictWriter(stream, fieldnames=list(assignments[0]))
        writer.writeheader()
        writer.writerows(assignments)
    metadata = {
        'source_filename': args.workbook.name,
        'sha256': hashlib.sha256(args.workbook.read_bytes()).hexdigest(),
        'person': PERSON, 'primary_count': len(primary), 'support_count': len(assignments)-len(primary),
        'primary_hours_planned': 116, 'planning_period': '2026-11-08/2026-11-20',
        'source_calendar_as_of': '2026-11-05', 'source_status_preserved': True,
        'note': 'Assignment data only. Planned hours are not actual work logs; source status is not a current completion claim.'}
    (destination/'source.json').write_text(json.dumps(metadata, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    shutil.copyfile(args.workbook, destination/'phan-cong-sprint-4.xlsx')
    print(f'Extracted {len(primary)} primary and {len(assignments)-len(primary)} support tasks; 116 planned owner hours.')


if __name__ == '__main__':
    main()
