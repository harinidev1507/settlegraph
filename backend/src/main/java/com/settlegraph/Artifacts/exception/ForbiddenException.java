package com.settlegraph.Artifacts.exception;

// Thrown when the authenticated user is known, but not allowed to touch this
// particular resource (e.g. reading a group they're not a member of).
// Mapped to HTTP 403 in GlobalExceptionHandler.
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
