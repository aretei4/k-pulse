package com.kahga.pluse.voterexport.service;

import com.lowagie.text.FontFactory;
import com.lowagie.text.pdf.BaseFont;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.GraphicsEnvironment;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * How Odia names get onto the page.
 *
 * <p>Two things are needed, and embedding a font only solves the first. The
 * built-in PDF fonts hold no Odia glyphs, so without an Odia font the names
 * print as boxes. But an Odia name is also not written in the order it is
 * stored: the vowel sign in {@code ବେ} is stored after its consonant and
 * printed before it, and conjuncts are single glyphs rather than the letters
 * they are spelled from. OpenPDF writes text straight through the font's cmap
 * with no such reordering, so an embedded font on its own yields a name that is
 * wrong rather than merely unreadable.
 *
 * <p>Odia runs are therefore laid out here by Java2D, which does apply the
 * font's shaping tables, and drawn into the PDF as outlines. The name then
 * looks exactly as it does on screen. The cost is that an Odia name is not
 * selectable text in the finished file; Latin names are written normally and
 * stay selectable.
 */
@Component
public class PdfFontSource {

    private static final Logger log = LoggerFactory.getLogger(PdfFontSource.class);

    /** Noto Sans Oriya is the font the requirement names; any Odia TTF under this name is used. */
    private static final String BUNDLED = "fonts/NotoSansOriya-Regular.ttf";

    /**
     * Families tried before the rest of the host's fonts, best first: the Notos
     * a Linux server is likely to have, then the Odia fonts the Indian distros
     * ship, then what Windows installs. Any other family holding Odia glyphs is
     * used after these.
     */
    private static final String[] PREFERRED = {
        "Noto Sans Oriya",
        "Noto Serif Oriya",
        "Noto Sans Odia",
        "Lohit Odia",
        "Lohit Oriya",
        "Utkal",
        "Kalinga",
        "Nirmala UI",
    };

    /** The Odia block. A name holding any of these needs the shaped path. */
    private static final char ODIA_FIRST = '଀';
    private static final char ODIA_LAST = '୿';

    /** KA — a font claiming to be Odia but lacking this would print boxes anyway. */
    private static final char ODIA_KA = 'କ';

    /** Antialiasing and fractional metrics on: this measures for print, not for a screen grid. */
    static final FontRenderContext RENDER = new FontRenderContext(null, true, true);

    private final Font odia;

    public PdfFontSource(
            @Value("${kpulse.pdf.odia-font:}") String configured,
            @Value("${kpulse.pdf.odia-font-host-fallback:true}") boolean hostFallback) {
        this.odia = load(configured, hostFallback);
        if (odia == null) {
            log.warn(
                    "No Odia font available (nothing usable in kpulse.pdf.odia-font, no {}, and no "
                            + "installed family with Odia glyphs): Odia voter names will be missing from "
                            + "downloaded lists. Install an Odia font on this host or see {}",
                    BUNDLED,
                    BUNDLED.replace("NotoSansOriya-Regular.ttf", "README.md"));
        }
    }

    /** Whether an Odia name can be printed at all. */
    public boolean hasOdia() {
        return odia != null;
    }

    public static boolean isOdia(String text) {
        return text != null && text.chars().anyMatch(ch -> ch >= ODIA_FIRST && ch <= ODIA_LAST);
    }

    /**
     * The text laid out at this size, with the font's own reordering and
     * ligatures applied — the shape a reader expects, not the storage order.
     */
    public GlyphVector shape(String text, float size) {
        Font sized = odia.deriveFont(size);
        return sized.layoutGlyphVector(RENDER, text.toCharArray(), 0, text.length(), Font.LAYOUT_LEFT_TO_RIGHT);
    }

    /**
     * One glyph's outline, drawn from its own origin. Taken by glyph code rather
     * than by character because the code is what laying out the text produced,
     * and a glyph may be a conjunct no single character names.
     */
    public java.awt.Shape glyphOutline(int glyphCode, float size) {
        return odia.deriveFont(size)
                .createGlyphVector(RENDER, new int[] {glyphCode})
                .getGlyphOutline(0);
    }

    /**
     * How far above the baseline this font draws, so a shaped run can be placed
     * in a cell. Zero when there is no Odia font: nothing will be drawn with it,
     * and asking for the measurements of a font that is not there should not be
     * what breaks the file.
     */
    public float ascent(float size) {
        return odia == null ? 0 : odia.deriveFont(size).getLineMetrics("Mg", RENDER).getAscent();
    }

