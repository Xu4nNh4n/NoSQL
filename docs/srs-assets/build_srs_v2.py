"""Dựng SRS v2.0 từ SRS v1.1: giữ bìa + mục lục, viết lại nội dung theo mẫu REFS (Chương 3).

Chạy: python -X utf8 docs/srs-assets/build_srs_v2.py
"""
import copy
from pathlib import Path

import docx
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Cm, Pt

from srs_content import CONTENT

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "SRS_He_thong_quan_ly_sieu_thi_v1_1_viet_lai.docx"
DST = ROOT / "SRS_He_thong_quan_ly_sieu_thi_v2_0.docx"
ASSETS = Path(__file__).resolve().parent
FONT = "Times New Roman"

doc = docx.Document(SRC)
body = doc.element.body
children = list(body.iterchildren())
sect = children[-1]
# Giữ bìa, bảng thành viên và mục lục (0..9), bỏ phần còn lại.
for el in children[10:-1]:
    body.remove(el)


def _run_fmt(run, size=13, bold=False, italic=False):
    run.font.name = FONT
    run.font.size = Pt(size)
    run.font.bold = bold
    run.font.italic = italic
    rpr = run._element.get_or_add_rPr()
    fonts = rpr.find(qn("w:rFonts"))
    if fonts is None:
        fonts = OxmlElement("w:rFonts")
        rpr.insert(0, fonts)
    for a in ("w:ascii", "w:hAnsi", "w:cs", "w:eastAsia"):
        fonts.set(qn(a), FONT)


def _add_runs(p, text, size=13, bold=False, italic=False):
    """Hỗ trợ **đậm** đơn giản trong chuỗi."""
    parts = text.split("**")
    for i, part in enumerate(parts):
        if part:
            _run_fmt(p.add_run(part), size, bold or i % 2 == 1, italic)


def _spacing(p, after=160, line=360, before=0):
    pf = p.paragraph_format
    pf.space_after = Pt(after / 20)
    pf.space_before = Pt(before / 20)
    pf.line_spacing = line / 240


def _move_before_sect(p):
    body.remove(p._p)
    sect.addprevious(p._p)


def heading(text, level):
    p = doc.add_paragraph(style=f"Heading {level}")
    _add_runs(p, text, size={1: 16, 2: 13, 3: 13, 4: 13}[level], bold=True)
    p.paragraph_format.keep_with_next = True
    _move_before_sect(p)


def para(text, indent=True, align="both", italic=False, size=13):
    p = doc.add_paragraph()
    _spacing(p)
    if indent:
        p.paragraph_format.first_line_indent = Cm(1.27)
    p.alignment = {"both": WD_ALIGN_PARAGRAPH.JUSTIFY, "center": WD_ALIGN_PARAGRAPH.CENTER,
                   "left": WD_ALIGN_PARAGRAPH.LEFT}[align]
    _add_runs(p, text, size=size, italic=italic)
    _move_before_sect(p)


def bullet(text):
    p = doc.add_paragraph(style="List Paragraph")
    _spacing(p, after=80)
    p.paragraph_format.left_indent = Cm(1.0)
    p.paragraph_format.first_line_indent = Cm(-0.5)
    p.alignment = WD_ALIGN_PARAGRAPH.JUSTIFY
    _add_runs(p, "–  " + text)
    _move_before_sect(p)


def code(text):
    for line in text.strip("\n").split("\n"):
        p = doc.add_paragraph()
        _spacing(p, after=0, line=240)
        p.paragraph_format.left_indent = Cm(0.8)
        r = p.add_run(line if line else " ")
        r.font.name = "Consolas"
        r.font.size = Pt(10)
        r._element.get_or_add_rPr().append(_shd("F7F7F7"))
        _move_before_sect(p)
    spacer = doc.add_paragraph()
    _spacing(spacer, after=80)
    _move_before_sect(spacer)


def _shd(fill):
    s = OxmlElement("w:shd")
    s.set(qn("w:val"), "clear")
    s.set(qn("w:color"), "auto")
    s.set(qn("w:fill"), fill)
    return s


def _cell_fmt(cell, width_pct, shade=None):
    tcpr = cell._tc.get_or_add_tcPr()
    w = OxmlElement("w:tcW")
    w.set(qn("w:w"), str(int(width_pct * 50)))
    w.set(qn("w:type"), "pct")
    tcpr.append(w)
    if shade:
        tcpr.append(_shd(shade))
    mar = OxmlElement("w:tcMar")
    for side, v in (("top", 60), ("left", 100), ("bottom", 60), ("right", 100)):
        e = OxmlElement(f"w:{side}")
        e.set(qn("w:w"), str(v))
        e.set(qn("w:type"), "dxa")
        mar.append(e)
    tcpr.append(mar)


