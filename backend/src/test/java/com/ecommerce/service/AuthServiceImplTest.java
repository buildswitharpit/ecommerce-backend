package com.ecommerce.service;

import com.ecommerce.dto.request.LoginRequest;
import com.ecommerce.dto.request.RefreshRequest;
import com.ecommerce.dto.request.RegisterRequest;
import com.ecommerce.dto.response.AuthResponse;
import com.ecommerce.dto.response.UserResponse;
import com.ecommerce.entity.RefreshToken;
import com.ecommerce.entity.Role;
import com.ecommerce.entity.User;
import com.ecommerce.exception.DuplicateEmailException;
import com.ecommerce.exception.UnauthorizedException;
import com.ecommerce.mapper.UserMapper;
import com.ecommerce.repository.RefreshTokenRepository;
import com.ecommerce.repository.UserRepository;
import com.ecommerce.security.JwtService;
import com.ecommerce.security.TokenHasher;
import com.ecommerce.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;

    private final UserMapper userMapper = new UserMapper();

    private AuthServiceImpl authService;
    private User sampleUser;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(userRepository, refreshTokenRepository, passwordEncoder, jwtService,
                userMapper, 604_800_000L);
        sampleUser = new User("jane@example.com", "hashed-pw", "Jane Doe", Role.CUSTOMER);
        sampleUser.setId(1L);
    }

    @Test
    void register_createsCustomerAccount_neverAdmin() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("jane@example.com");
        request.setPassword("SecurePass123!");
        request.setFullName("Jane Doe");

        when(userRepository.existsByEmail("jane@example.com")).thenReturn(false);
        when(passwordEncoder.encode("SecurePass123!")).thenReturn("hashed-pw");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(1L);
            return u;
        });

        UserResponse response = authService.register(request);

        assertThat(response.role()).isEqualTo(Role.CUSTOMER);
        assertThat(response.email()).isEqualTo("jane@example.com");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo(Role.CUSTOMER);
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("hashed-pw");
    }

    @Test
    void register_duplicateEmail_throwsConflict() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("jane@example.com");
        request.setPassword("SecurePass123!");
        request.setFullName("Jane Doe");

        when(userRepository.existsByEmail("jane@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(DuplicateEmailException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void login_validCredentials_returnsTokenPair() {
        LoginRequest request = new LoginRequest();
        request.setEmail("jane@example.com");
        request.setPassword("SecurePass123!");

        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("SecurePass123!", "hashed-pw")).thenReturn(true);
        when(jwtService.generateAccessToken(sampleUser)).thenReturn("access-token-value");
        when(jwtService.getAccessTokenExpirationMs()).thenReturn(900_000L);

        AuthResponse response = authService.login(request);

        assertThat(response.accessToken()).isEqualTo("access-token-value");
        assertThat(response.refreshToken()).isNotBlank();
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresInSeconds()).isEqualTo(900L);

        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void login_badPassword_throwsUnauthorized() {
        LoginRequest request = new LoginRequest();
        request.setEmail("jane@example.com");
        request.setPassword("wrong-password");

        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("wrong-password", "hashed-pw")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void login_unknownEmail_throwsUnauthorized() {
        LoginRequest request = new LoginRequest();
        request.setEmail("nobody@example.com");
        request.setPassword("whatever");

        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void refresh_validToken_rotatesAndReturnsNewPair() {
        String rawToken = "raw-refresh-token";
        RefreshToken stored = new RefreshToken(sampleUser, TokenHasher.sha256(rawToken), LocalDateTime.now().plusDays(1));
        stored.setId(5L);

        when(refreshTokenRepository.findByTokenHash(TokenHasher.sha256(rawToken))).thenReturn(Optional.of(stored));
        when(jwtService.generateAccessToken(sampleUser)).thenReturn("new-access-token");
        when(jwtService.getAccessTokenExpirationMs()).thenReturn(900_000L);

        AuthResponse response = authService.refresh(new RefreshRequest(rawToken));

        assertThat(response.accessToken()).isEqualTo("new-access-token");
        assertThat(stored.isRevoked()).isTrue();
        verify(refreshTokenRepository, times(2)).save(any(RefreshToken.class));
    }

    @Test
    void refresh_revokedToken_throwsUnauthorized() {
        String rawToken = "raw-refresh-token";
        RefreshToken stored = new RefreshToken(sampleUser, TokenHasher.sha256(rawToken), LocalDateTime.now().plusDays(1));
        stored.setRevoked(true);

        when(refreshTokenRepository.findByTokenHash(TokenHasher.sha256(rawToken))).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest(rawToken)))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void refresh_expiredToken_throwsUnauthorized() {
        String rawToken = "raw-refresh-token";
        RefreshToken stored = new RefreshToken(sampleUser, TokenHasher.sha256(rawToken), LocalDateTime.now().minusMinutes(1));

        when(refreshTokenRepository.findByTokenHash(TokenHasher.sha256(rawToken))).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest(rawToken)))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void refresh_unknownToken_throwsUnauthorized() {
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest("never-issued")))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void logout_revokesCallersOwnToken() {
        String rawToken = "raw-refresh-token";
        RefreshToken stored = new RefreshToken(sampleUser, TokenHasher.sha256(rawToken), LocalDateTime.now().plusDays(1));

        when(refreshTokenRepository.findByTokenHash(TokenHasher.sha256(rawToken))).thenReturn(Optional.of(stored));

        authService.logout(1L, new RefreshRequest(rawToken));

        assertThat(stored.isRevoked()).isTrue();
        verify(refreshTokenRepository).save(stored);
    }

    @Test
    void logout_tokenBelongsToSomeoneElse_throwsUnauthorized() {
        String rawToken = "raw-refresh-token";
        RefreshToken stored = new RefreshToken(sampleUser, TokenHasher.sha256(rawToken), LocalDateTime.now().plusDays(1));

        when(refreshTokenRepository.findByTokenHash(TokenHasher.sha256(rawToken))).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> authService.logout(999L, new RefreshRequest(rawToken)))
                .isInstanceOf(UnauthorizedException.class);

        verify(refreshTokenRepository, never()).save(any());
    }
}
