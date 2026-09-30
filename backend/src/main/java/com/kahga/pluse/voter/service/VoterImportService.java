package com.kahga.pluse.voter.service;

import com.kahga.pluse.common.exception.BusinessException;
import com.kahga.pluse.location.entity.Unit;
import com.kahga.pluse.location.entity.UnitLevel;
import com.kahga.pluse.location.service.LocationService;
import com.kahga.pluse.user.entity.User;
import com.kahga.pluse.voter.entity.Gender;
import com.kahga.pluse.voter.entity.Voter;
import com.kahga.pluse.voter.entity.VoterUpload;
import com.kahga.pluse.voter.repository.VoterRepository;
import com.kahga.pluse.voter.repository.VoterUploadRepository;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Bulk roll import (FR-A6). The admin prepares the file — often out of the
 * electoral-roll OCR pipeline — as .csv, .xlsx or .xls, and we read it by header
 * name so column order does not matter.
 *
 * <p>Headers follow the downloadable template: house_no, Name, Relation,
 * Relation_Name, Age, Gender, Assembly Part, Epic_no, Ward_No. Matching ignores
 * case, spaces and dots, and the older single-column spellings still work. A
 * roll covering several booths needs a booth column as well.
 *
 * <p>Relation and Relation_Name are stored as one line ("W/O Ranjit Nayak"),
 * which is how the roll prints it and how every screen shows it. Assembly Part
 * is accepted so the sheet imports unchanged, but there is nowhere to keep it,
 * so it is not stored.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VoterImportService {

    private static final DataFormatter FORMATTER = new DataFormatter();

    /** Odia rolls spell gender out; anything else falls back to the first letter. */
    private static final Map<String, Gender> GENDER_WORDS = Map.of(
            "ପୁରୁଷ", Gender.M,
            "ପୁ", Gender.M,
            "ମହିଳା", Gender.F,
            "ସ୍ତ୍ରୀ", Gender.F,
            "ମ", Gender.F);

    private final VoterRepository voterRepository;
    private final VoterUploadRepository voterUploadRepository;
    private final LocationService locationService;

    @Transactional
    public VoterUpload importWorkbook(MultipartFile file, UUID unitId, User uploadedBy) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("Choose a .csv or .xlsx file to upload");
        }
        String fileName = file.getOriginalFilename() == null ? "upload.csv" : file.getOriginalFilename();
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".csv") && !lower.endsWith(".xlsx") && !lower.endsWith(".xls")) {
            throw new BusinessException("Upload the roll as .csv, .xlsx or .xls");
        }

        Unit target = locationService.require(unitId);
        Map<String, Unit> boothsByName = new HashMap<>();
        Unit singleBooth = null;
        if (target.getLevel() == UnitLevel.BOOTH) {
            singleBooth = target;
        } else {
            for (UUID boothId : locationService.boothIdsUnder(unitId)) {
                Unit booth = locationService.require(boothId);
                boothsByName.put(booth.getName().toLowerCase(Locale.ROOT), booth);
            }
            if (boothsByName.isEmpty()) {
                throw new BusinessException("That unit has no booths under it yet");
            }
        }

        List<List<String>> table = lower.endsWith(".csv") ? readCsv(file, fileName) : readWorkbook(file, fileName);
        if (table.isEmpty()) {
            throw new BusinessException("That file has no header row");
        }

        Map<String, Integer> columns = readHeader(table.get(0));
        requireColumn(columns, "name");

        List<Voter> parsed = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        Set<String> epicsInFile = new HashSet<>();

        for (int i = 1; i < table.size(); i++) {
            List<String> row = table.get(i);
            if (isBlank(row, columns)) {
                continue;
            }
            int rowNumber = i + 1;
            String name = value(row, columns, "name");
            if (name.isBlank()) {
                problems.add("row " + rowNumber + ": missing name");
                continue;
            }

            Unit booth = singleBooth;
            if (booth == null) {
                String boothName = value(row, columns, "booth");
                booth = boothsByName.get(boothName.toLowerCase(Locale.ROOT));
                if (booth == null) {
                    problems.add("row " + rowNumber + ": unknown booth '" + boothName + "'");
                    continue;
                }
            }

            String epicNo = value(row, columns, "epic_no").trim().toUpperCase(Locale.ROOT);
            if (epicNo.isBlank()) {
                epicNo = "TMP" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
            }
            if (!epicsInFile.add(epicNo) || voterRepository.existsByEpicNoIgnoreCase(epicNo)) {
                problems.add("row " + rowNumber + ": duplicate EPIC no. " + epicNo);
                continue;
            }

            parsed.add(Voter.builder()
                    .id(UUID.randomUUID())
                    .epicNo(epicNo)
                    .name(name.trim())
                    .relation(relation(row, columns))
                    .houseNo(value(row, columns, "house_no"))
                    .age(parseInt(value(row, columns, "age"), 18))
                    .gender(parseGender(value(row, columns, "gender")))
                    .booth(booth)
                    .wardNo(optionalInt(value(row, columns, "ward_no")))
                    .createdAt(Instant.now())
                    .build());
        }

        if (parsed.isEmpty()) {
            throw new BusinessException(
                    problems.isEmpty() ? "No voter rows found in that file" : "Nothing imported — " + summary(problems));
        }

        voterRepository.insertAll(parsed);

        VoterUpload upload = VoterUpload.builder()
                .id(UUID.randomUUID())
                .fileName(fileName)
                .unit(target)
                .rowCount(parsed.size())
                .status("PROCESSED")
                .message(problems.isEmpty() ? null : problems.size() + " row(s) skipped — " + summary(problems))
                .uploadedBy(uploadedBy)
                .uploadedAt(Instant.now())
                .build();
        return voterUploadRepository.save(upload);
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

    private String summary(List<String> problems) {
        return String.join("; ", problems.subList(0, Math.min(3, problems.size())));
    }

    private Map<String, Integer> readHeader(List<String> header) {
        Map<String, Integer> columns = new HashMap<>();
        for (int i = 0; i < header.size(); i++) {
            String key = header.get(i).trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace(".", "");
            if (!key.isBlank()) {
                columns.put(key, i);
            }
        }
        // Tolerate the common alternative spellings without asking the admin to re-edit the file.
        alias(columns, "epic", "epic_no");
        alias(columns, "epic_number", "epic_no");
        alias(columns, "voter_name", "name");
        alias(columns, "father/husband", "relation");
        alias(columns, "relation_type", "relation");
        alias(columns, "guardian", "relation_name");
        alias(columns, "guardian_name", "relation_name");
        alias(columns, "father/husband_name", "relation_name");
        alias(columns, "house", "house_no");
        alias(columns, "ward", "ward_no");
        alias(columns, "booth_no", "booth");
        alias(columns, "booth_name", "booth");
        return columns;
    }

    private void alias(Map<String, Integer> columns, String from, String to) {
        if (columns.containsKey(from) && !columns.containsKey(to)) {
            columns.put(to, columns.get(from));
        }
    }

    private void requireColumn(Map<String, Integer> columns, String key) {
        if (!columns.containsKey(key)) {
            throw new BusinessException("The file needs a '" + key + "' column");
        }
    }

    /** "W/O" plus "Ranjit Nayak" becomes the single line the roll prints and every screen shows. */
    private String relation(List<String> row, Map<String, Integer> columns) {
        String type = value(row, columns, "relation");
        String person = value(row, columns, "relation_name");
        if (person.isBlank()) {
            return type;
        }
        return type.isBlank() ? person : type + " " + person;
    }

    private boolean isBlank(List<String> row, Map<String, Integer> columns) {
        return value(row, columns, "name").isBlank() && value(row, columns, "epic_no").isBlank();
    }

    private String value(List<String> row, Map<String, Integer> columns, String key) {
        Integer index = columns.get(key);
        if (index == null || index >= row.size()) {
            return "";
        }
        String cell = row.get(index);
        return cell == null ? "" : cell.trim();
    }

    private int parseInt(String raw, int fallback) {
        Integer parsed = optionalInt(raw);
        return parsed == null ? fallback : parsed;
    }

    private Integer optionalInt(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return (int) Double.parseDouble(raw.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Gender parseGender(String raw) {
        if (raw == null || raw.isBlank()) {
            return Gender.OTHER;
        }
        String clean = raw.trim();
        Gender word = GENDER_WORDS.get(clean);
        if (word != null) {
            return word;
        }
        return switch (clean.toUpperCase(Locale.ROOT).charAt(0)) {
            case 'M' -> Gender.M;
            case 'F' -> Gender.F;
            default -> Gender.OTHER;
        };
    }
}
