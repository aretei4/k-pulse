package com.kahga.pluse.fieldagent.service;

import com.kahga.pluse.fieldagent.dto.FieldAgentDto;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * The export (FA-16, §7). Written here rather than in the controller because
 * the column order is part of what was agreed, and a test can read it.
 *
 * <p>No password or login column, ever: FA-19 says a temporary password is
 * shown once and never again, and a CSV is the one place it would persist.
 */
@Component
public class FieldAgentCsv {

    private static final String[] HEADER = {
        "Agent ID", "Name", "Mobile", "Email", "Address", "Referral", "District", "Block", "Panchayat", "Candidate",
        "Access status", "Access expiry", "Voters recorded", "Joined", "Last active",
        "Paid", "Amount", "Paid on", "Mode", "Reference"
    };

    /** Dates as the admin's own day, not UTC, so a 1 a.m. sign-in is not yesterday. */
    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    public String build(List<FieldAgentDto> agents) {
        StringBuilder csv = new StringBuilder();
        // Excel opens a CSV in the system codepage unless the file says otherwise;
        // the mark is what makes an Odia name survive a round trip through it.
        csv.append('﻿');
        row(csv, HEADER);
        for (FieldAgentDto agent : agents) {
            row(
                    csv,
                    agent.agentCode(),
                    agent.name(),
                    agent.phone(),
                    agent.email(),
                    agent.address(),
                    agent.referralNo(),
                    agent.districtName(),
                    agent.blockName(),
                    agent.panchayatName(),
                    agent.candidateName(),
                    agent.access() == null ? null : agent.access().name(),
                    day(agent.accessExpiresAt()),
                    String.valueOf(agent.votersRecorded()),
                    day(agent.joined()),
                    day(agent.lastActive()),
                    agent.paid() ? "Yes" : "No",
                    agent.paidAmount() == null ? null : agent.paidAmount().toPlainString(),
                    agent.paidOn() == null ? null : agent.paidOn().format(DAY),
                    agent.paidMode() == null ? null : agent.paidMode().name(),
                    agent.paidRef());
        }
        return csv.toString();
    }

    private String day(java.time.Instant instant) {
        return instant == null ? null : DAY.format(instant.atZone(ZONE));
    }

    private void row(StringBuilder csv, String... cells) {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                csv.append(',');
            }
            csv.append(quote(cells[i]));
        }
        csv.append("\r\n");
    }

    /**
     * Quoted whenever the value could otherwise break the row, and a leading
     * =, +, - or @ is defused: a spreadsheet treats those as a formula.
     */
    private String quote(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String cell = "=+-@".indexOf(value.charAt(0)) >= 0 ? "'" + value : value;
        if (cell.indexOf(',') >= 0 || cell.indexOf('"') >= 0 || cell.indexOf('\n') >= 0 || cell.indexOf('\r') >= 0) {
            return '"' + cell.replace("\"", "\"\"") + '"';
        }
        return cell;
    }
}
