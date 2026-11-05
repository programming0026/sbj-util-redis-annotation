package com.juniverse.redis.exception;

import java.io.Serial;

public class CoalesceException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    public CoalesceException(String message) {
        super(message);
    }

    public CoalesceException(String message, Throwable cause) {
        super(message, cause);
    }

    public CoalesceException(Throwable cause) {
        super(cause);
    }
}
