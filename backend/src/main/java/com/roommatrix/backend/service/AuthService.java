package com.roommatrix.backend.service;

import com.roommatrix.backend.dto.auth.*;
import org.springframework.http.ResponseCookie;

public interface AuthService {
    record RegisterResult(AuthResponse response, ResponseCookie refreshCookie) {}

    // Step 1: Submit form ➔ saves to PendingRegistration ➔ sends WhatsApp OTP
    RegisterInitResponse registerInit(RegisterRequest request);

    // Step 2: Submit OTP ➔ verifies OTP ➔ creates real User in `users` table ➔ returns JWT tokens
    RegisterResult verifyOtpAndCompleteRegistration(VerifyOtpRequest request);

    // Resend OTP for existing pending registration
    RegisterInitResponse resendOtp(ResendOtpRequest request);
}
