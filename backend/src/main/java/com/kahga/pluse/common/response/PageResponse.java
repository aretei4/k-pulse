package com.kahga.pluse.common.response;

import java.util.List;

/** Mirrors the SPA's `Page<T>` type. */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {}
