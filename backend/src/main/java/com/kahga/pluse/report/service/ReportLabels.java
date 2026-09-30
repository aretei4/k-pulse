package com.kahga.pluse.report.service;

import com.kahga.pluse.report.dto.ReportFilterRequest;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Turns the applied filters into the one line that goes on every export. */
final class ReportLabels {

    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("d MMM yyyy").withZone(ZoneId.of("Asia/Kolkata"));

    private ReportLabels() {}

    static String describe(ReportFilterRequest filter) {
        List<String> bits = new ArrayList<>();
        bits.add("Level: " + filter.levelOrDefault().name().toLowerCase());
        if (filter.sentiment() != null) {
            bits.add("Sentiment: " + filter.sentiment().name().toLowerCase());
        }
        if (filter.confidence() != null) {
            bits.add("Confidence: " + filter.confidence().name().toLowerCase());
        }
        if (filter.from() != null) {
            bits.add("From: " + DATE.format(filter.from()));
        }
        if (filter.to() != null) {
            bits.add("To: " + DATE.format(filter.to()));
        }
        if (bits.size() == 1) {
            bits.add("Whole campaign");
        }
        return String.join(" · ", bits);
    }
}
