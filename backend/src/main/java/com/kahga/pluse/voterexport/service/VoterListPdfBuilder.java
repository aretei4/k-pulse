package com.kahga.pluse.voterexport.service;

import com.kahga.pluse.common.exception.BusinessException;
import com.kahga.pluse.voterexport.dto.ExportVoterRow;
import com.kahga.pluse.voterexport.dto.SentimentFilter;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfGState;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfTemplate;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.font.GlyphVector;
import java.awt.geom.Point2D;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The walk list itself (DL-6): A4, one booth per file, voters in roll order,
 * and on every page the agent's name — diagonally across it and again in the
 * footer — so a copy that leaves the agent's phone still says whose it was.
 */
@Component
@RequiredArgsConstructor
public class VoterListPdfBuilder {

    /**
     * What a page holds. The rows are broken into pages of this size rather than
     * left to fill the sheet, so the page count the agent is quoted before
     * downloading is the page count they get — an Odia name that wraps onto two
     * lines cannot push the file onto an extra sheet.
     */
    public static final int ROWS_PER_PAGE = 28;

    private static final String[] HEADERS = {
        "Sl", "Name", "Relation", "Age", "Gender", "House no.", "Sentiment", "Date recorded"
    };

    private static final float[] WIDTHS = {0.6f, 3.2f, 2.6f, 0.7f, 0.9f, 1.2f, 1.3f, 1.5f};

    /** The same list with the booth named, for an export that spans several. */
    private static final String[] HEADERS_WITH_BOOTH = {
        "Sl", "Booth", "Name", "Relation", "Age", "Gender", "House no.", "Sentiment", "Date recorded"
    };

    private static final float[] WIDTHS_WITH_BOOTH = {0.6f, 1.8f, 3.0f, 2.4f, 0.7f, 0.9f, 1.1f, 1.3f, 1.4f};

