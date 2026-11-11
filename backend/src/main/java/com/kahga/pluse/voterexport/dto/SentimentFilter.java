package com.kahga.pluse.voterexport.dto;

import com.kahga.pluse.common.exception.BusinessException;
import com.kahga.pluse.sentiment.entity.SentimentValue;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * DL-4: the only filter a download offers. Four choices, and "not recorded" is
 * one of them — it is the absence of a sentiment row rather than a value, which
 * is why this is its own type and not {@link SentimentValue}.
 *
 * <p>Declaration order is the order the file name lists them in (DL-6), so it
 * is fixed: Positive, Neutral, Negative, NotRecorded.
 */
public enum SentimentFilter {
    POSITIVE(SentimentValue.POSITIVE, "Positive"),
    NEUTRAL(SentimentValue.NEUTRAL, "Neutral"),
    NEGATIVE(SentimentValue.NEGATIVE, "Negative"),
    NOT_RECORDED(null, "NotRecorded");

    private final SentimentValue value;
    private final String token;

    SentimentFilter(SentimentValue value, String token) {
        this.value = value;
        this.token = token;
    }

    /** The recorded value this stands for, or null for "not recorded". */
    public SentimentValue value() {
        return value;
    }

    /** How the file name spells it. */
    public String token() {
        return token;
    }

    /** How a person reads it: "Not recorded", not "NOT_RECORDED". */
    public String label() {
        return this == NOT_RECORDED ? "Not recorded" : token;
    }

    /**
     * Reads what the phone sent. The parameter may repeat or be comma-separated,
     * and spelling is forgiving ("NotRecorded", "not_recorded", "not recorded")
     * because this is a query string, not a stored value.
     */
    public static Set<SentimentFilter> parse(Collection<String> raw) {
        Set<SentimentFilter> chosen = new LinkedHashSet<>();
        if (raw != null) {
            raw.stream()
                    .filter(part -> part != null && !part.isBlank())
                    .flatMap(part -> Arrays.stream(part.split(",")))
                    .map(SentimentFilter::one)
                    .forEach(chosen::add);
        }
        if (chosen.isEmpty()) {
            throw new BusinessException("Choose at least one sentiment to download");
        }
        // Back into declaration order, whatever order they arrived in.
        Set<SentimentFilter> ordered = new LinkedHashSet<>();
        Arrays.stream(values()).filter(chosen::contains).forEach(ordered::add);
        return ordered;
    }

    private static SentimentFilter one(String raw) {
        String key = raw.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        if (key.equals("NOTRECORDED")) {
            key = NOT_RECORDED.name();
        }
        try {
            return valueOf(key);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(raw.trim() + " is not a sentiment");
        }
    }

    /** The {@code {filters}} part of the file name: every choice, or "All". */
    public static String fileToken(Set<SentimentFilter> chosen) {
        if (chosen.size() == values().length) {
            return "All";
        }
        StringBuilder out = new StringBuilder();
        for (SentimentFilter filter : values()) {
            if (chosen.contains(filter)) {
                out.append(out.isEmpty() ? "" : "-").append(filter.token());
            }
        }
        return out.toString();
    }

    /** The same list as a person reads it, for the PDF heading. */
    public static String describe(Set<SentimentFilter> chosen) {
        if (chosen.size() == values().length) {
            return "All sentiments";
        }
        StringBuilder out = new StringBuilder();
        for (SentimentFilter filter : values()) {
            if (chosen.contains(filter)) {
                out.append(out.isEmpty() ? "" : ", ").append(filter.label());
            }
        }
        return out.toString();
    }
}
