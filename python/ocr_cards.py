"""
Reads a *scanned* photo-card electoral roll, where the pages are images and
there is no text layer to decode at all.

Rolls split or re-exported by some tools arrive rasterised, so none of the
glyph work in extract_voterlist.py applies: the fields have to be read by OCR
(tesseract, with its Odia model). The cards are still found the way they are in
a digital roll -- as the box the page repeats -- only here the boxes come from
the image rather than the PDF's drawing operators.

Each field is cropped and read on its own, which is what makes this usable: the
serial and the EPIC number are read as Latin with a character whitelist, so the
two fields most likely to be mistyped are also the most reliable, and the Odia
is never asked to compete with Latin in the same call.

Needs: pip install opencv-python pytesseract, and tesseract itself with the
'ori' language data (tesseract --list-langs).
"""
import re
import shutil
import cv2
import numpy as np
import pymupdf
import pytesseract

# Enlarging the scan is what makes the Odia legible, and how far to enlarge is
# measured from the printed card itself rather than fixed. Rolls are scanned at
# different resolutions, and some declare a page size in pixels, so neither a
# fixed multiplier nor the page's dpi can be trusted: the first leaves a coarse
# scan unreadable and blows a fine one up to hundreds of megabytes a page, and
# the second reads 72 on any roll whose page size was set from its own image.
# The card's height in pixels tracks the size of the text inside it, which is
# the thing tesseract actually cares about.
#
# Both targets were measured on a roll of 90 whose cards stand 121 pixels tall,
# where they worked out at 6x and 4x. The Odia wants the larger of the two -- it
# reads the conjuncts in a name appreciably better -- while the serial's digits
# start running into each other there, so the serial is read at the smaller one.
# Reading a line at a time rather than the whole block was tried too, and is
# worse.
TEXT_CARD_PX = 726
SERIAL_CARD_PX = 484
# However far off a scan is, enlarging past this is not worth the memory, and a
# scan finer than the target is reduced to it.
SCALE_LIMITS = (0.4, 8.0)
FALLBACK_DPI = 450

DIGITS_ONLY = "-c tessedit_char_whitelist=0123456789"
EPIC = "--psm 7 -c tessedit_char_whitelist=ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789/"
BLOCK = "--psm 6"
# Tesseract reads the serial as a line first and as a word second: the two
# disagree often enough on these scans that trying both is worth the second call.
SERIAL_PSMS = (6, 7)

WINDOWS_TESSERACT = r"C:\Program Files\Tesseract-OCR\tesseract.exe"


def use_tesseract(path=None):
    """Point pytesseract at the binary, which is often not on PATH on Windows."""
    if path:
        pytesseract.pytesseract.tesseract_cmd = path
    elif not shutil.which("tesseract"):
        pytesseract.pytesseract.tesseract_cmd = WINDOWS_TESSERACT
    return pytesseract.get_tesseract_version()


def page_image(page):
    """The page's own scan, grayscale and at its own resolution.

    Taking the embedded image and resizing it later beats rendering the PDF to
    a high dpi: that goes through a faster scaler, and tesseract reads the
    result noticeably worse -- enough to turn a legible name into a different
    one. Anything that is not a single full-page image falls back to rendering.
    """
    images = page.get_images(full=True)
    if len(images) == 1:
        raw = page.parent.extract_image(images[0][0])["image"]
        native = cv2.imdecode(np.frombuffer(raw, dtype=np.uint8), cv2.IMREAD_GRAYSCALE)
        if native is not None:
            return native
    pix = page.get_pixmap(dpi=FALLBACK_DPI, colorspace=pymupdf.csGRAY)
    return np.frombuffer(pix.samples, dtype=np.uint8).reshape(pix.height, pix.width)


