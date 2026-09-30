package com.kahga.pluse.report.service;

import com.kahga.pluse.common.exception.BusinessException;
import com.kahga.pluse.report.dto.ReportFilterRequest;
import com.kahga.pluse.report.dto.ReportResponse;
import com.kahga.pluse.report.dto.SentimentSummaryDto;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class PdfReportGenerator {

    private static final String[] HEADERS = {
        "Unit", "Positive", "Neutral", "Negative", "Recorded", "Not recorded", "Voters"
    };

    private static final Color INK = new Color(0x15, 0x2A, 0x38);
    private static final Color PAPER = new Color(0xF3, 0xEE, 0xE3);

    private static final DateTimeFormatter GENERATED =
            DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm").withZone(ZoneId.of("Asia/Kolkata"));

    public byte[] generate(ReportResponse report, ReportFilterRequest filter) {
        Document document = new Document(PageSize.A4, 36, 36, 42, 36);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfWriter.getInstance(document, out);
            document.open();

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, INK);
            Font metaFont = FontFactory.getFont(FontFactory.HELVETICA, 9, new Color(0x4A, 0x5A, 0x66));

            Paragraph title = new Paragraph("K-Pulse — voter sentiment", titleFont);
            title.setSpacingAfter(4);
            document.add(title);

            document.add(new Paragraph(ReportLabels.describe(filter), metaFont));
            document.add(new Paragraph("Generated " + GENERATED.format(Instant.now()), metaFont));
            document.add(new Paragraph(" "));

            PdfPTable table = new PdfPTable(HEADERS.length);
            table.setWidthPercentage(100);
            table.setWidths(new float[] {3f, 1.3f, 1.3f, 1.3f, 1.4f, 1.6f, 1.3f});

            Font headFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, INK);
            for (String header : HEADERS) {
                PdfPCell cell = new PdfPCell(new Phrase(header, headFont));
                cell.setBackgroundColor(PAPER);
                cell.setPadding(6);
                cell.setBorderColor(new Color(0xDA, 0xD2, 0xBE));
                table.addCell(cell);
            }

            Font bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 9, INK);
            for (SentimentSummaryDto row : report.rows()) {
                long recorded = row.positive() + row.neutral() + row.negative();
                addCell(table, row.unitName(), bodyFont, Element.ALIGN_LEFT);
                addCell(table, String.valueOf(row.positive()), bodyFont, Element.ALIGN_RIGHT);
                addCell(table, String.valueOf(row.neutral()), bodyFont, Element.ALIGN_RIGHT);
                addCell(table, String.valueOf(row.negative()), bodyFont, Element.ALIGN_RIGHT);
                addCell(table, String.valueOf(recorded), bodyFont, Element.ALIGN_RIGHT);
                addCell(table, String.valueOf(row.notRecorded()), bodyFont, Element.ALIGN_RIGHT);
                addCell(table, String.valueOf(row.total()), bodyFont, Element.ALIGN_RIGHT);
            }

            document.add(table);
            document.add(new Paragraph(" "));
            document.add(new Paragraph(
                    "Confidence — high %d · medium %d · low %d"
                            .formatted(
                                    report.confidence().high(),
                                    report.confidence().medium(),
                                    report.confidence().low()),
                    metaFont));

            document.close();
            return out.toByteArray();
        } catch (DocumentException | java.io.IOException ex) {
            throw new BusinessException("Could not build the PDF report");
        }
    }

    private void addCell(PdfPTable table, String text, Font font, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setPadding(5);
        cell.setHorizontalAlignment(alignment);
        cell.setBorderColor(new Color(0xDA, 0xD2, 0xBE));
        table.addCell(cell);
    }
}
