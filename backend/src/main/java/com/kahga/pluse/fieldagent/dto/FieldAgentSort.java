package com.kahga.pluse.fieldagent.dto;

/**
 * The sortable columns (FA-8). An enum rather than a column name off the
 * request: the sort goes into the SQL, and a free string there is an injection
 * waiting to happen. The names are the ones the outer query exposes.
 */
public enum FieldAgentSort {
    NAME("name"),
    VOTERS("voters_recorded");

    private final String column;

    FieldAgentSort(String column) {
        this.column = column;
    }

    public String column() {
        return column;
    }
}