def prepared(page):
    """(image, boxes, cards) with the page enlarged to read the Odia best.

    The cards are found on the scan as it is -- their borders are strong lines
    and survive at any resolution -- and their height then says how much to
    enlarge. They are then found again on the enlarged image: multiplying the
    coordinates up instead was tried and reads serials worse, because a pixel
    of slack around a box at the original size becomes six, which is enough to
    clip a digit.
    """
    native = page_image(page)
    boxes = boxes_in(native)
    cards = cards_in(boxes, native.size)
    if not cards:
        return native, boxes, []

    heights = sorted(card[3] for card in cards)
    low, high = SCALE_LIMITS
    scale = max(low, min(high, TEXT_CARD_PX / heights[len(heights) // 2]))
    if abs(scale - 1.0) < 0.02:
        return native, boxes, cards

    smooth = cv2.INTER_CUBIC if scale > 1 else cv2.INTER_AREA
    gray = cv2.resize(native, None, fx=scale, fy=scale, interpolation=smooth)
    boxes = boxes_in(gray)
    return gray, boxes, cards_in(boxes, gray.size)


# A drawn border has an outside and an inside edge, so every box is found twice,
# a few pixels apart. This is how far apart two findings may be and still be the
# same box.
SAME_BOX = 14


def same_box(a, b):
    return (abs(a[0] - b[0]) <= SAME_BOX and abs(a[1] - b[1]) <= SAME_BOX
            and abs(a[2] - b[2]) <= SAME_BOX * 2 and abs(a[3] - b[3]) <= SAME_BOX * 2)


def boxes_in(gray):
    """Every ruled box on the page, the two edges of each border merged into one."""
    inv = cv2.threshold(gray, 200, 255, cv2.THRESH_BINARY_INV)[1]
    contours = cv2.findContours(inv, cv2.RETR_TREE, cv2.CHAIN_APPROX_SIMPLE)[0]
    found = [cv2.boundingRect(c) for c in contours]
    found = sorted((b for b in found if b[2] >= 20 and b[3] >= 20),
                   key=lambda b: -b[2] * b[3])
    kept = []
    for box in found:
        if not any(same_box(box, k) for k in kept):
            kept.append(box)
    return kept


# A card takes up a real share of the page: thirty to a page is about three per
# cent each, and even a denser roll stays well above this. A line of text does
# not, which is what keeps letters and words out of the running.
CARD_SHARE = 0.008


def cards_in(boxes, page_area):
    """The voter cards: the box the page repeats, in reading order.

    Matching sizes within a percentage rather than a fixed number of pixels
    matters more than it looks: a scan puts a few pixels of noise on every
    edge, which is nothing on a photo box and enough to scatter the much larger
    card across several buckets. Where two sizes still repeat equally often, the
    larger is the card and the smaller the photo box inside it.
    """
    candidates = [b for b in boxes if b[2] * b[3] >= page_area * CARD_SHARE]
    best, best_rank = [], None
    for box in candidates:
        group = [o for o in candidates
                 if abs(o[2] - box[2]) <= max(6, box[2] * 0.02)
                 and abs(o[3] - box[3]) <= max(6, box[3] * 0.02)]
        rank = (len(group), box[2] * box[3])
        if best_rank is None or rank > best_rank:
            best, best_rank = group, rank
    if len(best) < 3:
        return []
    height = max(b[3] for b in best)
    best.sort(key=lambda b: (round(b[1] / (height / 2)), b[0]))
    return best


def inside(card, boxes):
    """The boxes drawn within a card: its serial box and its photo box.

    A letter's own outline is a box too, so anything much smaller than the card
    is left out rather than mistaken for one of them.
    """
    x, y, w, h = card
    return [b for b in boxes
            if b[2] * b[3] < w * h * 0.9 and b[2] > w * 0.1 and b[3] > h * 0.05
            and x <= b[0] and b[0] + b[2] <= x + w and y <= b[1] and b[1] + b[3] <= y + h]


def read_image(crop, config, lang):
    """One field, enlarged and flattened to black and white for OCR."""
    if crop.size == 0 or min(crop.shape) < 4:
        return ""
    if min(crop.shape) < 40:                       # small fields need the help most
        crop = cv2.resize(crop, None, fx=2, fy=2, interpolation=cv2.INTER_CUBIC)
    crop = cv2.threshold(crop, 0, 255, cv2.THRESH_BINARY | cv2.THRESH_OTSU)[1]
    crop = cv2.copyMakeBorder(crop, 20, 20, 20, 20, cv2.BORDER_CONSTANT, value=255)
    return pytesseract.image_to_string(crop, lang=lang, config=config).strip()


def read(gray, rect, config, lang):
    x, y, w, h = rect
    return read_image(gray[y:y + h, x:x + w], config, lang)


def digit_strip(crop):
    """Just the serial's digits, cut out of the box they are printed in.

    Reading the box whole does not work: its border is as much ink as the
    number, and some entries carry a # alongside to mark a change. The border
    is a single shape as tall as the box, and the # stands apart to the left,
    so keeping only digit-sized shapes and then only the rightmost cluster of
    them leaves the number by itself. This is worth the trouble -- it took the
    serial from roughly six in seven right to better than nineteen in twenty.
    """
    inv = cv2.threshold(crop, 0, 255, cv2.THRESH_BINARY_INV | cv2.THRESH_OTSU)[1]
    count, _labels, stats, _centroids = cv2.connectedComponentsWithStats(inv, 8)
    h, w = crop.shape
    glyphs = []
    for i in range(1, count):
        x, y, gw, gh = stats[i][:4]
        if gh < h * 0.2 or gh > h * 0.85 or gw > w * 0.3:
            continue                               # a rule, or the border itself
        glyphs.append((x, y, gw, gh))
    if not glyphs:
        return crop
    glyphs.sort(key=lambda g: g[0])
    cluster = [glyphs[-1]]
    for glyph in reversed(glyphs[:-1]):
        if min(c[0] for c in cluster) - (glyph[0] + glyph[2]) >= h * 0.5:
            break                                  # a gap this wide is the # marker
        cluster.append(glyph)
    x0 = min(g[0] for g in cluster)
    x1 = max(g[0] + g[2] for g in cluster)
    y0 = min(g[1] for g in cluster)
    y1 = max(g[1] + g[3] for g in cluster)
    return crop[max(0, y0 - 10):y1 + 10, max(0, x0 - 10):x1 + 10]


def read_serial(gray, box):
    """The serial, read from a crop reduced to the resolution digits read best at."""
    x, y, w, h = box
    crop = gray[y:y + h, x:x + w]
    factor = SERIAL_CARD_PX / TEXT_CARD_PX
    crop = cv2.resize(crop, None, fx=factor, fy=factor, interpolation=cv2.INTER_AREA)
    strip = digit_strip(crop)
    for psm in SERIAL_PSMS:
        digits = "".join(ch for ch in read_image(strip, f"--psm {psm} {DIGITS_ONLY}", "eng")
                         if ch.isdigit())
        if digits:
            return digits
    return ""


def card_fields(gray, card, boxes, lang):
    """The four crops a card is read from: serial, EPIC, and the text column."""
    x, y, w, h = card
    nested = inside(card, boxes)
    # The serial sits in a shallow box along the top; the photo box is the big one.
    serial_box = min((b for b in nested if b[3] < h * 0.35), key=lambda b: b[1], default=None)
    photo_box = max((b for b in nested if b is not serial_box), key=lambda b: b[2] * b[3],
                    default=None)

    head_bottom = serial_box[1] + serial_box[3] if serial_box else y + int(h * 0.2)
    text_right = photo_box[0] if photo_box else x + w

    # Clear of the card's own border by a margin that follows the card's size:
    # a few fixed pixels is enough on a coarse scan and not on a fine one, and a
    # border left in the crop is read as ink on every line.
    pad = max(3, h // 50)
    serial = read_serial(gray, serial_box) if serial_box else ""
    epic_left = serial_box[0] + serial_box[2] if serial_box else x
    epic = read(gray, (epic_left + pad, y + pad, x + w - epic_left - 2 * pad,
                       head_bottom - y - pad), EPIC, "eng")
    text = read(gray, (x + pad, head_bottom + pad // 2, text_right - x - 2 * pad,
                       y + h - head_bottom - pad), BLOCK, lang)
    return serial, epic, text


ODIA = re.compile(r"[\u0B00-\u0B7F]")
DIGITS = re.compile(r"\d+")


def first_letter(text):
    """The first Odia letter of a line, which is what names its field."""
    found = ODIA.search(text)
    return found.group(0) if found else ""


def value_after(line, index=0):
    """What a label introduces, up to where the next label begins.

    "ବୟସ : 21 ଲିଙ୍ଗ : ସ୍ତ୍ରୀ" is one line holding two fields. Where OCR has lost
    the colon, the text after the first run of spaces is used instead.
    """
    parts = line.split(":")
    if len(parts) > index + 1:
        return parts[index + 1]
    if index == 0:
        split = re.split(r"\s{2,}|\s", line, maxsplit=1)
        return split[1] if len(split) > 1 else ""
    return ""


def last_odia_run(text):
    """The last stretch of Odia on a line, which is where a gender word sits."""
    runs = re.findall(r"[\u0B00-\u0B7F]+", text)
    return runs[-1] if runs else ""


def part_number(text):
    """The roll's part number, printed as ଭାଗ ନଂ in the page header.

    The same header carries ଅନୁଭାଗ, the section, which ends in the same three
    letters -- so only a ଭାଗ starting a word counts.
    """
    for found in re.finditer("\u0b2d\u0b3e\u0b17", text):
        before = text[found.start() - 1] if found.start() else " "
        if ODIA.match(before):
            continue
        digits = DIGITS.search(text[found.end():found.end() + 40])
        if digits:
            return digits.group(0)
    return None
