# -*- coding: utf-8 -*-
"""
Converter script: DS1 Markdown to Professional Word (.docx)
Author: Nguyen Tien Vu (Data / AI Lead)
"""

import os
import re
import docx
from docx.shared import Inches, Pt, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.enum.table import WD_TABLE_ALIGNMENT, WD_ALIGN_VERTICAL
from docx.oxml import parse_xml
from docx.oxml.ns import nsdecls

def set_cell_background(cell, fill_hex):
    tcPr = cell._tc.get_or_add_tcPr()
    shd = parse_xml(f'<w:shd {nsdecls("w")} w:fill="{fill_hex}"/>')
    tcPr.append(shd)

def set_cell_margins(cell, top=100, bottom=100, left=140, right=140):
    tcPr = cell._tc.get_or_add_tcPr()
    tcMar = parse_xml(f'''
        <w:tcMar {nsdecls("w")}>
            <w:top w:w="{top}" w:type="dxa"/>
            <w:bottom w:w="{bottom}" w:type="dxa"/>
            <w:left w:w="{left}" w:type="dxa"/>
            <w:right w:w="{right}" w:type="dxa"/>
        </w:tcMar>
    ''')
    tcPr.append(tcMar)

def set_table_borders(table, color="D1D5DB", sz="4", val="single"):
    tblPr = table._tbl.tblPr
    borders = parse_xml(f'''
        <w:tblBorders {nsdecls("w")}>
            <w:top w:val="{val}" w:sz="{sz}" w:space="0" w:color="{color}"/>
            <w:bottom w:val="{val}" w:sz="{sz}" w:space="0" w:color="{color}"/>
            <w:left w:val="none"/>
            <w:right w:val="none"/>
            <w:insideH w:val="{val}" w:sz="{sz}" w:space="0" w:color="{color}"/>
            <w:insideV w:val="none"/>
        </w:tblBorders>
    ''')
    tblPr.append(borders)

def add_header_footer(doc):
    for s in doc.sections:
        s.top_margin = Inches(0.8)
        s.bottom_margin = Inches(0.8)
        s.left_margin = Inches(0.9)
        s.right_margin = Inches(0.9)
        s.different_first_page_header_footer = False
        
        # Header
        header = s.header
        hp = header.paragraphs[0]
        hp.alignment = WD_ALIGN_PARAGRAPH.RIGHT
        hrun = hp.add_run("SASMS — DS1: Phần mềm Tăng trưởng Phân hệ AI | Sprint 1 (Quality Gate M2)")
        hrun.font.name = "Calibri"
        hrun.font.size = Pt(8.5)
        hrun.font.color.rgb = RGBColor(107, 114, 128)
        
        # Footer
        footer = s.footer
        fp = footer.paragraphs[0]
        fp.alignment = WD_ALIGN_PARAGRAPH.LEFT
        frun1 = fp.add_run("Dự án SASMS • Nguyễn Tiến Vũ (Data / AI Lead)")
        frun1.font.name = "Calibri"
        frun1.font.size = Pt(8.5)
        frun1.font.color.rgb = RGBColor(107, 114, 128)
        
        frun2 = fp.add_run("\t\tBảo mật nội bộ")
        frun2.font.name = "Calibri"
        frun2.font.size = Pt(8.5)
        frun2.font.color.rgb = RGBColor(156, 163, 175)

def format_inlines(paragraph, text):
    tokens = re.split(r'(\*\*[^*]+\*\*|`[^`]+`|\*[^*]+\*)', text)
    for tok in tokens:
        if not tok:
            continue
        if tok.startswith('**') and tok.endswith('**'):
            run = paragraph.add_run(tok[2:-2])
            run.bold = True
        elif tok.startswith('`') and tok.endswith('`'):
            run = paragraph.add_run(tok[1:-1])
            run.font.name = 'Consolas'
            run.font.size = Pt(9.5)
            run.font.color.rgb = RGBColor(194, 65, 12)
        elif tok.startswith('*') and tok.endswith('*'):
            run = paragraph.add_run(tok[1:-1])
            run.italic = True
        else:
            paragraph.add_run(tok)

