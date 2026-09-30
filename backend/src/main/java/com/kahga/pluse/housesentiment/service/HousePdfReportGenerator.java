package com.kahga.pluse.housesentiment.service;

import com.kahga.pluse.common.exception.BusinessException;
import com.kahga.pluse.housesentiment.dto.HouseEntryRowDto;
import com.kahga.pluse.housesentiment.dto.HouseReportFilter;
import com.kahga.pluse.housesentiment.dto.HouseReportResponse;
import com.kahga.pluse.housesentiment.dto.HouseUnitSummaryDto;
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
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.stereotype.Component;

/** The pre-election report as a PDF, laid out like the named-voter one. */
@Component
public class HousePdfReportGenerator {

    private static final String[] HEADERS = {
        "Unit", "Houses", "People", "Residents", "Positive", "Neutral", "Negative"
    };

    private static final Color INK = new Color(0x15, 0x2A, 0x38);
    private static final Color PAPER = new Color(0xF3, 0xEE, 0xE3);
    private static final Color LINE = new Color(0xDA, 0xD2, 0xBE);

    private static final DateTimeFormatter GENERATED =
            DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm").withZone(ZoneId.of("Asia/Kolkata"));

    private static final String[] ENTRY_HEADERS = {
        "House no.", "House name", "Booth", "Ward", "People", "Resid.", "Pos.", "Neu.", "Neg.", "Conf."
    };

    /** One unit's houses, row by row — the drill-down behind a report line. */
    public byte[] generateEntries(List<HouseEntryRowDto> houses, String unitName, HouseReportFilter filter) {
        Document document = new Document(PageSize.A4.rotate(), 36, 36, 42, 36);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfWriter.getInstance(document, out);
            document.open();

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, INK);
            Font metaFont = FontFactory.getFont(FontFactory.HELVETICA, 9, new Color(0x4A, 0x5A, 0x66));

            Paragraph title = new Paragraph("K-Pulse — houses recorded in " + unitName, titleFont);
            title.setSpacingAfter(4);
            document.add(title);
            document.add(new Paragraph(HouseReportLabels.describe(filter), metaFont));
            document.add(new Paragraph("Generated " + GENERATED.format(Instant.now()), metaFont));
            document.add(new Paragraph(" "));

            PdfPTable table = new PdfPTable(ENTRY_HEADERS.length);
            table.setWidthPercentage(100);
            table.setWidths(new float[] {1.4f, 3f, 1.8f, 1f, 1.1f, 1.2f, 1f, 1f, 1f, 1.2f});

