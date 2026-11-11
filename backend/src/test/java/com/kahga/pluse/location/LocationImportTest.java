package com.kahga.pluse.location;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kahga.pluse.common.exception.BusinessException;
import com.kahga.pluse.location.dto.LocationImportResult;
import com.kahga.pluse.location.entity.Unit;
import com.kahga.pluse.location.entity.UnitLevel;
import com.kahga.pluse.location.repository.UnitRepository;
import com.kahga.pluse.location.service.LocationImportService;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The location master arrives as two sheets, and the order they are loaded in
 * matters: the booth sheet names no district, so a booth cannot be placed until
 * its panchayat exists. These assert that, and that a second upload of the same
 * file changes nothing rather than duplicating the state.
 */
@SpringBootTest
@ActiveProfiles("test")
// Each method rolls back, so one upload cannot leave units behind that make the
// next method's counts depend on the order they happened to run in.
@Transactional
class LocationImportTest {

    private static final String HIERARCHY = """
            gp_lgd_code,gp_name,block_lgd_code,block_name,district_lgd_code,district_name
            900001,Panasganda,800001,Rajkanika,700001,Kendrapara
            900002,Padhanpada,800001,Rajkanika,700001,Kendrapara
            """;

    /** The real sheet's headings, year and language tag included. */
    private static final String BOOTHS = """
            PART No. New (2026),BLOCK / ULB,GRAM PANCHAYAT / WARD NO,PART Name (Eng.),PS Building Name and Address (Eng.)
            1,Rajkanika,Panasganda,Panasaganda,Panasaganda Nodal UP School
            3,Rajkanika,Panasganda,Damodarpur,P Damadorpur Pry. School
            5,Rajkanika,Padhanpada,Padhanpada,Padhanpada Pry. School
            """;

    @Autowired
    private LocationImportService locationImportService;

    @Autowired
    private UnitRepository unitRepository;

    @Test
    void theHierarchySheetBuildsDistrictBlockAndPanchayat() {
        LocationImportResult result = importHierarchy(HIERARCHY, false);

        assertThat(result.rowsRead()).isEqualTo(2);
        assertThat(result.problems()).isEmpty();
        // Two rows share a district and a block, so those are created once.
        assertThat(count(result, UnitLevel.DISTRICT)).isEqualTo(1);
        assertThat(count(result, UnitLevel.BLOCK)).isEqualTo(1);
        assertThat(count(result, UnitLevel.PANCHAYAT)).isEqualTo(2);

        Unit panchayat = unitRepository.findByLevelAndCode(UnitLevel.PANCHAYAT, "900001").orElseThrow();
        assertThat(panchayat.getName()).isEqualTo("Panasganda");
        assertThat(panchayat.getPath()).isEqualTo("Kendrapara > Rajkanika > Panasganda");
    }

    /**
     * The whole reason the sheets have an order: a booth whose panchayat is
     * missing must be reported, never guessed into some other district.
     */
    @Test
    void theBoothSheetRefusesToGuessAMissingPanchayat() {
        LocationImportResult result = importBooths(BOOTHS.replace("Panasganda", "Nowhere"), false);

        assertThat(result.created()).isZero();
        assertThat(result.rowsSkipped()).isPositive();
        assertThat(result.problems()).anyMatch(p -> p.contains("Nowhere") && p.contains("district sheet first"));
    }

    @Test
    void theBoothSheetReadsPartNumberNameAndPollingStation() {
        importHierarchy(HIERARCHY, false);

        LocationImportResult result = importBooths(BOOTHS, false);

        assertThat(result.problems()).isEmpty();
        assertThat(count(result, UnitLevel.BOOTH)).isEqualTo(3);

        Unit booth = unitRepository.findByLevelAndCode(UnitLevel.BOOTH, "900001/3").orElseThrow();
        // Columns A and E: the part number and the station the agent is sent to.
        assertThat(booth.getName()).isEqualTo("3-P Damadorpur Pry. School");
        assertThat(booth.getPremises()).isEqualTo("P Damadorpur Pry. School");
        assertThat(booth.getPath())
                .isEqualTo("Kendrapara > Rajkanika > Panasganda > 3-P Damadorpur Pry. School");
    }

