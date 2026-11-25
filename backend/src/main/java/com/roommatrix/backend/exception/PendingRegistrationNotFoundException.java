package com.roommatrix.backend.exception;

public class PendingRegistrationNotFoundException extends RuntimeException {
    public PendingRegistrationNotFoundException(String message) {
        super(message);
    }
}
