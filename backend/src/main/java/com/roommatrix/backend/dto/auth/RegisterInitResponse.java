package com.roommatrix.backend.dto.auth;

public record RegisterInitResponse(
        String message,
        String mobileNumber,
        int expiresInMinutes
) {}
