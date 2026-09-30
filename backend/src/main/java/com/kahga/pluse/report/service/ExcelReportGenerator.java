package com.kahga.pluse.report.service;

import com.kahga.pluse.common.exception.BusinessException;
import com.kahga.pluse.report.dto.ReportFilterRequest;
import com.kahga.pluse.report.dto.ReportResponse;
import com.kahga.pluse.report.dto.SentimentSummaryDto;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

@Component
public class ExcelReportGenerator {

    private static final String[] HEADERS = {
        "Unit", "Level", "Positive", "Neutral", "Negative", "Recorded", "Not recorded", "Voters", "Coverage %"
    };

    public byte[] generate(ReportResponse report, ReportFilterRequest filter) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Sentiment");

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
            for (SentimentSummaryDto row : report.rows()) {
                long recorded = row.positive() + row.neutral() + row.negative();
                Row sheetRow = sheet.createRow(rowIndex++);
                sheetRow.createCell(0).setCellValue(row.unitName());
                sheetRow.createCell(1).setCellValue(row.unitLevel().name());
                sheetRow.createCell(2).setCellValue(row.positive());
                sheetRow.createCell(3).setCellValue(row.neutral());
                sheetRow.createCell(4).setCellValue(row.negative());
                sheetRow.createCell(5).setCellValue(recorded);
                sheetRow.createCell(6).setCellValue(row.notRecorded());
                sheetRow.createCell(7).setCellValue(row.total());
                sheetRow.createCell(8).setCellValue(row.total() == 0 ? 0 : Math.round(recorded * 100.0 / row.total()));
            }

            Row spacer = sheet.createRow(rowIndex + 1);
            spacer.createCell(0).setCellValue("Confidence");
            Row confidence = sheet.createRow(rowIndex + 2);
            confidence.createCell(0).setCellValue("High");
            confidence.createCell(1).setCellValue(report.confidence().high());
            Row medium = sheet.createRow(rowIndex + 3);
            medium.createCell(0).setCellValue("Medium");
            medium.createCell(1).setCellValue(report.confidence().medium());
            Row low = sheet.createRow(rowIndex + 4);
            low.createCell(0).setCellValue("Low");
            low.createCell(1).setCellValue(report.confidence().low());

            Row meta = sheet.createRow(rowIndex + 6);
            meta.createCell(0).setCellValue("Filters");
            meta.createCell(1).setCellValue(ReportLabels.describe(filter));

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