def convert_md_to_docx(md_path, docx_path):
    with open(md_path, 'r', encoding='utf-8') as f:
        md_text = f.read()

    doc = docx.Document()
    add_header_footer(doc)

    normal_style = doc.styles['Normal']
    normal_style.font.name = 'Calibri'
    normal_style.font.size = Pt(10.5)
    normal_style.font.color.rgb = RGBColor(31, 41, 55)
    normal_style.paragraph_format.line_spacing = 1.2
    normal_style.paragraph_format.space_after = Pt(4)

    lines = md_text.splitlines()
    i = 0
    in_code_block = False
    code_lang = ""
    code_lines = []

    while i < len(lines):
        line = lines[i]
        
        if line.startswith('```'):
            if not in_code_block:
                in_code_block = True
                code_lang = line[3:].strip()
                code_lines = []
                i += 1
                continue
            else:
                in_code_block = False
                table = doc.add_table(rows=1, cols=1)
                table.alignment = WD_TABLE_ALIGNMENT.CENTER
                cell = table.cell(0, 0)
                set_cell_background(cell, "F9FAFB")
                set_cell_margins(cell, top=140, bottom=140, left=200, right=200)
                
                tcPr = cell._tc.get_or_add_tcPr()
                tcBorders = parse_xml(f'''
                    <w:tcBorders {nsdecls("w")}>
                        <w:top w:val="single" w:sz="6" w:space="0" w:color="E5E7EB"/>
                        <w:left w:val="single" w:sz="18" w:space="0" w:color="3B82F6"/>
                        <w:bottom w:val="single" w:sz="6" w:space="0" w:color="E5E7EB"/>
                        <w:right w:val="single" w:sz="6" w:space="0" w:color="E5E7EB"/>
                    </w:tcBorders>
                ''')
                tcPr.append(tcBorders)
                
                cp = cell.paragraphs[0]
                cp.paragraph_format.space_after = Pt(2)
                cp.paragraph_format.line_spacing = 1.15
                
                if code_lang:
                    lbl = cp.add_run(f"[{code_lang.upper()}]\n")
                    lbl.bold = True
                    lbl.font.size = Pt(8.5)
                    lbl.font.name = "Consolas"
                    lbl.font.color.rgb = RGBColor(37, 99, 235)
                
                c_content = "\n".join(code_lines)
                crun = cp.add_run(c_content)
                crun.font.name = "Consolas"
                crun.font.size = Pt(9.0)
                crun.font.color.rgb = RGBColor(17, 24, 39)
                
                doc.add_paragraph()
                i += 1
                continue

        if in_code_block:
            code_lines.append(line)
            i += 1
            continue

        stripped = line.strip()

        if not stripped:
            i += 1
            continue

        if stripped == '---':
            p = doc.add_paragraph()
            p.paragraph_format.space_before = Pt(6)
            p.paragraph_format.space_after = Pt(6)
            pBdr = parse_xml(f'''
                <w:pBdr {nsdecls("w")}>
                    <w:bottom w:val="single" w:sz="6" w:space="1" w:color="E5E7EB"/>
                </w:pBdr>
            ''')
            p._p.get_or_add_pPr().append(pBdr)
            i += 1
            continue

        if line.startswith('# '):
            p = doc.add_paragraph()
            p.paragraph_format.space_before = Pt(14)
            p.paragraph_format.space_after = Pt(8)
            run = p.add_run(line[2:].strip())
            run.bold = True
            run.font.size = Pt(20)
            run.font.color.rgb = RGBColor(30, 58, 138)
            run.font.name = "Calibri"
            i += 1
            continue

        if line.startswith('## '):
            p = doc.add_paragraph()
            p.paragraph_format.space_before = Pt(16)
            p.paragraph_format.space_after = Pt(6)
            p.paragraph_format.keep_with_next = True
            run = p.add_run(line[3:].strip())
            run.bold = True
            run.font.size = Pt(14)
            run.font.color.rgb = RGBColor(30, 58, 138)
            run.font.name = "Calibri"
            
            pBdr = parse_xml(f'''
                <w:pBdr {nsdecls("w")}>
                    <w:bottom w:val="single" w:sz="12" w:space="4" w:color="93C5FD"/>
                </w:pBdr>
            ''')
            p._p.get_or_add_pPr().append(pBdr)
            i += 1
            continue

        if line.startswith('### '):
            p = doc.add_paragraph()
            p.paragraph_format.space_before = Pt(12)
            p.paragraph_format.space_after = Pt(4)
            p.paragraph_format.keep_with_next = True
            run = p.add_run(line[4:].strip())
            run.bold = True
            run.font.size = Pt(12)
            run.font.color.rgb = RGBColor(37, 99, 235)
            run.font.name = "Calibri"
            i += 1
            continue

        if line.startswith('#### '):
            p = doc.add_paragraph()
            p.paragraph_format.space_before = Pt(8)
            p.paragraph_format.space_after = Pt(2)
            p.paragraph_format.keep_with_next = True
            run = p.add_run(line[5:].strip())
            run.bold = True
            run.font.size = Pt(11)
            run.font.color.rgb = RGBColor(79, 70, 229)
            run.font.name = "Calibri"
            i += 1
            continue

        # Tables
        if stripped.startswith('|') and stripped.endswith('|'):
            table_lines = []
            while i < len(lines) and lines[i].strip().startswith('|') and lines[i].strip().endswith('|'):
                table_lines.append(lines[i].strip())
                i += 1
            
            parsed_rows = []
            for t_line in table_lines:
                if re.match(r'^\|(\s*:?-+:?\s*\|)+$', t_line):
                    continue
                cells = [c.strip() for c in t_line.split('|')[1:-1]]
                parsed_rows.append(cells)
            
            if parsed_rows:
                num_cols = max(len(r) for r in parsed_rows)
                tbl = doc.add_table(rows=len(parsed_rows), cols=num_cols)
                tbl.alignment = WD_TABLE_ALIGNMENT.CENTER
                set_table_borders(tbl, color="D1D5DB", sz="4")
                
                for row_idx, r_data in enumerate(parsed_rows):
                    row = tbl.rows[row_idx]
                    trPr = row._tr.get_or_add_trPr()
                    trPr.append(parse_xml(f'<w:cantSplit {nsdecls("w")}/>'))
                    if row_idx == 0:
                        trPr.append(parse_xml(f'<w:tblHeader {nsdecls("w")}/>'))
                        
                    for col_idx in range(num_cols):
                        cell = row.cells[col_idx]
                        cell.vertical_alignment = WD_ALIGN_VERTICAL.CENTER
                        set_cell_margins(cell, top=100, bottom=100, left=140, right=140)
                        
                        txt = r_data[col_idx] if col_idx < len(r_data) else ""
                        txt = txt.replace('<br>', '\n')
                        
                        cp = cell.paragraphs[0]
                        cp.paragraph_format.line_spacing = 1.15
                        cp.paragraph_format.space_after = Pt(1)
                        cp.paragraph_format.space_before = Pt(1)
                        
                        if row_idx == 0:
                            set_cell_background(cell, "1E3A8A")
                            format_inlines(cp, txt)
                            for r in cp.runs:
                                r.font.bold = True
                                r.font.color.rgb = RGBColor(255, 255, 255)
                                r.font.size = Pt(9.5)
                        else:
                            if row_idx % 2 == 1:
                                set_cell_background(cell, "FFFFFF")
                            else:
                                set_cell_background(cell, "F9FAFB")
                            format_inlines(cp, txt)
                            for r in cp.runs:
                                r.font.size = Pt(9.5)
                
                doc.add_paragraph()
            continue

        if stripped.startswith('- ') or stripped.startswith('* '):
            indent_level = (len(line) - len(line.lstrip())) // 2
            p = doc.add_paragraph(style='List Bullet')
            p.paragraph_format.left_indent = Inches(0.25 * (indent_level + 1))
            p.paragraph_format.space_before = Pt(1)
            p.paragraph_format.space_after = Pt(2)
            format_inlines(p, stripped[2:])
            i += 1
            continue

        m_num = re.match(r'^(\d+)\.\s+(.*)$', stripped)
        if m_num:
            p = doc.add_paragraph(style='List Number')
            p.paragraph_format.space_before = Pt(1)
            p.paragraph_format.space_after = Pt(2)
            format_inlines(p, m_num.group(2))
            i += 1
            continue

        p = doc.add_paragraph()
        format_inlines(p, stripped)
        i += 1

    doc.save(docx_path)
    print(f"Successfully converted {md_path} -> {docx_path}")

if __name__ == "__main__":
    src = r"Sprint 1\AI\DS1-phan-mem-tang-truong-ai-sprint1.md"
    dst = r"Sprint 1\AI\DS1_Phan_mem_tang_truong_AI_Sprint1.docx"
    convert_md_to_docx(src, dst)
