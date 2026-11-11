package com.kahga.pluse.voterexport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.kahga.pluse.voter.entity.Gender;
import com.kahga.pluse.voterexport.dto.ExportVoterRow;
import com.kahga.pluse.voterexport.dto.SentimentFilter;
import com.kahga.pluse.voterexport.service.PdfFontSource;
import com.kahga.pluse.voterexport.service.VoterListPdfBuilder;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Acceptance 9: Odia names print as a reader would write them.
 *
 * <p>The font is whatever the host has — a deployment's bundled Noto Sans
 * Oriya, a path in {@code kpulse.pdf.odia-font}, or an installed family. With
 * none of them the Odia assertions are skipped rather than failed: a machine
 * with no Odia font cannot say anything about Odia rendering, and the fallback
 * behaviour is checked separately below.
 */
class OdiaPdfTest {

    /** "ବିଜୟ କୁମାର ବେହେରା" — a name whose vowel signs are stored after the consonants they print before. */
    private static final String ODIA_NAME = "ବିଜୟ କୁମାର ବେହେରା";

    private static final String ODIA_RELATION = "ପ ରାମଚନ୍ଦ୍ର ବେହେରା";

    /** Installed on the developer machines this was built on; absent elsewhere, and then skipped. */
    private static final String FALLBACK_FAMILY = "Nirmala UI";

    private static PdfFontSource fonts() {
        return new PdfFontSource(System.getProperty("kpulse.pdf.odia-font", FALLBACK_FAMILY), true);
    }

    /**
     * The reason the Odia path exists at all. Writing the name straight through
     * the font's cmap — which is all a PDF writer does — gives a different glyph
     * order from laying it out properly, so the laid-out order is what gets
     * drawn. If this ever stops being true, the outline path can go.
     */
    @Test
    void odiaIsNotPrintableInStorageOrder() {
        PdfFontSource source = fonts();
        assumeTrue(source.hasOdia(), "no Odia font on this host");

        GlyphVector shaped = source.shape(ODIA_NAME, 10f);
        Font raw = new Font(FALLBACK_FAMILY, Font.PLAIN, 10);
        GlyphVector asStored = raw.createGlyphVector(new FontRenderContext(null, true, true), ODIA_NAME);

        assertThat(codes(shaped))
                .as("laying the name out moves glyphs that a straight cmap lookup would leave in storage order")
                .isNotEqualTo(codes(asStored));
    }

    /**
     * What the application does when nothing is configured at all — the state a
     * fresh checkout runs in, and the one that was printing blank names: the
     * host's own Odia family is found and used.
     */
    @Test
    void anUnconfiguredHostWithAnOdiaFamilyFindsIt() {
        assumeTrue(fonts().hasOdia(), "no Odia font on this host");

        assertThat(new PdfFontSource("", true).hasOdia())
                .as("an Odia family is installed, so an unconfigured application should print Odia")
                .isTrue();
    }

    @Test
    void anOdiaRollBuildsAndCarriesItsNames() throws IOException {
        PdfFontSource source = fonts();
        assumeTrue(source.hasOdia(), "no Odia font on this host");

        byte[] pdf = build(source, ODIA_NAME, ODIA_RELATION);
        byte[] latin = build(source, "Bijay Kumar Behera", "F Ramachandra Behera");

        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
        PdfReader reader = new PdfReader(pdf);
        assertThat(reader.getNumberOfPages()).isEqualTo(1);
        // The footer's own count has to agree with the file, or every page lies.
        String printed = new PdfTextExtractor(reader).getTextFromPage(1);
        reader.close();
        assertThat(printed).contains("Page 1 of 1");
        assertThat(printed).contains("Downloaded by Prakash Sahoo");
        assertThat(printed).contains("Not for sharing");

        // The Odia file carries drawn outlines where the Latin one carries a
        // short string, so it is necessarily the larger of the two.
        assertThat(pdf.length).isGreaterThan(latin.length);

        Path sample = Path.of(System.getProperty("java.io.tmpdir"), "k-pulse-odia-sample.pdf");
        Files.write(sample, pdf);
        System.out.println("Odia sample written to " + sample);
    }

    /**
     * A host with no Odia font must still produce a file — unreadable names, but
     * not a crash. The host search is switched off here, since this machine very
     * likely does have an Odia family and the point is the host that does not.
     */
    @Test
    void withoutAnOdiaFontTheListStillBuilds() {
        PdfFontSource none = new PdfFontSource("no-such-font-family", false);
        assertThat(none.hasOdia()).isFalse();

        byte[] pdf = build(none, ODIA_NAME, ODIA_RELATION);
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
    }

    private byte[] build(PdfFontSource fonts, String name, String relation) {
        VoterListPdfBuilder builder = new VoterListPdfBuilder(fonts);
        // No booth name: the walk list is one booth and prints no such column.
        ExportVoterRow row = new ExportVoterRow(
                null,
                name,
                relation,
                "77/4",
                42,
                Gender.M,
                SentimentFilter.POSITIVE,
                Instant.parse("2026-11-01T06:00:00Z"));
        return builder.build(new VoterListPdfBuilder.Request(
                "Booth 12",
                "Bhadrak > Tihidi > Kansabansa > Booth 12",
                "R. Mohanty",
                Set.of(SentimentFilter.values()),
                List.of(row),
                "Prakash Sahoo",
                Instant.parse("2026-11-03T04:30:00Z")));
    }

    private String codes(GlyphVector glyphs) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < glyphs.getNumGlyphs(); i++) {
            out.append(glyphs.getGlyphCode(i)).append(' ');
        }
        return out.toString();
    }
}