def _cell_text(cell, text, size, bold=False):
    lines = str(text).split("\n")
    first = True
    for line in lines:
        p = cell.paragraphs[0] if first else cell.add_paragraph()
        first = False
        _spacing(p, after=30, line=276)
        _add_runs(p, line, size=size, bold=bold)


def table(headers, rows, widths, size=None, first_col_shade=False):
    size = size or (11 if len(headers) >= 5 else 12)
    t = doc.add_table(rows=1 + len(rows), cols=len(headers))
    t.style = doc.styles["Table Grid"]
    tblpr = t._tbl.tblPr
    tw = OxmlElement("w:tblW")
    tw.set(qn("w:w"), "5000")
    tw.set(qn("w:type"), "pct")
    for old in tblpr.findall(qn("w:tblW")):
        tblpr.remove(old)
    tblpr.append(tw)
    for i, h in enumerate(headers):
        c = t.rows[0].cells[i]
        _cell_fmt(c, widths[i], "F2F2F2")
        _cell_text(c, h, size, bold=True)
    hdr = t.rows[0]._tr.get_or_add_trPr()
    th = OxmlElement("w:tblHeader")
    hdr.append(th)
    for r, row in enumerate(rows, start=1):
        for i, val in enumerate(row):
            c = t.rows[r].cells[i]
            _cell_fmt(c, widths[i], "F2F2F2" if first_col_shade and i == 0 else None)
            _cell_text(c, val, size, bold=first_col_shade and i == 0)
    body.remove(t._tbl)
    sect.addprevious(t._tbl)
    spacer = doc.add_paragraph()
    _spacing(spacer, after=120)
    _move_before_sect(spacer)


def kv(pairs):
    t_rows = [(k, v) for k, v in pairs]
    t = doc.add_table(rows=len(t_rows), cols=2)
    t.style = doc.styles["Table Grid"]
    for r, (k, v) in enumerate(t_rows):
        a, b = t.rows[r].cells
        _cell_fmt(a, 27, "F2F2F2")
        _cell_fmt(b, 73)
        _cell_text(a, k, 12, bold=True)
        _cell_text(b, v, 12)
    body.remove(t._tbl)
    sect.addprevious(t._tbl)
    spacer = doc.add_paragraph()
    _spacing(spacer, after=120)
    _move_before_sect(spacer)


FIG = [0]


def image(name, caption, width_cm=16):
    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    _spacing(p, after=60)
    p.add_run().add_picture(str(ASSETS / name), width=Cm(width_cm))
    p.paragraph_format.keep_with_next = True
    _move_before_sect(p)
    FIG[0] += 1
    para(f"Hình {FIG[0]}. {caption}", indent=False, align="center", italic=True, size=12)


def page_break():
    p = doc.add_paragraph()
    p.add_run().add_break(docx.enum.text.WD_BREAK.PAGE)
    _move_before_sect(p)


HANDLERS = {"h1": lambda a: heading(a, 1), "h2": lambda a: heading(a, 2), "h3": lambda a: heading(a, 3),
            "p": para, "b": bullet, "code": code, "pb": lambda a: page_break()}

for item in CONTENT:
    kind = item[0]
    if kind == "table":
        table(*item[1:])
    elif kind == "kv":
        kv(item[1])
    elif kind == "img":
        image(*item[1:])
    else:
        HANDLERS[kind](item[1] if len(item) > 1 else None)

# Bìa: thêm dòng phiên bản sau bảng thành viên.
cover_p = docx.text.paragraph.Paragraph(children[4], doc._body)
cover_p.alignment = WD_ALIGN_PARAGRAPH.CENTER
_add_runs(cover_p, "Phiên bản 2.1 – 07/11/2026 (đồ án môn Dữ liệu NoSQL)", size=13, italic=True)

# Word tự cập nhật mục lục khi mở file.
settings = doc.settings.element  # Word cập nhật mục lục; chạy update bằng Word rồi bỏ cờ này
upd = OxmlElement("w:updateFields")
upd.set(qn("w:val"), "true")
settings.append(upd)

doc.core_properties.title = "SRS – Hệ thống quản lý bán hàng và tồn kho siêu thị v2.1"
doc.save(DST)
print("saved", DST, "figures", FIG[0])
