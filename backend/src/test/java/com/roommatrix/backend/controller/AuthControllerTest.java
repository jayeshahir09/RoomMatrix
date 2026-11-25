package com.roommatrix.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roommatrix.backend.dto.auth.AuthResponse;
import com.roommatrix.backend.dto.auth.RegisterInitResponse;
import com.roommatrix.backend.dto.auth.RegisterRequest;
import com.roommatrix.backend.dto.auth.ResendOtpRequest;
import com.roommatrix.backend.dto.auth.VerifyOtpRequest;
import com.roommatrix.backend.entity.enums.Gender;
import com.roommatrix.backend.entity.enums.Role;
import com.roommatrix.backend.exception.EmailAlreadyExistsException;
import com.roommatrix.backend.exception.GlobalExceptionHandler;
import com.roommatrix.backend.exception.InvalidOtpException;
import com.roommatrix.backend.exception.OtpExpiredException;
import com.roommatrix.backend.exception.PendingRegistrationNotFoundException;
import com.roommatrix.backend.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthController authController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ─── POST /api/auth/register ───────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/auth/register - Should return 200 OK when request is valid")
    void register_ValidRequest_Returns200() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "John",
                null,
                "Doe",
                "john@example.com",
                "secret123",
                "9876543210",
                Gender.MALE,
                Set.of(Role.TENANT)
        );

        RegisterInitResponse response = new RegisterInitResponse(
                "OTP sent to WhatsApp successfully.",
                "9876543210",
                10
        );

        when(authService.registerInit(any(RegisterRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("OTP sent to WhatsApp successfully."))
                .andExpect(jsonPath("$.mobileNumber").value("9876543210"))
                .andExpect(jsonPath("$.expiresInMinutes").value(10));
    }

    @Test
    @DisplayName("POST /api/auth/register - Should return 400 Bad Request when validation fails")
    void register_InvalidMobile_Returns400() throws Exception {
        // Invalid mobile (starts with 1, only 6 digits)
        RegisterRequest request = new RegisterRequest(
                "John",
                null,
                "Doe",
                "john@example.com",
                "secret123",
                "123456",
                Gender.MALE,
                Set.of(Role.TENANT)
        );

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Validation Failed"))
                .andExpect(jsonPath("$.validationErrors.mobileNumber").exists());
    }

    @Test
    @DisplayName("POST /api/auth/register - Should return 409 Conflict when email exists")
    void register_EmailExists_Returns409() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "John",
                null,
                "Doe",
                "john@example.com",
                "secret123",
                "9876543210",
                Gender.MALE,
                Set.of(Role.TENANT)
        );

        when(authService.registerInit(any(RegisterRequest.class)))
                .thenThrow(new EmailAlreadyExistsException("Email is already registered: john@example.com"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Email is already registered: john@example.com"));
    }

    // ─── POST /api/auth/verify-otp ────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/auth/verify-otp - Should return 201 Created with HttpOnly cookie and AuthResponse")
    void verifyOtp_ValidRequest_Returns201WithCookie() throws Exception {
        VerifyOtpRequest request = new VerifyOtpRequest("9876543210", "123456");

        AuthResponse authResponse = AuthResponse.of(
                "access.jwt.token",
                "John",
                "Doe",
                "John Doe",
                "john@example.com",
                "9876543210",
                Gender.MALE,
                Set.of("TENANT"),
                Map.of(Role.TENANT, 1L)
        );

        ResponseCookie cookie = ResponseCookie.from("refreshToken", "refresh.jwt.token")
                .httpOnly(true)
                .path("/")
                .maxAge(604800)
                .build();

        AuthService.RegisterResult result = new AuthService.RegisterResult(authResponse, cookie);

        when(authService.verifyOtpAndCompleteRegistration(any(VerifyOtpRequest.class))).thenReturn(result);

        mockMvc.perform(post("/api/auth/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.SET_COOKIE))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("refreshToken=refresh.jwt.token")))
                .andExpect(jsonPath("$.accessToken").value("access.jwt.token"))
                .andExpect(jsonPath("$.email").value("john@example.com"))
                .andExpect(jsonPath("$.fullName").value("John Doe"));
    }

    @Test
    @DisplayName("POST /api/auth/verify-otp - Should return 400 Bad Request when OTP is invalid")
    void verifyOtp_InvalidOtp_Returns400() throws Exception {
        VerifyOtpRequest request = new VerifyOtpRequest("9876543210", "999999");

        when(authService.verifyOtpAndCompleteRegistration(any(VerifyOtpRequest.class)))
                .thenThrow(new InvalidOtpException("Invalid OTP. Please check and try again."));

        mockMvc.perform(post("/api/auth/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Invalid OTP. Please check and try again."));
    }

    @Test
    @DisplayName("POST /api/auth/verify-otp - Should return 410 Gone when OTP is expired")
    void verifyOtp_ExpiredOtp_Returns410() throws Exception {
        VerifyOtpRequest request = new VerifyOtpRequest("9876543210", "123456");

        when(authService.verifyOtpAndCompleteRegistration(any(VerifyOtpRequest.class)))
                .thenThrow(new OtpExpiredException("OTP has expired. Please register again or request a new OTP."));

        mockMvc.perform(post("/api/auth/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.status").value(410))
                .andExpect(jsonPath("$.message").value("OTP has expired. Please register again or request a new OTP."));
    }

    // ─── POST /api/auth/resend-otp ────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/auth/resend-otp - Should return 200 OK when resend succeeds")
    void resendOtp_ValidRequest_Returns200() throws Exception {
        ResendOtpRequest request = new ResendOtpRequest("9876543210");

        RegisterInitResponse response = new RegisterInitResponse(
                "A fresh OTP has been sent to your WhatsApp.",
                "9876543210",
                10
        );

        when(authService.resendOtp(any(ResendOtpRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/auth/resend-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("A fresh OTP has been sent to your WhatsApp."))
                .andExpect(jsonPath("$.mobileNumber").value("9876543210"));
    }

    @Test
    @DisplayName("POST /api/auth/resend-otp - Should return 404 Not Found when pending session not found")
    void resendOtp_NotFound_Returns404() throws Exception {
        ResendOtpRequest request = new ResendOtpRequest("9876543210");

        when(authService.resendOtp(any(ResendOtpRequest.class)))
                .thenThrow(new PendingRegistrationNotFoundException("No pending registration found for mobile: 9876543210. Please register again."));

        mockMvc.perform(post("/api/auth/resend-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("No pending registration found")));
    }
}
