package com.roommatrix.backend.dto.auth;

import com.roommatrix.backend.entity.enums.Gender;
import com.roommatrix.backend.entity.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record RegisterRequest(
        @NotBlank(message = "First name is required")
        @Size(min = 2, max = 50, message = "First name must be between 2 and 50 characters")
        @Pattern(regexp = "^[a-zA-Z\\s]+$", message = "First name must contain only alphabets")
        String firstName,

        @Size(max = 50, message = "Middle name cannot exceed 50 characters")
        @Pattern(regexp = "^[a-zA-Z\\s]*$", message = "Middle name must contain only alphabets")
        String middleName,

        @NotBlank(message = "Last name is required")
        @Size(min = 1, max = 50, message = "Last name must be between 1 and 50 characters")
        @Pattern(regexp = "^[a-zA-Z\\s]+$", message = "Last name must contain only alphabets")
        String lastName,

        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        @Size(max = 254, message = "Email cannot exceed 254 characters")
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 6, max = 64, message = "Password must be between 6 and 64 characters")
        String password,

        @NotBlank(message = "Mobile number is required")
        @Pattern(regexp = "^[6-9][0-9]{9}$", message = "Mobile number must be a valid 10-digit number starting with 6, 7, 8, or 9")
        String mobileNumber,

        Gender gender,

        Set<Role> roles
) {}
