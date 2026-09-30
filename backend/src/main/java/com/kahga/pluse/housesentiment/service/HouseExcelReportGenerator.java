package com.kahga.pluse.housesentiment.service;

import com.kahga.pluse.common.exception.BusinessException;
import com.kahga.pluse.housesentiment.dto.HouseEntryRowDto;
import com.kahga.pluse.housesentiment.dto.HouseReportFilter;
import com.kahga.pluse.housesentiment.dto.HouseReportResponse;
import com.kahga.pluse.housesentiment.dto.HouseUnitSummaryDto;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

/** The pre-election report as a spreadsheet — households, not roll entries. */
@Component
public class HouseExcelReportGenerator {

    private static final String[] HEADERS = {
        "Unit", "Level", "Houses", "People", "Residents", "Positive", "Neutral", "Negative", "Positive %"
    };

    private static final String[] ENTRY_HEADERS = {
        "House no.", "House name", "Booth", "Ward", "People", "Residents",
        "Positive", "Neutral", "Negative", "Confidence", "Recorded by", "Updated"
    };

    /** One unit's houses, row by row — the drill-down behind a report line. */
    public byte[] generateEntries(List<HouseEntryRowDto> houses, String unitName, HouseReportFilter filter) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Houses");

            Font bold = workbook.createFont();
            bold.setBold(true);
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(bold);

            Row header = sheet.createRow(0);
            for (int i = 0; i < ENTRY_HEADERS.length; i++) {
                var cell = header.createCell(i);
                cell.setCellValue(ENTRY_HEADERS[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowIndex = 1;
            for (HouseEntryRowDto house : houses) {
                Row sheetRow = sheet.createRow(rowIndex++);
                sheetRow.createCell(0).setCellValue(house.houseNo());
                sheetRow.createCell(1).setCellValue(house.houseName());
                sheetRow.createCell(2).setCellValue(house.boothName());
                if (house.wardNo() != null) {
                    sheetRow.createCell(3).setCellValue(house.wardNo());
                }
                sheetRow.createCell(4).setCellValue(house.headcount());
                sheetRow.createCell(5).setCellValue(house.residentialCount());
                sheetRow.createCell(6).setCellValue(house.positiveCount());
                sheetRow.createCell(7).setCellValue(house.neutralCount());
                sheetRow.createCell(8).setCellValue(house.negativeCount());
                sheetRow.createCell(9).setCellValue(house.confidence().name());
                sheetRow.createCell(10).setCellValue(house.recordedByName());
                sheetRow.createCell(11).setCellValue(house.updatedAt().toString());
            }

            Row meta = sheet.createRow(rowIndex + 1);
            meta.createCell(0).setCellValue("Unit");
            meta.createCell(1).setCellValue(unitName);
            Row filters = sheet.createRow(rowIndex + 2);
            filters.createCell(0).setCellValue("Filters");
            filters.createCell(1).setCellValue(HouseReportLabels.describe(filter));

            for (int i = 0; i < ENTRY_HEADERS.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new BusinessException("Could not build the Excel report");
        }
    }

    public byte[] generate(HouseReportResponse report, HouseReportFilter filter) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Pre-election");

            Font bold = workbook.createFont();
            bold.setBold(true);
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(bold);

            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                var cell = header.createCell(i);
                cell.setCellValue(HEADERS[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowIndex = 1;
            for (HouseUnitSummaryDto row : report.rows()) {
                Row sheetRow = sheet.createRow(rowIndex++);
                sheetRow.createCell(0).setCellValue(row.unitName());
                sheetRow.createCell(1).setCellValue(row.unitLevel().name());
                sheetRow.createCell(2).setCellValue(row.houses());
                sheetRow.createCell(3).setCellValue(row.people());
                sheetRow.createCell(4).setCellValue(row.residents());
                sheetRow.createCell(5).setCellValue(row.positive());
                sheetRow.createCell(6).setCellValue(row.neutral());
                sheetRow.createCell(7).setCellValue(row.negative());
                // Of residents: people counted but living elsewhere are not part
                // of the breakdown.
                sheetRow.createCell(8)
                        .setCellValue(row.residents() == 0 ? 0 : Math.round(row.positive() * 100.0 / row.residents()));
            }

            Row totals = sheet.createRow(rowIndex + 1);
            totals.createCell(0).setCellValue("All units");
            totals.createCell(2).setCellValue(report.totals().houses());
            totals.createCell(3).setCellValue(report.totals().people());
            totals.createCell(4).setCellValue(report.totals().residents());
            totals.createCell(5).setCellValue(report.totals().positive());
            totals.createCell(6).setCellValue(report.totals().neutral());
            totals.createCell(7).setCellValue(report.totals().negative());

            Row spacer = sheet.createRow(rowIndex + 3);
            spacer.createCell(0).setCellValue("Confidence");
            Row high = sheet.createRow(rowIndex + 4);
            high.createCell(0).setCellValue("High");
            high.createCell(1).setCellValue(report.totals().confidence().high());
            Row medium = sheet.createRow(rowIndex + 5);
            medium.createCell(0).setCellValue("Medium");
            medium.createCell(1).setCellValue(report.totals().confidence().medium());
            Row low = sheet.createRow(rowIndex + 6);
            low.createCell(0).setCellValue("Low");
            low.createCell(1).setCellValue(report.totals().confidence().low());

            Row meta = sheet.createRow(rowIndex + 8);
            meta.createCell(0).setCellValue("Filters");
            meta.createCell(1).setCellValue(HouseReportLabels.describe(filter));

            for (int i = 0; i < HEADERS.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new BusinessException("Could not build the Excel report");
        }
    }
}