    private static final float BODY_SIZE = 8.5f;

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm").withZone(IST);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy").withZone(IST);

    private static final Color INK = new Color(0x15, 0x2A, 0x38);
    private static final Color MUTED = new Color(0x4A, 0x5A, 0x66);
    private static final Color PAPER = new Color(0xF3, 0xEE, 0xE3);
    private static final Color LINE = new Color(0xDA, 0xD2, 0xBE);

    private final PdfFontSource fonts;

    /** Everything the file prints. Assembled by the service, which has already checked access. */
    public record Request(
            String boothName,
            String boothPath,
            String candidateName,
            Set<SentimentFilter> chosen,
            List<ExportVoterRow> rows,
            String agentName,
            Instant downloadedAt,
            /** True for the report export, which may cover more than one booth. */
            boolean showBooth) {

        /** The booth walk list (DL-6), which is always one booth. */
        public Request(
                String boothName,
                String boothPath,
                String candidateName,
                Set<SentimentFilter> chosen,
                List<ExportVoterRow> rows,
                String agentName,
                Instant downloadedAt) {
            this(boothName, boothPath, candidateName, chosen, rows, agentName, downloadedAt, false);
        }
    }

    public byte[] build(Request request) {
        Document document = new Document(PageSize.A4, 32, 32, 36, 44);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfWriter writer = PdfWriter.getInstance(document, out);
            writer.setPageEvent(new Stamp(request));
            document.open();

            Shaper shaper = new Shaper(writer);
            heading(document, request);
            List<ExportVoterRow> rows = request.rows();
            for (int from = 0; from < Math.max(rows.size(), 1); from += ROWS_PER_PAGE) {
                if (from > 0) {
                    document.newPage();
                }
                int to = Math.min(from + ROWS_PER_PAGE, rows.size());
                document.add(table(shaper, rows.subList(from, to), from + 1, request.showBooth()));
            }

            document.close();
            return out.toByteArray();
        } catch (DocumentException | IOException ex) {
            throw new BusinessException("Could not build the voter list PDF");
        }
    }

    private void heading(Document document, Request request) throws DocumentException {
        Font titleFont = fonts.latin(14, INK, true);
        Font metaFont = fonts.latin(9, MUTED, false);

        Paragraph title = new Paragraph(request.boothName() + " — voter list", titleFont);
        title.setSpacingAfter(2);
        document.add(title);

        document.add(new Paragraph(request.boothPath(), metaFont));
        document.add(new Paragraph(
                "Candidate %s · %s · %d voter(s)"
                        .formatted(
                                request.candidateName(),
                                SentimentFilter.describe(request.chosen()),
                                request.rows().size()),
                metaFont));
        document.add(new Paragraph(" ", metaFont));
    }

    /** One page of rows, numbered from {@code firstSerial} so the count runs through the file. */
    private PdfPTable table(Shaper shaper, List<ExportVoterRow> rows, int firstSerial, boolean showBooth)
            throws DocumentException {

        String[] headers = showBooth ? HEADERS_WITH_BOOTH : HEADERS;
        PdfPTable table = new PdfPTable(headers.length);
        table.setWidthPercentage(100);
        table.setWidths(showBooth ? WIDTHS_WITH_BOOTH : WIDTHS);
        table.setHeaderRows(1);

        Font headFont = fonts.latin(BODY_SIZE, INK, true);
        for (String header : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(header, headFont));
            cell.setBackgroundColor(PAPER);
            cell.setPadding(5);
            cell.setBorderColor(LINE);
            table.addCell(cell);
        }

        int serial = firstSerial;
        for (ExportVoterRow row : rows) {
            // Only the name and the relation can be Odia; the rest is digits and
            // fixed words, so they stay on the built-in font.
            cell(shaper, table, String.valueOf(serial++), Element.ALIGN_RIGHT);
            if (showBooth) {
                cell(shaper, table, row.boothName() == null ? "" : row.boothName(), Element.ALIGN_LEFT);
            }
            cell(shaper, table, row.name(), Element.ALIGN_LEFT);
            cell(shaper, table, row.relation(), Element.ALIGN_LEFT);
            cell(shaper, table, String.valueOf(row.age()), Element.ALIGN_RIGHT);
            cell(shaper, table, row.gender() == null ? "" : row.gender().name(), Element.ALIGN_CENTER);
            cell(shaper, table, row.houseNo(), Element.ALIGN_LEFT);
            cell(shaper, table, row.sentimentOrNotRecorded().label(), Element.ALIGN_LEFT);
            cell(shaper, table, row.recordedAt() == null ? "" : DAY.format(row.recordedAt()), Element.ALIGN_LEFT);
        }
        return table;
    }

    /**
     * Latin text is written as text, so it stays selectable and searchable in
     * the file. Odia is drawn as shaped outlines instead - see {@link
     * PdfFontSource} for why writing it as text would print it wrongly.
     */
    private void cell(Shaper shaper, PdfPTable table, String text, int alignment) {
        String value = text == null ? "" : text;
        boolean shaped = PdfFontSource.isOdia(value) && fonts.hasOdia();
        PdfPCell cell = shaped
                ? new PdfPCell(shaper.run(value), false)
                : new PdfPCell(new Phrase(value, fonts.latin(BODY_SIZE, INK, false)));
        cell.setPadding(4);
        cell.setHorizontalAlignment(alignment);
        cell.setBorderColor(LINE);
        table.addCell(cell);
    }

    /**
     * Draws Odia runs, one file's worth.
     *
     * <p>Each distinct glyph's outline is written into the file once and then
     * referenced wherever it occurs. Drawing every name's outlines in full
     * instead also works, but a long roll then carries the same few hundred
     * letter shapes over and over - measured at a full-length file, 34 MB and
     * ten seconds against a fraction of that this way. Glyphs are only reusable
     * within one file, so the cache lives for one build and no longer.
     */
    private final class Shaper {

        /** Room around a glyph that paints outside its own advance, as many Odia marks do. */
        private static final float BLEED = BODY_SIZE;

        private final PdfWriter writer;
        private final Map<Integer, PdfTemplate> glyphs = new HashMap<>();
        private final float ascent = fonts.ascent(BODY_SIZE);
        private final float height = fonts.ascent(BODY_SIZE) + fonts.descent(BODY_SIZE);
        private final float box = height + 2 * BLEED;

        private Shaper(PdfWriter writer) {
            this.writer = writer;
        }

        /** One cell's text, laid out and then placed glyph by glyph. */
        Image run(String text) {
            GlyphVector shaped = fonts.shape(text, BODY_SIZE);
            float width = Math.max((float) shaped.getLogicalBounds().getWidth(), 1f);

            PdfTemplate run = writer.getDirectContent().createTemplate(width, height);
            for (int i = 0; i < shaped.getNumGlyphs(); i++) {
                PdfTemplate glyph = glyph(shaped.getGlyphCode(i));
                Point2D at = shaped.getGlyphPosition(i);
                // Each glyph box holds its own baseline a fixed bleed in from the
                // left and ascent+bleed down from the top, so placing it is a
                // matter of stepping back by exactly that.
                run.addTemplate(
                        glyph,
                        (float) at.getX() - BLEED,
                        ascent - (float) at.getY() - (ascent + BLEED));
            }
            return Image.getInstance(run);
        }

        /** One glyph, drawn from its own origin into a box that leaves room around it. */
        private PdfTemplate glyph(int code) {
            return glyphs.computeIfAbsent(code, unused -> {
                Shape outline = fonts.glyphOutline(code, BODY_SIZE);
                PdfTemplate template = writer.getDirectContent().createTemplate(box, box);
                Graphics2D canvas = template.createGraphicsShapes(box, box);
                try {
                    canvas.setColor(INK);
                    canvas.translate(BLEED, ascent + BLEED);
                    canvas.fill(outline);
                } finally {
                    canvas.dispose();
                }
                return template;
            });
        }
    }

    /**
     * The part that makes a leaked copy traceable: a diagonal watermark under the
     * rows and a footer line over them, written on every page including the last.
     * "Page x of y" needs a total nobody knows until the document closes, so the
     * total is left as a placeholder and filled in at the end.
     */
    private final class Stamp extends PdfPageEventHelper {

        private static final float TOTAL_WIDTH = 26;

        private final Request request;
        private final String mark;
        private PdfTemplate total;

        private Stamp(Request request) {
            this.request = request;
            this.mark = "%s · %s".formatted(request.agentName(), STAMP.format(request.downloadedAt()));
        }

        @Override
        public void onOpenDocument(PdfWriter writer, Document document) {
            total = writer.getDirectContent().createTemplate(TOTAL_WIDTH, 10);
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            watermark(writer, document);
            footer(writer, document);
        }

        @Override
        public void onCloseDocument(PdfWriter writer, Document document) {
            total.beginText();
            total.setFontAndSize(fonts.base(), 7.5f);
            total.setColorFill(MUTED);
            // By now the writer has moved past the last page, so the count is one behind it.
            total.showText(String.valueOf(writer.getPageNumber() - 1));
            total.endText();
        }

        private void watermark(PdfWriter writer, Document document) {
            Rectangle page = document.getPageSize();
            PdfContentByte canvas = writer.getDirectContentUnder();

            canvas.saveState();
            PdfGState faint = new PdfGState();
            faint.setFillOpacity(0.10f);
            canvas.setGState(faint);
            canvas.beginText();
            canvas.setFontAndSize(fonts.base(), 20);
            canvas.setColorFill(INK);
            // Three passes up the page, so no part of a sheet is unmarked.
            for (float fraction : new float[] {0.22f, 0.5f, 0.78f}) {
                canvas.showTextAligned(
                        Element.ALIGN_CENTER, mark, page.getWidth() / 2, page.getHeight() * fraction, 30);
            }
            canvas.endText();
            canvas.restoreState();
        }

        private void footer(PdfWriter writer, Document document) {
            Rectangle page = document.getPageSize();
            PdfContentByte canvas = writer.getDirectContent();
            float y = 24;
            float totalAt = page.getWidth() - document.rightMargin() - TOTAL_WIDTH;

            canvas.beginText();
            canvas.setFontAndSize(fonts.base(), 7.5f);
            canvas.setColorFill(MUTED);
            canvas.showTextAligned(
                    Element.ALIGN_LEFT,
                    "Downloaded by %s · %s · Not for sharing"
                            .formatted(request.agentName(), STAMP.format(request.downloadedAt())),
                    document.leftMargin(),
                    y,
                    0);
            canvas.showTextAligned(Element.ALIGN_RIGHT, "Page " + writer.getPageNumber() + " of ", totalAt, y, 0);
            canvas.endText();
            canvas.addTemplate(total, totalAt, y);
        }
    }
}
