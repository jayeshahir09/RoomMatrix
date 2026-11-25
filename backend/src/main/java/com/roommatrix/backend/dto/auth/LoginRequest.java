package com.roommatrix.backend.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record LoginRequest(
        @NotBlank(message = "Mobile number is required")
        @Pattern(regexp = "^[6-9][0-9]{9}$", message = "Mobile number must be a valid 10-digit number starting with 6, 7, 8, or 9")
        String mobileNumber,

        @NotBlank(message = "Password is required")
        String password
) {}
