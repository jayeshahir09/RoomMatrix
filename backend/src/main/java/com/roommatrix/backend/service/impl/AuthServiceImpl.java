package com.roommatrix.backend.service.impl;

import com.roommatrix.backend.dto.auth.AuthResponse;
import com.roommatrix.backend.dto.auth.LoginRequest;
import com.roommatrix.backend.dto.auth.RegisterInitResponse;
import com.roommatrix.backend.dto.auth.RegisterRequest;
import com.roommatrix.backend.dto.auth.ResendOtpRequest;
import com.roommatrix.backend.dto.auth.VerifyOtpRequest;
import com.roommatrix.backend.entity.PendingRegistration;
import com.roommatrix.backend.entity.User;
import com.roommatrix.backend.entity.enums.Role;
import com.roommatrix.backend.exception.EmailAlreadyExistsException;
import com.roommatrix.backend.exception.InvalidOtpException;
import com.roommatrix.backend.exception.MobileAlreadyExistsException;
import com.roommatrix.backend.exception.OtpExpiredException;
import com.roommatrix.backend.exception.PendingRegistrationNotFoundException;
import com.roommatrix.backend.repository.PendingRegistrationRepository;
import com.roommatrix.backend.repository.UserRepository;
import com.roommatrix.backend.security.CustomUserDetails;
import com.roommatrix.backend.security.JwtService;
import com.roommatrix.backend.service.AuthService;
import com.roommatrix.backend.service.WhatsAppService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    private final UserRepository userRepository;
    private final PendingRegistrationRepository pendingRegistrationRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final WhatsAppService whatsAppService;
    private final AuthenticationManager authenticationManager;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${jwt.refresh-token-expiration:604800000}")
    private long refreshTokenExpiration;

    private static final int OTP_EXPIRY_MINUTES = 10;

    public AuthServiceImpl(UserRepository userRepository,
            PendingRegistrationRepository pendingRegistrationRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            WhatsAppService whatsAppService,
            AuthenticationManager authenticationManager) {
        this.userRepository = userRepository;
        this.pendingRegistrationRepository = pendingRegistrationRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.whatsAppService = whatsAppService;
        this.authenticationManager = authenticationManager;
    }

    @Override
    @Transactional(readOnly = true)
    public LoginResult login(LoginRequest request) {
        String cleanMobile = request.mobileNumber().trim();
        log.info("Processing login request for mobile: {}", cleanMobile);

        // 1. Authenticate mobile number & password via AuthenticationManager
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(cleanMobile, request.password())
            );
        } catch (BadCredentialsException ex) {
            log.warn("Login failed for mobile: {}. Invalid credentials.", cleanMobile);
            throw new BadCredentialsException("Invalid mobile number or password");
        }

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        User user = userDetails.getUser();

        // 2. Enforce active status
        if (!user.isActive()) {
            log.warn("Login rejected for mobile: {}. Account id={} is deactivated.", cleanMobile, user.getId());
            throw new DisabledException("Account has been deactivated. Please contact support.");
        }

        // 3. Generate JWT tokens
        String accessToken = jwtService.generateAccessToken(userDetails);
        String refreshToken = jwtService.generateRefreshToken(userDetails);

        // 4. Build HttpOnly Cookie for Refresh Token
        ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", refreshToken)
                .httpOnly(true)
                .secure(false) // true in HTTPS production
                .path("/")
                .maxAge(refreshTokenExpiration / 1000)
                .sameSite("Lax")
                .build();

        Set<String> roleNames = user.getRoles().stream()
                .map(Enum::name)
                .collect(Collectors.toSet());

        log.info("Login successful for user: id={}, mobile={}, roles={}", user.getId(), user.getMobileNumber(), roleNames);

        AuthResponse authResponse = AuthResponse.of(
                accessToken,
                user.getFirstName(),
                user.getLastName(),
                user.getFullName(),
                user.getEmail(),
                user.getMobileNumber(),
                user.getGender(),
                roleNames,
                user.getRoleReferences());

        return new LoginResult(authResponse, refreshCookie);
    }

    @Override
    @Transactional
    public RegisterInitResponse registerInit(RegisterRequest request) {
        String cleanEmail = request.email().trim().toLowerCase();
        String cleanMobile = request.mobileNumber().trim();
        log.info("Initiating registration for email: {}, mobile: {}", cleanEmail, cleanMobile);

        // 1. Verify that email is not already taken by a REAL user in `users` table
        if (userRepository.existsByEmail(cleanEmail)) {
            log.warn("Registration rejected: Email {} is already registered.", cleanEmail);
            throw new EmailAlreadyExistsException("Email is already registered: " + cleanEmail);
        }

        // 2. Verify that mobile is not already taken by a REAL user in `users` table
        if (userRepository.existsByMobileNumber(cleanMobile)) {
            log.warn("Registration rejected: Mobile {} is already registered.", cleanMobile);
            throw new MobileAlreadyExistsException("Mobile number is already registered: " + cleanMobile);
        }

        // 3. Build roleReferences map (default to TENANT if not specified)
        Map<Role, Long> roleReferences = new EnumMap<>(Role.class);
        Set<Role> roles = request.roles();
        if (roles == null || roles.isEmpty()) {
            roleReferences.put(Role.TENANT, null);
        } else {
            for (Role role : roles) {
                roleReferences.put(role, null);
            }
        }

        // 4. Generate 6-digit OTP (e.g. 582910)
        String otp = String.format("%06d", secureRandom.nextInt(1000000));
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES);

        // 5. Look for existing pending registration for this mobile; if present,
        // OVERWRITE it!
        PendingRegistration pending = pendingRegistrationRepository.findByMobileNumber(cleanMobile)
                .orElse(new PendingRegistration());

        pending.setMobileNumber(cleanMobile);
        pending.setEmail(cleanEmail);
        pending.setPassword(passwordEncoder.encode(request.password()));
        pending.setFirstName(request.firstName().trim());
        pending.setMiddleName(request.middleName() != null ? request.middleName().trim() : null);
        pending.setLastName(request.lastName().trim());
        pending.setGender(request.gender());
        pending.setRoleReferences(roleReferences);
        pending.setOtp(otp);
        pending.setExpiresAt(expiresAt);

        pendingRegistrationRepository.save(pending);

        // 6. Send OTP to WhatsApp
        whatsAppService.sendOtp(cleanMobile, otp);
        log.info("Pending registration saved for mobile: {}. OTP dispatched via WhatsApp.", cleanMobile);

        return new RegisterInitResponse(
                "OTP sent to WhatsApp successfully. Please verify within " + OTP_EXPIRY_MINUTES + " minutes.",
                cleanMobile,
                OTP_EXPIRY_MINUTES);
    }

    @Override
    @Transactional
    public RegisterResult verifyOtpAndCompleteRegistration(VerifyOtpRequest request) {
        String cleanMobile = request.mobileNumber().trim();
        String submittedOtp = request.otp().trim();
        log.info("Processing OTP verification for mobile: {}", cleanMobile);

        // 1. Find the pending registration session
        PendingRegistration pending = pendingRegistrationRepository.findByMobileNumber(cleanMobile)
                .orElseThrow(() -> {
                    log.warn("OTP verification rejected: Session not found or expired for mobile: {}", cleanMobile);
                    return new PendingRegistrationNotFoundException(
                            "Registration session not found or expired for mobile: " + cleanMobile
                                    + ". Please register again.");
                });

        // 2. Check if OTP is expired
        if (pending.getExpiresAt().isBefore(LocalDateTime.now())) {
            log.warn("OTP verification rejected for mobile: {}. OTP expired at {}", cleanMobile, pending.getExpiresAt());
            pendingRegistrationRepository.delete(pending);
            throw new OtpExpiredException("OTP has expired. Please register again or request a new OTP.");
        }

        // 3. Check if OTP matches
        if (!pending.getOtp().equals(submittedOtp)) {
            log.warn("OTP verification rejected for mobile: {}. Invalid OTP submitted.", cleanMobile);
            throw new InvalidOtpException("Invalid OTP. Please check and try again.");
        }

        // 4. Double check that email or mobile wasn't registered in `users` in the meantime
        if (userRepository.existsByEmail(pending.getEmail())) {
            log.warn("OTP verification conflict: Email {} was registered concurrently in users table.", pending.getEmail());
            pendingRegistrationRepository.delete(pending);
            throw new EmailAlreadyExistsException("Email is already registered: " + pending.getEmail());
        }
        if (userRepository.existsByMobileNumber(pending.getMobileNumber())) {
            log.warn("OTP verification conflict: Mobile {} was registered concurrently in users table.", pending.getMobileNumber());
            pendingRegistrationRepository.delete(pending);
            throw new MobileAlreadyExistsException("Mobile number is already registered: " + pending.getMobileNumber());
        }

        // 5. CREATE THE REAL, VERIFIED USER IN `users` TABLE!
        User user = User.builder()
                .firstName(pending.getFirstName())
                .middleName(pending.getMiddleName())
                .lastName(pending.getLastName())
                .email(pending.getEmail())
                .password(pending.getPassword()) // already hashed
                .mobileNumber(pending.getMobileNumber())
                .gender(pending.getGender())
                .roleReferences(pending.getRoleReferences())
                .isActive(true)
                .build();

        User savedUser = userRepository.save(user);
        log.info("Registration completed: Real user created with id: {}, mobile: {}, email: {}",
                savedUser.getId(), savedUser.getMobileNumber(), savedUser.getEmail());

        // 6. Delete the pending registration record (job done!)
        pendingRegistrationRepository.delete(pending);

        // 7. Generate JWT Access and Refresh Tokens
        CustomUserDetails userDetails = new CustomUserDetails(savedUser);
        String accessToken = jwtService.generateAccessToken(userDetails);
        String refreshToken = jwtService.generateRefreshToken(userDetails);

        // 8. Build HttpOnly Cookie for Refresh Token
        ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", refreshToken)
                .httpOnly(true)
                .secure(false) // true in HTTPS production
                .path("/")
                .maxAge(refreshTokenExpiration / 1000)
                .sameSite("Lax")
                .build();

        Set<String> roleNames = savedUser.getRoles().stream()
                .map(Enum::name)
                .collect(Collectors.toSet());

        AuthResponse authResponse = AuthResponse.of(
                accessToken,
                savedUser.getFirstName(),
                savedUser.getLastName(),
                savedUser.getFullName(),
                savedUser.getEmail(),
                savedUser.getMobileNumber(),
                savedUser.getGender(),
                roleNames,
                savedUser.getRoleReferences());

        return new RegisterResult(authResponse, refreshCookie);
    }

    @Override
    @Transactional
    public RegisterInitResponse resendOtp(ResendOtpRequest request) {
        String cleanMobile = request.mobileNumber().trim();
        log.info("Processing OTP resend request for mobile: {}", cleanMobile);

        // 1. Look for pending registration
        PendingRegistration pending = pendingRegistrationRepository.findByMobileNumber(cleanMobile)
                .orElseThrow(() -> {
                    log.warn("OTP resend failed: No pending registration session found for mobile: {}", cleanMobile);
                    return new PendingRegistrationNotFoundException(
                            "No pending registration found for mobile: " + cleanMobile + ". Please register again.");
                });

        // 2. Generate new OTP and reset expiration
        String newOtp = String.format("%06d", secureRandom.nextInt(1000000));
        pending.setOtp(newOtp);
        pending.setExpiresAt(LocalDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES));
        pendingRegistrationRepository.save(pending);

        // 3. Send via WhatsApp
        whatsAppService.sendOtp(cleanMobile, newOtp);
        log.info("Fresh OTP generated and dispatched via WhatsApp to mobile: {}", cleanMobile);

        return new RegisterInitResponse(
                "A fresh OTP has been sent to your WhatsApp.",
                cleanMobile,
                OTP_EXPIRY_MINUTES);
    }
}
