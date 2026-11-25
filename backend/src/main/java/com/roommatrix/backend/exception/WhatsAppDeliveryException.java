package com.roommatrix.backend.exception;

public class WhatsAppDeliveryException extends RuntimeException {
    public WhatsAppDeliveryException(String message) {
        super(message);
    }

    public WhatsAppDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
