package com.roommatrix.backend.controller;

import com.roommatrix.backend.dto.auth.AuthResponse;
import com.roommatrix.backend.dto.auth.LoginRequest;
import com.roommatrix.backend.dto.auth.RegisterInitResponse;
import com.roommatrix.backend.dto.auth.RegisterRequest;
import com.roommatrix.backend.dto.auth.ResendOtpRequest;
import com.roommatrix.backend.dto.auth.VerifyOtpRequest;
import com.roommatrix.backend.service.AuthService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Mobile number and password login.
     * Validates credentials, returns JWT access token, and sets HttpOnly refresh token cookie.
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("REST request: POST /api/auth/login for mobile: {}", request.mobileNumber());
        AuthService.LoginResult result = authService.login(request);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, result.refreshCookie().toString())
                .body(result.response());
    }

    /**
     * Step 1: Submit registration details.
     * Validates input, saves to pending_registrations, and sends OTP to WhatsApp.
     */
    @PostMapping("/register")
    public ResponseEntity<RegisterInitResponse> register(@Valid @RequestBody RegisterRequest request) {
        log.info("REST request: POST /api/auth/register for mobile: {}, email: {}", request.mobileNumber(), request.email());
        RegisterInitResponse response = authService.registerInit(request);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    /**
     * Step 2: Submit WhatsApp OTP.
     * Verifies OTP, creates the real verified User in `users` table, and returns JWT tokens.
     */
    @PostMapping("/verify-otp")
    public ResponseEntity<AuthResponse> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        log.info("REST request: POST /api/auth/verify-otp for mobile: {}", request.mobileNumber());
        AuthService.RegisterResult result = authService.verifyOtpAndCompleteRegistration(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, result.refreshCookie().toString())
                .body(result.response());
    }

    /**
     * Resend a fresh OTP to WhatsApp if previous one expired.
     */
    @PostMapping("/resend-otp")
    public ResponseEntity<RegisterInitResponse> resendOtp(@Valid @RequestBody ResendOtpRequest request) {
        log.info("REST request: POST /api/auth/resend-otp for mobile: {}", request.mobileNumber());
        RegisterInitResponse response = authService.resendOtp(request);
        return ResponseEntity.ok(response);
    }
}
