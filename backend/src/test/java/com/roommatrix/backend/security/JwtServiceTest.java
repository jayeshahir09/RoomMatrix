package com.roommatrix.backend.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private JwtService jwtService;
    private UserDetails userDetails;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();

        // Inject properties manually (no Spring context needed in unit tests)
        ReflectionTestUtils.setField(jwtService, "secretKey",
                "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970");
        ReflectionTestUtils.setField(jwtService, "accessTokenExpiration", 900000L);       // 15 min
        ReflectionTestUtils.setField(jwtService, "refreshTokenExpiration", 604800000L);   // 7 days

        userDetails = new User("user@roommatrix.com", "password", Collections.emptyList());
    }

    // ─── ACCESS TOKEN TESTS ───────────────────────────────────────────────────────

    @Test
    @DisplayName("Should generate a valid access token with 3 JWT parts")
    void shouldGenerateAccessToken() {
        String token = jwtService.generateAccessToken(userDetails);

        assertThat(token).isNotNull().isNotBlank();
        assertThat(token.split("\\.")).hasSize(3); // header.payload.signature
    }

    @Test
    @DisplayName("Should extract correct username from access token")
    void shouldExtractUsernameFromAccessToken() {
        String token = jwtService.generateAccessToken(userDetails);

        String username = jwtService.extractUsername(token);

        assertThat(username).isEqualTo("user@roommatrix.com");
    }

    @Test
    @DisplayName("Should validate access token for correct user")
    void shouldValidateAccessToken() {
        String token = jwtService.generateAccessToken(userDetails);

        assertThat(jwtService.isTokenValid(token, userDetails)).isTrue();
        assertThat(jwtService.isAccessToken(token)).isTrue();
    }

    @Test
    @DisplayName("Should NOT validate access token for a different user")
    void shouldRejectAccessTokenForDifferentUser() {
        String token = jwtService.generateAccessToken(userDetails);
        UserDetails otherUser = new User("hacker@evil.com", "pass", Collections.emptyList());

        assertThat(jwtService.isTokenValid(token, otherUser)).isFalse();
    }

    // ─── REFRESH TOKEN TESTS ──────────────────────────────────────────────────────

    @Test
    @DisplayName("Should generate a valid refresh token")
    void shouldGenerateRefreshToken() {
        String token = jwtService.generateRefreshToken(userDetails);

        assertThat(token).isNotNull().isNotBlank();
        assertThat(token.split("\\.")).hasSize(3);
    }

    @Test
    @DisplayName("Should identify refresh token type correctly")
    void shouldIdentifyRefreshTokenType() {
        String refreshToken = jwtService.generateRefreshToken(userDetails);

        assertThat(jwtService.isRefreshToken(refreshToken)).isTrue();
        assertThat(jwtService.isAccessToken(refreshToken)).isFalse();
    }

    @Test
    @DisplayName("Should identify access token type correctly")
    void shouldIdentifyAccessTokenType() {
        String accessToken = jwtService.generateAccessToken(userDetails);

        assertThat(jwtService.isAccessToken(accessToken)).isTrue();
        assertThat(jwtService.isRefreshToken(accessToken)).isFalse();
    }

    // ─── ACCESS vs REFRESH COMPARISON TESTS ──────────────────────────────────────

    @Test
    @DisplayName("Access token and refresh token should be different strings")
    void accessAndRefreshTokenShouldBeDifferent() {
        String accessToken = jwtService.generateAccessToken(userDetails);
        String refreshToken = jwtService.generateRefreshToken(userDetails);

        assertThat(accessToken).isNotEqualTo(refreshToken);
    }

    @Test
    @DisplayName("Refresh token should expire later than access token")
    void refreshTokenShouldExpireLaterThanAccessToken() {
        String accessToken = jwtService.generateAccessToken(userDetails);
        String refreshToken = jwtService.generateRefreshToken(userDetails);

        Date accessExpiry = jwtService.extractClaim(accessToken, Claims::getExpiration);
        Date refreshExpiry = jwtService.extractClaim(refreshToken, Claims::getExpiration);

        assertThat(refreshExpiry).isAfter(accessExpiry);
    }

    @Test
    @DisplayName("Both tokens should contain the same username as subject")
    void bothTokensShouldHaveSameUsername() {
        String accessToken  = jwtService.generateAccessToken(userDetails);
        String refreshToken = jwtService.generateRefreshToken(userDetails);

        assertThat(jwtService.extractUsername(accessToken)).isEqualTo("user@roommatrix.com");
        assertThat(jwtService.extractUsername(refreshToken)).isEqualTo("user@roommatrix.com");
    }
}
