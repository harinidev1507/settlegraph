package com.settlegraph.Artifacts.exception;

// Thrown when a resource named in the request path doesn't exist, or doesn't
// exist under the path it was requested from (e.g. a settlement id that belongs
// to a different group). Mapped to HTTP 404 in GlobalExceptionHandler.
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