    public float descent(float size) {
        return odia == null ? 0 : odia.deriveFont(size).getLineMetrics("Mg", RENDER).getDescent();
    }

    /** Ordinary text: a built-in font, written as text and still selectable in the file. */
    public com.lowagie.text.Font latin(float size, java.awt.Color color, boolean bold) {
        return FontFactory.getFont(bold ? FontFactory.HELVETICA_BOLD : FontFactory.HELVETICA, size, color);
    }

    /** The raw font for drawing straight onto the page, as the watermark and footer do. */
    public BaseFont base() {
        try {
            return BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
        } catch (IOException ex) {
            throw new IllegalStateException("The built-in Helvetica could not be loaded", ex);
        }
    }

    /**
     * The configured value is either a font file a deployment ships, or the name
     * of a family already installed on the host. Failing both, the font bundled
     * with the application is used.
     */
    private Font load(String configured, boolean hostFallback) {
        if (configured != null && !configured.isBlank()) {
            String value = configured.trim();
            Path path = Path.of(value);
            Font fromFile = Files.isReadable(path) ? fromFile(path) : null;
            if (fromFile != null) {
                log.info("Odia PDF font loaded from {}", path);
                return fromFile;
            }
            Font installed = installed(value);
            if (installed != null) {
                log.info("Odia PDF font: using the installed family {}", value);
                return installed;
            }
            log.warn(
                    "kpulse.pdf.odia-font is {}, which is neither a readable font file nor an installed family",
                    value);
        }
        Font bundled = bundled();
        if (bundled != null) {
            return bundled;
        }
        return hostFallback ? hostFamily() : null;
    }

    /**
     * The last resort: a family the host already has. A server without the
     * bundled font is still likely to carry Noto or a distribution's Odia font,
     * and a name printed in one of those is far better than a name that does
     * not print at all — which is what happens otherwise, since the built-in
     * Helvetica simply drops the characters it cannot encode.
     *
     * <p>A deployment that would rather be told than guessed for can switch this
     * off with {@code kpulse.pdf.odia-font-host-fallback=false}, and then a
     * missing font is a warning in the log instead of names in whatever family
     * the host happened to have.
     */
    private Font hostFamily() {
        String[] installed =
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
        return Stream.concat(
                        Arrays.stream(PREFERRED)
                                .filter(preferred -> Arrays.stream(installed)
                                        .anyMatch(name -> name.equalsIgnoreCase(preferred))),
                        Arrays.stream(installed))
                .distinct()
                .map(family -> new Font(family, Font.PLAIN, 1))
                .filter(font -> font.canDisplay(ODIA_KA))
                .findFirst()
                .map(font -> {
                    log.info("Odia PDF font: falling back to the installed family {}", font.getFamily());
                    return font;
                })
                .orElse(null);
    }

    private Font bundled() {
        ClassPathResource resource = new ClassPathResource(BUNDLED);
        if (!resource.exists()) {
            return null;
        }
        try (InputStream in = resource.getInputStream()) {
            Font font = check(Font.createFont(Font.TRUETYPE_FONT, in));
            if (font != null) {
                log.info("Odia PDF font loaded from {}", BUNDLED);
            }
            return font;
        } catch (IOException | FontFormatException ex) {
            log.warn("Could not read {}: {}", BUNDLED, ex.getMessage());
            return null;
        }
    }

    private Font fromFile(Path path) {
        try (InputStream in = Files.newInputStream(path)) {
            return check(Font.createFont(Font.TRUETYPE_FONT, in));
        } catch (IOException | FontFormatException ex) {
            log.warn("{} is not a usable font: {}", path, ex.getMessage());
            return null;
        }
    }

    /**
     * Java hands back a default font rather than failing when a family is not
     * installed, so the name asked for is checked against the list first —
     * otherwise a typo would quietly print every name in Dialog.
     */
    private Font installed(String family) {
        boolean known = Arrays.stream(
                        GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames())
                .anyMatch(name -> name.equalsIgnoreCase(family));
        return known ? check(new Font(family, Font.PLAIN, 1)) : null;
    }

    /** A font with no Odia glyphs would print boxes just as surely as no font at all. */
    private Font check(Font font) {
        if (font == null) {
            return null;
        }
        if (!font.canDisplay(ODIA_KA)) {
            log.warn("{} holds no Odia glyphs, so it is not used for voter names", font.getFontName());
            return null;
        }
        return font;
    }
}
