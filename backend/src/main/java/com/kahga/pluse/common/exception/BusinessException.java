package com.kahga.pluse.common.exception;

import org.springframework.http.HttpStatus;

/** A rule the caller broke — surfaced to the user as-is, so keep messages readable. */
public class BusinessException extends RuntimeException {

    private final HttpStatus status;

    public BusinessException(String message) {
        this(message, HttpStatus.BAD_REQUEST);
    }

    public BusinessException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
