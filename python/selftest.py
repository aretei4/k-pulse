"""Checks extract_voterlist.py against both roll layouts.

    python selftest.py

The card roll is built here from the sample page rather than shipped, so the
expected values below are the ground truth: they are what that page prints.
Building it needs an Odia font (Nirmala UI, which ships with Windows); without
one that half is skipped. The table half runs whenever P030028.pdf is present.
"""
import io
import sys
import tempfile
from pathlib import Path

import pymupdf

from extract_voterlist import Decoder, extract

HERE = Path(__file__).resolve().parent
NIRMALA = Path(r"C:\Windows\Fonts\Nirmala.ttc")

# serial, epic, name, relation label, relation name, house, age, gender
CARD_PAGE = [
    (1, "OR/05/030/070002", "ଅଞ୍ଜଳି ଦାସ", "ସ୍ୱାମୀଙ୍କ ନାମ", "ଖଗେଶ୍ୱର ଦାସ", "1", 40, "ସ୍ତ୍ରୀ"),
    (2, "OR/05/030/070003", "ବିଷ୍ଣୁ ଦାସ", "ପିତାଙ୍କ ନାମ", "ଗୋପୀନାଥ ଦାସ", "1", 40, "ପୁରୁଷ"),
    (3, "OR/05/030/070004", "ଯଶୋଦା ଦାସ", "ସ୍ୱାମୀଙ୍କ ନାମ", "ବିଷ୍ଣୁ ଦାସ", "1", 70, "ସ୍ତ୍ରୀ"),
    (4, "SGY1980234", "ନରେନ୍ଦ୍ର କୁମାର ଦାସ", "ପିତାଙ୍କ ନାମ", "ଜଗନ୍ନାଥ ଦାସ", "1", 69, "ପୁରୁଷ"),
    (5, "SGY1889013", "ପ୍ରମିଳା ସାହୁ", "ସ୍ୱାମୀଙ୍କ ନାମ", "ଚିରଞ୍ଜନ ସାହୁ", "1", 61, "ସ୍ତ୍ରୀ"),
    (6, "SGY1957463", "ପ୍ରଭାତୀ ସାହୁ", "ସ୍ୱାମୀଙ୍କ ନାମ", "ଚୈତନ୍ୟ ସାହୁ", "1", 58, "ସ୍ତ୍ରୀ"),
]
EXPECTED_RELATION = {"ପିତାଙ୍କ ନାମ": "F", "ସ୍ୱାମୀଙ୍କ ନାମ": "H", "ମାତାଙ୍କ ନାମ": "M"}
EXPECTED_GENDER = {"ପୁରୁଷ": "M", "ସ୍ତ୍ରୀ": "F", "ଅନ୍ୟ": "O"}

CARD_W, CARD_H, SIZE = 185.0, 108.0, 7.0


def odia_font():
    """Nirmala UI face 0, extracted from the collection pymupdf cannot embed."""
    from fontTools.ttLib import TTCollection

    out = Path(tempfile.gettempdir()) / "kpulse_odia_selftest.ttf"
    if not out.exists():
        buf = io.BytesIO()
        TTCollection(str(NIRMALA)).fonts[0].save(buf)
        out.write_bytes(buf.getvalue())
    return out


def build_card_pdf(path):
    """The sample page: six photo cards, three to a row, and a page border."""
    doc = pymupdf.open()
    page = doc.new_page(width=595, height=842)
    page.insert_font(fontname="odia", fontfile=str(odia_font()))

    def put(x, y, text):
        page.insert_text((x, y), text, fontname="odia", fontsize=SIZE)

    page.draw_rect(pymupdf.Rect(14, 14, 581, 828))
    put(25, 30, "ଭାଗ : 12")
    put(240, 30, "ବିଧାନସଭା ନିର୍ବାଚନ ମଣ୍ଡଳ : 30-ଆଳମ")

    for i, (serial, epic, name, rel_label, rel_name, house, age, gender) in enumerate(CARD_PAGE):
        x0 = 20 + (i % 3) * (CARD_W + 2)
        y0 = 60 + (i // 3) * (CARD_H + 2)
        x1, y1 = x0 + CARD_W, y0 + CARD_H
        page.draw_rect(pymupdf.Rect(x0, y0, x1, y1))
        page.draw_rect(pymupdf.Rect(x0 + 6, y0 + 4, x0 + 40, y0 + 16))
        photo = pymupdf.Rect(x1 - 52, y0 + 20, x1 - 8, y1 - 10)
        page.draw_rect(photo)

        put(x0 + 20, y0 + 13, str(serial))
        put(x0 + 100, y0 + 13, epic)
        put(photo.x0 + 3, (photo.y0 + photo.y1) / 2, "ଫଟୋ ଉପଲବ୍ଧ")
        put(x0 + 6, y0 + 32, f"ନାମ : {name}")
        put(x0 + 6, y0 + 48, f"{rel_label}: {rel_name}")
        put(x0 + 6, y0 + 62, f"ଘର ନଂ : {house}")
        put(x0 + 6, y0 + 76, f"ବୟସ : {age} ଲିଙ୍ଗ : {gender}")

    doc.save(path)


def check(label, got, want, failures):
    if got != want:
        failures.append(f"{label}: got {got!r}, expected {want!r}")


def card_layout(failures):
    pdf = Path(tempfile.gettempdir()) / "kpulse_cards_selftest.pdf"
    build_card_pdf(str(pdf))
    part, rows = extract(str(pdf), Decoder(HERE / "odia_glyphs.json"))

    check("part number", part, "12", failures)
    check("voters found", len(rows), len(CARD_PAGE), failures)
    for row, want in zip(rows, CARD_PAGE):
        serial, epic, name, rel_label, rel_name, house, age, gender = want
        where = f"card {serial}"
        check(f"{where} serial", row["serial"], serial, failures)
        check(f"{where} name", row["name"], name, failures)
        check(f"{where} relation", row["reltype"], EXPECTED_RELATION[rel_label], failures)
        check(f"{where} relation name", row["relname"], rel_name, failures)
        check(f"{where} house", row["house"], house, failures)
        check(f"{where} age", row["age"], age, failures)
        check(f"{where} gender", row["gender"], EXPECTED_GENDER[gender], failures)
        check(f"{where} epic", row["epic"], epic, failures)
    print(f"card layout: {len(rows)} voters read")


def table_layout(failures):
    """The tabular roll, against figures checked by hand against the printed PDF."""
    part, rows = extract(str(HERE / "P030028.pdf"), Decoder(HERE / "odia_glyphs.json"))
    check("part number", part, "28", failures)
    check("voters found", len(rows), 975, failures)
    first = rows[0]
    check("first serial", first["serial"], 1, failures)
    check("first house", first["house"], "1", failures)
    blank = [r["serial"] for r in rows if not r["name"] or r["age"] is None]
    check("rows missing a name or age", blank, [], failures)
    print(f"table layout: {len(rows)} voters read")


def main():
    failures = []
    if NIRMALA.exists():
        card_layout(failures)
    else:
        print(f"card layout: skipped, no Odia font at {NIRMALA}")
    if (HERE / "P030028.pdf").exists():
        table_layout(failures)
    else:
        print("table layout: skipped, P030028.pdf is not here")

    if failures:
        print(f"\n{len(failures)} failure(s):")
        for f in failures:
            print("  -", f)
        return 1
    print("\nall checks passed")
    return 0


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    sys.exit(main())
