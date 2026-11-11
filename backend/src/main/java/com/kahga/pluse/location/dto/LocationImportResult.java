package com.kahga.pluse.location.dto;

import com.kahga.pluse.location.entity.UnitLevel;
import java.util.List;

/**
 * What an upload did, or would have done when it was only a preview.
 *
 * <p>The breakdown is per level because that is how an admin checks the file
 * was the one they meant: a sheet reporting two districts and four thousand
 * panchayats looks right, one reporting four thousand districts does not.
 *
 * <p>Every figure is a record component rather than something worked out from
 * the others, because only components are serialised.
 */
public record LocationImportResult(
        boolean preview,
        int rowsRead,
        int created,
        int updated,
        int rowsSkipped,
        /** Units already in the database that this file did not mention. Never touched. */
        int notInFile,
        List<LevelCount> levels,
        List<String> problems) {

    public record LevelCount(UnitLevel level, int created, int updated) {}
}