    /** A row with no station still has to produce a booth an admin can read. */
    @Test
    void aBoothWithNoPollingStationFallsBackToThePartName() {
        importHierarchy(HIERARCHY, false);

        importBooths(BOOTHS.replace(",P Damadorpur Pry. School", ","), false);

        Unit booth = unitRepository.findByLevelAndCode(UnitLevel.BOOTH, "900001/3").orElseThrow();
        assertThat(booth.getName()).isEqualTo("3-Damodarpur");
        assertThat(booth.getPremises()).isNull();
    }

    /** The column holds 120 characters, and a station carries its address too. */
    @Test
    void aLongStationNameIsCutToWhatTheColumnHolds() {
        importHierarchy(HIERARCHY, false);
        // No commas: the sheet is read as CSV here, and a station name with one
        // would be two fields rather than one long value.
        String long_ = "Panasaganda Nodal Upper Primary School at the far end of the village road past "
                + "the panchayat office and the weekly market in Rajkanika block";

        importBooths(BOOTHS.replace("Panasaganda Nodal UP School", long_), false);

        Unit booth = unitRepository.findByLevelAndCode(UnitLevel.BOOTH, "900001/1").orElseThrow();
        assertThat(booth.getName()).hasSize(120).startsWith("1-Panasaganda Nodal Upper");
        // Stored whole, so nothing of the address is lost.
        assertThat(booth.getPremises()).isEqualTo(long_);
    }

    /** Re-uploading the same file is how a revision arrives, so it must be a no-op. */
    @Test
    void uploadingTheSameSheetTwiceChangesNothingTheSecondTime() {
        importHierarchy(HIERARCHY, false);
        importBooths(BOOTHS, false);
        long before = unitRepository.count();

        LocationImportResult again = importBooths(BOOTHS, false);

        assertThat(again.created()).isZero();
        assertThat(again.updated()).isZero();
        assertThat(unitRepository.count()).isEqualTo(before);
    }

    /** A respelled name updates the unit it belongs to instead of adding a second. */
    @Test
    void aRenameInTheSheetMovesTheExistingUnitAndItsBooths() {
        importHierarchy(HIERARCHY, false);
        importBooths(BOOTHS, false);

        importHierarchy(HIERARCHY.replace("Panasganda", "Panasaganda"), false);

        assertThat(unitRepository.findByLevelAndCode(UnitLevel.PANCHAYAT, "900001").orElseThrow().getName())
                .isEqualTo("Panasaganda");
        // The booths beneath it keep their readable trail.
        assertThat(unitRepository.findByLevelAndCode(UnitLevel.BOOTH, "900001/3").orElseThrow().getPath())
                .isEqualTo("Kendrapara > Rajkanika > Panasaganda > 3-P Damadorpur Pry. School");
    }

    /**
     * Run outside the test's own transaction on purpose. A preview rolls its
     * transaction back, which is invisible from inside that same transaction, so
     * this suspends it and lets the import be the outermost one -- which is how
     * it runs in the app.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void aPreviewReportsWhatItWouldDoAndWritesNothing() {
        long before = unitRepository.count();

        LocationImportResult preview = importHierarchy(HIERARCHY, true);

        assertThat(preview.preview()).isTrue();
        assertThat(preview.created()).isPositive();
        assertThat(unitRepository.count()).isEqualTo(before);
    }

    @Test
    void aSheetMissingAColumnIsRefusedBeforeAnythingIsWritten() {
        long before = unitRepository.count();

        assertThatThrownBy(() -> importHierarchy("gp_name,block_name\nPanasganda,Rajkanika\n", false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("district_name");

        assertThat(unitRepository.count()).isEqualTo(before);
    }

    private int count(LocationImportResult result, UnitLevel level) {
        return result.levels().stream()
                .filter(l -> l.level() == level)
                .mapToInt(LocationImportResult.LevelCount::created)
                .sum();
    }

    private LocationImportResult importHierarchy(String csv, boolean preview) {
        return locationImportService.importHierarchy(file("hierarchy.csv", csv), preview);
    }

    private LocationImportResult importBooths(String csv, boolean preview) {
        return locationImportService.importBooths(file("booths.csv", csv), preview);
    }

    private MockMultipartFile file(String name, String csv) {
        return new MockMultipartFile("file", name, "text/csv", csv.getBytes(StandardCharsets.UTF_8));
    }
}
