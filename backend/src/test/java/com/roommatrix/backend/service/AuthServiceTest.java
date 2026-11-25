package com.roommatrix.backend.service;

import com.roommatrix.backend.dto.auth.AuthResponse;
import com.roommatrix.backend.dto.auth.RegisterInitResponse;
import com.roommatrix.backend.dto.auth.RegisterRequest;
import com.roommatrix.backend.dto.auth.ResendOtpRequest;
import com.roommatrix.backend.dto.auth.VerifyOtpRequest;
import com.roommatrix.backend.entity.PendingRegistration;
import com.roommatrix.backend.entity.User;
import com.roommatrix.backend.entity.enums.Gender;
import com.roommatrix.backend.entity.enums.Role;
import com.roommatrix.backend.exception.EmailAlreadyExistsException;
import com.roommatrix.backend.exception.InvalidOtpException;
import com.roommatrix.backend.exception.MobileAlreadyExistsException;
import com.roommatrix.backend.exception.OtpExpiredException;
import com.roommatrix.backend.repository.PendingRegistrationRepository;
import com.roommatrix.backend.repository.UserRepository;
import com.roommatrix.backend.security.JwtService;
import com.roommatrix.backend.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PendingRegistrationRepository pendingRegistrationRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private WhatsAppService whatsAppService;

    @InjectMocks
    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "refreshTokenExpiration", 604800000L);
    }

    // ─── STEP 1: REGISTER INIT TESTS ──────────────────────────────────────────

    @Test
    @DisplayName("Should initiate registration, save to pending, and send OTP via WhatsApp")
    void shouldInitiateRegistrationAndSendWhatsAppOtp() {
        // Arrange
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

        when(userRepository.existsByEmail("john@example.com")).thenReturn(false);
        when(userRepository.existsByMobileNumber("9876543210")).thenReturn(false);
        when(passwordEncoder.encode("secret123")).thenReturn("$2a$10$hashed");
        when(pendingRegistrationRepository.findByMobileNumber("9876543210")).thenReturn(Optional.empty());

        // Act
        RegisterInitResponse response = authService.registerInit(request);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.mobileNumber()).isEqualTo("9876543210");
        assertThat(response.expiresInMinutes()).isEqualTo(10);
        assertThat(response.message()).contains("OTP sent to WhatsApp");

        verify(pendingRegistrationRepository).save(any(PendingRegistration.class));
        verify(whatsAppService).sendOtp(eq("9876543210"), anyString());
        verify(userRepository, never()).save(any(User.class)); // Real user NOT created yet!
    }

    @Test
    @DisplayName("Should throw EmailAlreadyExistsException if email is already in real users table")
    void shouldThrowException_WhenEmailAlreadyInRealUsers() {
        // Arrange
        RegisterRequest request = new RegisterRequest(
                "Jane",
                null,
                "Doe",
                "existing@example.com",
                "secret123",
                "9876543210",
                Gender.FEMALE,
                Set.of(Role.TENANT)
        );

        when(userRepository.existsByEmail("existing@example.com")).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> authService.registerInit(request))
                .isInstanceOf(EmailAlreadyExistsException.class)
                .hasMessageContaining("Email is already registered");

        verify(pendingRegistrationRepository, never()).save(any());
        verify(whatsAppService, never()).sendOtp(anyString(), anyString());
    }

    @Test
    @DisplayName("Should throw MobileAlreadyExistsException if mobile is already in real users table")
    void shouldThrowException_WhenMobileAlreadyInRealUsers() {
        // Arrange
        RegisterRequest request = new RegisterRequest(
                "Bob",
                null,
                "Smith",
                "bob@example.com",
                "secret123",
                "9999999999",
                Gender.MALE,
                Set.of(Role.TENANT)
        );

        when(userRepository.existsByEmail("bob@example.com")).thenReturn(false);
        when(userRepository.existsByMobileNumber("9999999999")).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> authService.registerInit(request))
                .isInstanceOf(MobileAlreadyExistsException.class)
                .hasMessageContaining("Mobile number is already registered");

        verify(pendingRegistrationRepository, never()).save(any());
        verify(whatsAppService, never()).sendOtp(anyString(), anyString());
    }

    // ─── STEP 2: VERIFY OTP AND COMPLETE REGISTRATION TESTS ────────────────────

    @Test
    @DisplayName("Should verify OTP, save real User, delete pending, and return JWT tokens")
    void shouldVerifyOtpAndCreateRealUserSuccessfully() {
        // Arrange
        VerifyOtpRequest request = new VerifyOtpRequest("9876543210", "123456");

        Map<Role, Long> roleRefs = new EnumMap<>(Role.class);
        roleRefs.put(Role.TENANT, null);

        PendingRegistration pending = PendingRegistration.builder()
                .id(10L)
                .mobileNumber("9876543210")
                .email("john@example.com")
                .password("$2a$10$hashed")
                .firstName("John")
                .lastName("Doe")
                .gender(Gender.MALE)
                .roleReferences(roleRefs)
                .otp("123456")
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .build();

        User savedUser = User.builder()
                .id(1L)
                .firstName("John")
                .lastName("Doe")
                .email("john@example.com")
                .mobileNumber("9876543210")
                .password("$2a$10$hashed")
                .gender(Gender.MALE)
                .roleReferences(roleRefs)
                .isActive(true)
                .build();

        when(pendingRegistrationRepository.findByMobileNumber("9876543210")).thenReturn(Optional.of(pending));
        when(userRepository.existsByEmail("john@example.com")).thenReturn(false);
        when(userRepository.existsByMobileNumber("9876543210")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(jwtService.generateAccessToken(any(UserDetails.class))).thenReturn("dummy.access.token");
        when(jwtService.generateRefreshToken(any(UserDetails.class))).thenReturn("dummy.refresh.token");

        // Act
        AuthService.RegisterResult result = authService.verifyOtpAndCompleteRegistration(request);

        // Assert
        assertThat(result).isNotNull();
        AuthResponse response = result.response();
        assertThat(response.accessToken()).isEqualTo("dummy.access.token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.email()).isEqualTo("john@example.com");
        assertThat(response.fullName()).isEqualTo("John Doe");
        assertThat(response.roles()).contains("TENANT");

        // Verify Pending is deleted and real User is saved
        verify(userRepository).save(any(User.class));
        verify(pendingRegistrationRepository).delete(pending);
    }

    @Test
    @DisplayName("Should throw InvalidOtpException when OTP does not match")
    void shouldThrowInvalidOtp_WhenOtpDoesNotMatch() {
        // Arrange
        VerifyOtpRequest request = new VerifyOtpRequest("9876543210", "999999"); // Wrong OTP

        PendingRegistration pending = PendingRegistration.builder()
                .mobileNumber("9876543210")
                .otp("123456")
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .build();

        when(pendingRegistrationRepository.findByMobileNumber("9876543210")).thenReturn(Optional.of(pending));

        // Act & Assert
        assertThatThrownBy(() -> authService.verifyOtpAndCompleteRegistration(request))
                .isInstanceOf(InvalidOtpException.class)
                .hasMessageContaining("Invalid OTP");

        verify(userRepository, never()).save(any());
        verify(pendingRegistrationRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Should throw OtpExpiredException when OTP has expired")
    void shouldThrowOtpExpired_WhenExpired() {
        // Arrange
        VerifyOtpRequest request = new VerifyOtpRequest("9876543210", "123456");

        PendingRegistration pending = PendingRegistration.builder()
                .mobileNumber("9876543210")
                .otp("123456")
                .expiresAt(LocalDateTime.now().minusMinutes(1)) // Expired 1 min ago!
                .build();

        when(pendingRegistrationRepository.findByMobileNumber("9876543210")).thenReturn(Optional.of(pending));

        // Act & Assert
        assertThatThrownBy(() -> authService.verifyOtpAndCompleteRegistration(request))
                .isInstanceOf(OtpExpiredException.class)
                .hasMessageContaining("OTP has expired");

        verify(userRepository, never()).save(any());
        verify(pendingRegistrationRepository).delete(pending); // Cleans up expired record
    }

    @Test
    @DisplayName("Should throw MobileAlreadyExistsException if mobile was registered between step 1 and step 2")
    void shouldThrowMobileAlreadyExists_WhenMobileRegisteredBetweenSteps() {
        // Arrange
        VerifyOtpRequest request = new VerifyOtpRequest("9876543210", "123456");

        PendingRegistration pending = PendingRegistration.builder()
                .mobileNumber("9876543210")
                .email("john@example.com")
                .otp("123456")
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .build();

        when(pendingRegistrationRepository.findByMobileNumber("9876543210")).thenReturn(Optional.of(pending));
        when(userRepository.existsByEmail("john@example.com")).thenReturn(false);
        when(userRepository.existsByMobileNumber("9876543210")).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> authService.verifyOtpAndCompleteRegistration(request))
                .isInstanceOf(MobileAlreadyExistsException.class)
                .hasMessageContaining("Mobile number is already registered");

        verify(userRepository, never()).save(any());
        verify(pendingRegistrationRepository).delete(pending);
    }

    // ─── RESEND OTP TESTS ─────────────────────────────────────────────────────

    @Test
    @DisplayName("Should resend fresh OTP for pending registration")
    void shouldResendOtpSuccessfully() {
        // Arrange
        ResendOtpRequest request = new ResendOtpRequest("9876543210");

        PendingRegistration pending = PendingRegistration.builder()
                .mobileNumber("9876543210")
                .otp("111111")
                .expiresAt(LocalDateTime.now().plusMinutes(2))
                .build();

        when(pendingRegistrationRepository.findByMobileNumber("9876543210")).thenReturn(Optional.of(pending));

        // Act
        RegisterInitResponse response = authService.resendOtp(request);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.mobileNumber()).isEqualTo("9876543210");
        assertThat(response.message()).contains("fresh OTP has been sent");

        verify(pendingRegistrationRepository).save(pending);
        verify(whatsAppService).sendOtp(eq("9876543210"), anyString());
    }
}
