package com.kahga.pluse.common.response;

import java.util.List;

/** One page cut from a larger result, plus the size of the whole. */
public record PageResult<T>(List<T> content, int page, int size, long totalElements) {

    public int totalPages() {
        return size <= 0 ? 0 : (int) ((totalElements + size - 1) / size);
    }
}