            Font headFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, INK);
            for (String header : ENTRY_HEADERS) {
                PdfPCell cell = new PdfPCell(new Phrase(header, headFont));
                cell.setBackgroundColor(PAPER);
                cell.setPadding(6);
                cell.setBorderColor(LINE);
                table.addCell(cell);
            }

            Font bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 9, INK);
            long people = 0;
            long residents = 0;
            for (HouseEntryRowDto house : houses) {
                addCell(table, house.houseNo(), bodyFont, Element.ALIGN_LEFT);
                addCell(table, house.houseName(), bodyFont, Element.ALIGN_LEFT);
                addCell(table, house.boothName(), bodyFont, Element.ALIGN_LEFT);
                addCell(table, house.wardNo() == null ? "-" : String.valueOf(house.wardNo()), bodyFont, Element.ALIGN_RIGHT);
                addCell(table, String.valueOf(house.headcount()), bodyFont, Element.ALIGN_RIGHT);
                addCell(table, String.valueOf(house.residentialCount()), bodyFont, Element.ALIGN_RIGHT);
                addCell(table, String.valueOf(house.positiveCount()), bodyFont, Element.ALIGN_RIGHT);
                addCell(table, String.valueOf(house.neutralCount()), bodyFont, Element.ALIGN_RIGHT);
                addCell(table, String.valueOf(house.negativeCount()), bodyFont, Element.ALIGN_RIGHT);
                addCell(table, house.confidence().name().charAt(0) + house.confidence().name().substring(1).toLowerCase(), bodyFont, Element.ALIGN_LEFT);
                people += house.headcount();
                residents += house.residentialCount();
            }

            document.add(table);
            document.add(new Paragraph(" "));
            document.add(new Paragraph(
                    "%d house(s) · %d people · %d resident(s)".formatted(houses.size(), people, residents),
                    metaFont));
            document.add(new Paragraph(
                    "Households, not named voters — no individual name, age, gender or EPIC no. is held for anyone "
                            + "in these houses. Sentiment covers residents only.",
                    metaFont));

            document.close();
            return out.toByteArray();
        } catch (DocumentException | java.io.IOException ex) {
            throw new BusinessException("Could not build the PDF report");
        }
    }

    public byte[] generate(HouseReportResponse report, HouseReportFilter filter) {
        Document document = new Document(PageSize.A4, 36, 36, 42, 36);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfWriter.getInstance(document, out);
            document.open();

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, INK);
            Font metaFont = FontFactory.getFont(FontFactory.HELVETICA, 9, new Color(0x4A, 0x5A, 0x66));

            Paragraph title = new Paragraph("K-Pulse — pre-election sentiment", titleFont);
            title.setSpacingAfter(4);
            document.add(title);

            document.add(new Paragraph(HouseReportLabels.describe(filter), metaFont));
            document.add(new Paragraph("Generated " + GENERATED.format(Instant.now()), metaFont));
            document.add(new Paragraph(" "));

            PdfPTable table = new PdfPTable(HEADERS.length);
            table.setWidthPercentage(100);
            table.setWidths(new float[] {3f, 1.3f, 1.3f, 1.4f, 1.3f, 1.3f, 1.3f});

            Font headFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, INK);
            for (String header : HEADERS) {
                PdfPCell cell = new PdfPCell(new Phrase(header, headFont));
                cell.setBackgroundColor(PAPER);
                cell.setPadding(6);
                cell.setBorderColor(LINE);
                table.addCell(cell);
            }

            Font bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 9, INK);
            for (HouseUnitSummaryDto row : report.rows()) {
                addCell(table, row.unitName(), bodyFont, Element.ALIGN_LEFT);
                addCell(table, String.valueOf(row.houses()), bodyFont, Element.ALIGN_RIGHT);
                addCell(table, String.valueOf(row.people()), bodyFont, Element.ALIGN_RIGHT);
                addCell(table, String.valueOf(row.residents()), bodyFont, Element.ALIGN_RIGHT);
                addCell(table, String.valueOf(row.positive()), bodyFont, Element.ALIGN_RIGHT);
                addCell(table, String.valueOf(row.neutral()), bodyFont, Element.ALIGN_RIGHT);
                addCell(table, String.valueOf(row.negative()), bodyFont, Element.ALIGN_RIGHT);
            }

            Font totalFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, INK);
            addCell(table, "All units", totalFont, Element.ALIGN_LEFT);
            addCell(table, String.valueOf(report.totals().houses()), totalFont, Element.ALIGN_RIGHT);
            addCell(table, String.valueOf(report.totals().people()), totalFont, Element.ALIGN_RIGHT);
            addCell(table, String.valueOf(report.totals().residents()), totalFont, Element.ALIGN_RIGHT);
            addCell(table, String.valueOf(report.totals().positive()), totalFont, Element.ALIGN_RIGHT);
            addCell(table, String.valueOf(report.totals().neutral()), totalFont, Element.ALIGN_RIGHT);
            addCell(table, String.valueOf(report.totals().negative()), totalFont, Element.ALIGN_RIGHT);

            document.add(table);
            document.add(new Paragraph(" "));
            document.add(new Paragraph(
                    "Confidence — high %d · medium %d · low %d"
                            .formatted(
                                    report.totals().confidence().high(),
                                    report.totals().confidence().medium(),
                                    report.totals().confidence().low()),
                    metaFont));
            document.add(new Paragraph(
                    "Households, not named voters. Sentiment covers residents only; people counted at a house but "
                            + "living elsewhere are not part of the breakdown.",
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
        cell.setBorderColor(LINE);
        table.addCell(cell);
    }
}
