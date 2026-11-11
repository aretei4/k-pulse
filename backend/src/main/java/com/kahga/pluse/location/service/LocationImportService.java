package com.kahga.pluse.location.service;

import com.kahga.pluse.common.exception.BusinessException;
import com.kahga.pluse.common.sheet.SheetReader;
import com.kahga.pluse.location.dto.LocationImportResult;
import com.kahga.pluse.location.entity.Unit;
import com.kahga.pluse.location.entity.UnitLevel;
import com.kahga.pluse.location.repository.UnitRepository;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.web.multipart.MultipartFile;

/**
 * Loads the location master from the state's own sheets, which arrive as two
 * separate files:
 *
 * <ol>
 *   <li>district / block / gram panchayat, with an LGD code for each;
 *   <li>block / gram panchayat / booth, with the part number, the part name and
 *       the polling station building.
 * </ol>
 *
 * <p>The order matters and is enforced: the booth sheet carries no district, so
 * a booth can only be placed once its panchayat exists. Uploading the booth
 * sheet first reports the panchayats it cannot find rather than inventing them,
 * because guessing a parent here would put booths, and every voter under them,
 * in the wrong district.
 *
 * <p>A booth is named for its part number and its polling station -- columns A
 * and E of the state's sheet -- because the station is the building an agent is
 * sent to, while a part name is a village that several booths can share. The
 * part name only stands in where a row leaves the station blank.
 *
 * <p>A row is matched on its LGD code, never its name: names get respelled
 * between revisions, and matching on them would either duplicate a unit or
 * rename the wrong one. A unit uploaded before, under the same code, is updated
 * in place. Nothing is ever deleted — voters, grants and recorded sentiment all
 * point at these rows — so a unit missing from the sheet is only reported.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LocationImportService {

    /** A booth has no code of its own, so it is keyed within its panchayat. */
    private static final String BOOTH_CODE = "%s/%s";

    /** What {@code unit.name} holds. A station's name carries its address too. */
    private static final int NAME_LIMIT = 120;

    private final UnitRepository unitRepository;
    private final LocationService locationService;
    private final SheetReader sheetReader;

    /** Sheet one: district, block and gram panchayat, each with its LGD code. */
    @Transactional
    public LocationImportResult importHierarchy(MultipartFile file, boolean preview) {
        List<List<String>> table = sheetReader.read(file);
        if (table.isEmpty()) {
            throw new BusinessException("That file has no header row");
        }
        Map<String, Integer> columns = sheetReader.headerIndex(table.get(0));
        sheetReader.resolve(columns, "district_name", "district", "name");
        sheetReader.resolve(columns, "district_lgd_code", "district", "code");
        sheetReader.resolve(columns, "block_name", "block", "name");
        sheetReader.resolve(columns, "block_lgd_code", "block", "code");
        sheetReader.resolve(columns, "gp_name", "gp", "name");
        sheetReader.resolve(columns, "gp_lgd_code", "gp", "code");
        sheetReader.resolve(columns, "gp_name", "panchayat");
        sheetReader.requireColumn(columns, "district_name");
        sheetReader.requireColumn(columns, "block_name");
        sheetReader.requireColumn(columns, "gp_name");

        Index index = new Index(unitRepository.findAllByOrderByPathAsc());
        Tally tally = new Tally();
        List<String> problems = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        int rows = 0;

        for (int i = 1; i < table.size(); i++) {
            List<String> row = table.get(i);
            if (sheetReader.isBlank(row, columns)) {
                continue;
            }
            rows++;
            int line = i + 1;

            String districtName = sheetReader.value(row, columns, "district_name");
            String blockName = sheetReader.value(row, columns, "block_name");
            String gpName = sheetReader.value(row, columns, "gp_name");
            if (districtName.isBlank() || blockName.isBlank() || gpName.isBlank()) {
                problems.add("row " + line + ": district, block and gram panchayat are all required");
                tally.skipped++;
                continue;
            }

            Unit district = upsert(
                    index, tally, UnitLevel.DISTRICT,
                    sheetReader.value(row, columns, "district_lgd_code"), districtName, null, null);
            Unit block = upsert(
                    index, tally, UnitLevel.BLOCK,
                    sheetReader.value(row, columns, "block_lgd_code"), blockName, district, null);
            Unit panchayat = upsert(
                    index, tally, UnitLevel.PANCHAYAT,
                    sheetReader.value(row, columns, "gp_lgd_code"), gpName, block, null);

            seen.add(district.getId());
            seen.add(block.getId());
            seen.add(panchayat.getId());
        }

        return finish(preview, rows, tally, problems, index, seen,
                Set.of(UnitLevel.DISTRICT, UnitLevel.BLOCK, UnitLevel.PANCHAYAT));
    }

    /** Sheet two: the booths of each gram panchayat, with their polling station. */
    @Transactional
    public LocationImportResult importBooths(MultipartFile file, boolean preview) {
        List<List<String>> table = sheetReader.read(file);
        if (table.isEmpty()) {
            throw new BusinessException("That file has no header row");
        }
        Map<String, Integer> columns = sheetReader.headerIndex(table.get(0));
        // The year in "PART No. New (2026)" and the "(Eng.)" tag move between
        // revisions, so these match on the words that stay put.
        sheetReader.resolve(columns, "part_no", "part", "no");
        sheetReader.resolve(columns, "part_name", "part", "name");
        sheetReader.resolve(columns, "block_name", "block");
        sheetReader.resolve(columns, "gp_name", "panchayat");
        sheetReader.resolve(columns, "gp_name", "gp");
        sheetReader.resolve(columns, "premises", "building");
        sheetReader.resolve(columns, "premises", "ps_building");
        sheetReader.requireColumn(columns, "part_no");
        sheetReader.requireColumn(columns, "part_name");
        sheetReader.requireColumn(columns, "block_name");
        sheetReader.requireColumn(columns, "gp_name");

        Index index = new Index(unitRepository.findAllByOrderByPathAsc());
        Tally tally = new Tally();
        List<String> problems = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        int rows = 0;

        for (int i = 1; i < table.size(); i++) {
            List<String> row = table.get(i);
            if (sheetReader.isBlank(row, columns)) {
                continue;
            }
            rows++;
            int line = i + 1;

            String partNo = sheetReader.value(row, columns, "part_no");
            String partName = sheetReader.value(row, columns, "part_name");
            String blockName = sheetReader.value(row, columns, "block_name");
            String gpName = sheetReader.value(row, columns, "gp_name");
            if (partNo.isBlank() || partName.isBlank() || gpName.isBlank()) {
                problems.add("row " + line + ": part number, part name and gram panchayat are all required");
                tally.skipped++;
                continue;
            }

            Unit panchayat = index.panchayatUnder(blockName, gpName);
            if (panchayat == null) {
                problems.add("row " + line + ": no gram panchayat '" + gpName + "'"
                        + (blockName.isBlank() ? "" : " in block '" + blockName + "'")
                        + " — upload the district sheet first");
                tally.skipped++;
                continue;
            }

            // Part numbers repeat across the state, so the key is scoped to the
            // panchayat the booth belongs to.
            String code = BOOTH_CODE.formatted(
                    panchayat.getCode() == null ? panchayat.getId() : panchayat.getCode(), partNo);
            String premises = sheetReader.value(row, columns, "premises");
            String boothName = fit(partNo + "-" + (premises.isBlank() ? partName : premises));
            Unit booth = upsert(index, tally, UnitLevel.BOOTH, code, boothName, panchayat, premises);
            seen.add(booth.getId());
        }

        return finish(preview, rows, tally, problems, index, seen, Set.of(UnitLevel.BOOTH));
    }

    /**
     * Adds the unit or brings the one already under that code up to date.
     *
     * <p>A unit created by hand before any upload has no code. Rather than
     * leaving a duplicate beside it, one with a matching name under the same
     * parent is adopted and given the code from here on.
     */
    private Unit upsert(Index index, Tally tally, UnitLevel level, String code, String name, Unit parent, String premises) {
        String trimmed = name.trim();
        Unit existing = code == null || code.isBlank() ? null : index.byCode(level, code);
        if (existing == null) {
            existing = index.byName(level, parent, trimmed);
        }

        if (existing == null) {
            Unit unit = Unit.builder()
                    .id(UUID.randomUUID())
                    .level(level)
                    .name(trimmed)
                    .path(parent == null ? trimmed : parent.getPath() + " > " + trimmed)
                    .parent(parent)
                    .code(blankToNull(code))
                    .premises(blankToNull(premises))
                    .build();
            unitRepository.save(unit);
            index.add(unit);
            tally.created.merge(level, 1, Integer::sum);
            return unit;
        }

        boolean renamed = !existing.getName().equals(trimmed);
        boolean changed = renamed
                || !java.util.Objects.equals(existing.getCode(), blankToNull(code))
                || !java.util.Objects.equals(existing.getPremises(), blankToNull(premises));
        if (!changed) {
            return existing;
        }

        index.forget(existing);
        existing.setName(trimmed);
        existing.setCode(blankToNull(code));
        existing.setPremises(blankToNull(premises));
        existing.setPath(parent == null ? trimmed : parent.getPath() + " > " + trimmed);
        unitRepository.save(existing);
        index.add(existing);
        if (renamed) {
            // The readable trail of everything beneath it has to follow the rename.
            locationService.repathFrom(existing);
        }
        tally.updated.merge(level, 1, Integer::sum);
        return existing;
    }

    private LocationImportResult finish(
            boolean preview,
            int rows,
            Tally tally,
            List<String> problems,
            Index index,
            Set<UUID> seen,
            Set<UnitLevel> levels) {

        int notInFile = (int) index.all().stream()
                .filter(u -> levels.contains(u.getLevel()))
                .filter(u -> !seen.contains(u.getId()))
                .count();

        List<LocationImportResult.LevelCount> breakdown = levels.stream()
                .sorted()
                .map(level -> new LocationImportResult.LevelCount(
                        level, tally.created.getOrDefault(level, 0), tally.updated.getOrDefault(level, 0)))
                .toList();

        if (preview) {
            // The counts come from doing the work for real and then undoing it, so
            // what the preview reports is exactly what a commit would do.
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        }
        return new LocationImportResult(
                preview,
                rows,
                breakdown.stream().mapToInt(LocationImportResult.LevelCount::created).sum(),
                breakdown.stream().mapToInt(LocationImportResult.LevelCount::updated).sum(),
                tally.skipped,
                notInFile,
                breakdown,
                problems.size() > 200 ? problems.subList(0, 200) : problems);
    }

    /**
     * Cuts a name down to what the column holds. Plain truncation on purpose:
     * it gives the same answer every time, so re-uploading the sheet still
     * changes nothing rather than renaming the booth back and forth.
     */
    private static String fit(String name) {
        return name.length() <= NAME_LIMIT ? name : name.substring(0, NAME_LIMIT).trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String key(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static final class Tally {
        private final Map<UnitLevel, Integer> created = new HashMap<>();
        private final Map<UnitLevel, Integer> updated = new HashMap<>();
        private int skipped;
    }

    /**
     * The whole tree held in memory for the length of one upload. A full state
     * master runs to thousands of rows, each needing its parents looked up, so
     * reading the table once beats a query per row by a wide margin.
     */
    private static final class Index {

        private final List<Unit> units;
        private final Map<String, Unit> byCode = new HashMap<>();
        private final Map<String, Unit> byName = new HashMap<>();

        private Index(List<Unit> loaded) {
            units = new ArrayList<>(loaded);
            loaded.forEach(this::put);
        }

        private void put(Unit unit) {
            if (unit.getCode() != null) {
                byCode.put(unit.getLevel() + "/" + unit.getCode(), unit);
            }
            byName.put(nameKey(unit.getLevel(), parentId(unit), unit.getName()), unit);
        }

        private void add(Unit unit) {
            if (!units.contains(unit)) {
                units.add(unit);
            }
            put(unit);
        }

        private void forget(Unit unit) {
            byName.remove(nameKey(unit.getLevel(), parentId(unit), unit.getName()));
            if (unit.getCode() != null) {
                byCode.remove(unit.getLevel() + "/" + unit.getCode());
            }
        }

        private Unit byCode(UnitLevel level, String code) {
            return byCode.get(level + "/" + code);
        }

        private Unit byName(UnitLevel level, Unit parent, String name) {
            return byName.get(nameKey(level, parent == null ? null : parent.getId(), name));
        }

        /** A panchayat by name, narrowed by its block when the sheet names one. */
        private Unit panchayatUnder(String blockName, String gpName) {
            return units.stream()
                    .filter(u -> u.getLevel() == UnitLevel.PANCHAYAT)
                    .filter(u -> key(u.getName()).equals(key(gpName)))
                    .filter(u -> blockName.isBlank() || blockKeyOf(u).equals(key(blockName)))
                    .findFirst()
                    .orElse(null);
        }

        private String blockKeyOf(Unit panchayat) {
            return units.stream()
                    .filter(u -> u.getId().equals(parentId(panchayat)))
                    .map(u -> key(u.getName()))
                    .findFirst()
                    .orElse("");
        }

        private List<Unit> all() {
            return units;
        }

        private static UUID parentId(Unit unit) {
            return unit.getParent() == null ? null : unit.getParent().getId();
        }

        private static String nameKey(UnitLevel level, UUID parentId, String name) {
            return level + "/" + parentId + "/" + key(name);
        }
    }
}
