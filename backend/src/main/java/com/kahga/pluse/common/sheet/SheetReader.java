package com.kahga.pluse.common.sheet;

import com.kahga.pluse.common.exception.BusinessException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * Reads an uploaded .csv, .xlsx or .xls into rows of strings, and indexes the
 * header so callers can read a column by name rather than by position.
 *
 * <p>Shared by every bulk upload: the admin prepares these files by hand, so
 * column order, casing and the odd spelling vary from one to the next, and
 * there should be one place that copes with all of it.
 */
@Slf4j
@Component
public class SheetReader {

    private static final DataFormatter FORMATTER = new DataFormatter();

    public boolean looksLikeSheet(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        return lower.endsWith(".csv") || lower.endsWith(".xlsx") || lower.endsWith(".xls");
    }

    /** The whole file, header row included, as trimmed strings. */
    public List<List<String>> read(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("Choose a .csv or .xlsx file to upload");
        }
        String fileName = file.getOriginalFilename() == null ? "upload.csv" : file.getOriginalFilename();
        if (!looksLikeSheet(fileName)) {
            throw new BusinessException("Upload the sheet as .csv, .xlsx or .xls");
        }
        return fileName.toLowerCase(Locale.ROOT).endsWith(".csv") ? readCsv(file, fileName) : readWorkbook(file, fileName);
    }

    /** Header name -> column index, lowercased with spaces and dots folded away. */
    public Map<String, Integer> headerIndex(List<String> header) {
        Map<String, Integer> columns = new HashMap<>();
        for (int i = 0; i < header.size(); i++) {
            String key = header.get(i).trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace(".", "");
            if (!key.isBlank()) {
                columns.put(key, i);
            }
        }
        return columns;
    }

    /** Lets a caller accept an alternative spelling without re-editing the file. */
    public void alias(Map<String, Integer> columns, String from, String to) {
        if (columns.containsKey(from) && !columns.containsKey(to)) {
            columns.put(to, columns.get(from));
        }
    }

    /**
     * Points {@code canonical} at the first column whose header contains every
     * one of {@code words}.
     *
     * <p>State sheets head their columns "PART No. New (2026)" and "PS Building
     * Name and Address (Eng.)", where the year and the language tag move between
     * revisions. Matching on the words that do not move survives that, where a
     * list of exact spellings would need editing every year.
     */
    public void resolve(Map<String, Integer> columns, String canonical, String... words) {
        if (columns.containsKey(canonical)) {
            return;
        }
        columns.keySet().stream()
                .filter(key -> Arrays.stream(words).allMatch(key::contains))
                .min(Comparator.comparing(key -> columns.get(key)))
                .ifPresent(key -> columns.put(canonical, columns.get(key)));
    }

    public void requireColumn(Map<String, Integer> columns, String key) {
        if (!columns.containsKey(key)) {
            throw new BusinessException("The file needs a '" + key + "' column");
        }
    }

    public String value(List<String> row, Map<String, Integer> columns, String key) {
        Integer index = columns.get(key);
        if (index == null || index >= row.size()) {
            return "";
        }
        return row.get(index).trim();
    }

    public boolean isBlank(List<String> row, Map<String, Integer> columns) {
        return columns.values().stream().allMatch(i -> i >= row.size() || row.get(i).isBlank());
    }

    /** The first few problems, for a message an admin can act on. */
    public String summary(List<String> problems) {
        return String.join("; ", problems.subList(0, Math.min(3, problems.size())));
    }

    private List<List<String>> readWorkbook(MultipartFile file, String fileName) {
        List<List<String>> table = new ArrayList<>();
        try (InputStream in = file.getInputStream();
                Workbook workbook = WorkbookFactory.create(in)) {

            Sheet sheet = workbook.getSheetAt(0);
            Row header = sheet.getRow(sheet.getFirstRowNum());
            if (header == null) {
                return table;
            }
            int width = header.getLastCellNum();
            for (int i = header.getRowNum(); i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                List<String> cells = new ArrayList<>();
                for (int c = 0; c < width; c++) {
                    Cell cell = row == null ? null : row.getCell(c);
                    cells.add(cell == null ? "" : FORMATTER.formatCellValue(cell).trim());
                }
                table.add(cells);
            }
        } catch (IOException | RuntimeException ex) {
            log.error("Could not read uploaded workbook {}", fileName, ex);
            throw new BusinessException("That file could not be read as an Excel workbook");
        }
        return table;
    }

    /**
     * Minimal RFC 4180 reader: quoted fields, doubled quotes inside them, and
     * newlines within quotes. Read as UTF-8 so Odia names survive, with the byte
     * order mark Excel adds stripped from the first header.
     */
    private List<List<String>> readCsv(MultipartFile file, String fileName) {
        String text;
        try (InputStream in = file.getInputStream()) {
            text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            log.error("Could not read uploaded CSV {}", fileName, ex);
            throw new BusinessException("That file could not be read");
        }
        if (text.startsWith("﻿")) {
            text = text.substring(1);
        }

        List<List<String>> table = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;

        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (quoted) {
                if (ch == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        cell.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    cell.append(ch);
                }
                continue;
            }
            switch (ch) {
                case '"' -> quoted = true;
                case ',' -> {
                    row.add(cell.toString().trim());
                    cell.setLength(0);
                }
                case '\r' -> {
                    /* handled by the newline that follows */
                }
                case '\n' -> {
                    row.add(cell.toString().trim());
                    cell.setLength(0);
                    table.add(row);
                    row = new ArrayList<>();
                }
                default -> cell.append(ch);
            }
        }
        if (cell.length() > 0 || !row.isEmpty()) {
            row.add(cell.toString().trim());
            table.add(row);
        }
        return table;
    }
}
