package com.roommatrix.backend.dto.auth;

import com.roommatrix.backend.entity.enums.Gender;
import com.roommatrix.backend.entity.enums.Role;

import java.util.Map;
import java.util.Set;

public record AuthResponse(
                String accessToken,
                String tokenType,
                String firstName,
                String lastName,
                String fullName,
                String email,
                String mobileNumber,
                Gender gender,
                Set<String> roles,
                Map<Role, Long> roleReferences) {
        public static AuthResponse of(String accessToken,
                        String firstName,
                        String lastName,
                        String fullName,
                        String email,
                        String mobileNumber,
                        Gender gender,
                        Set<String> roles,
                        Map<Role, Long> roleReferences) {
                return new AuthResponse(
                                accessToken,
                                "Bearer",
                                firstName,
                                lastName,
                                fullName,
                                email,
                                mobileNumber,
                                gender,
                                roles,
                                roleReferences);
        }
}
