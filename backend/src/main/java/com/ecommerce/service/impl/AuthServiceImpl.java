package com.ecommerce.service.impl;

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
import com.ecommerce.service.AuthService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserMapper userMapper;
    private final long refreshTokenExpirationMs;

    public AuthServiceImpl(UserRepository userRepository,
                            RefreshTokenRepository refreshTokenRepository,
                            PasswordEncoder passwordEncoder,
                            JwtService jwtService,
                            UserMapper userMapper,
                            @Value("${app.jwt.refresh-token-expiration-ms:604800000}") long refreshTokenExpirationMs) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.userMapper = userMapper;
        this.refreshTokenExpirationMs = refreshTokenExpirationMs;
    }

    @Override
    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateEmailException(request.getEmail());
        }
        // Registration can never self-assign ADMIN: the role is hardcoded here,
        // request DTOs for registration don't even expose a role field.
        User user = new User(request.getEmail(), passwordEncoder.encode(request.getPassword()),
                request.getFullName(), Role.CUSTOMER);
        user = userRepository.save(user);
        return userMapper.toResponse(user);
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid email or password");
        }

        return issueTokenPair(user);
    }

    @Override
    @Transactional
    public AuthResponse refresh(RefreshRequest request) {
        String hash = TokenHasher.sha256(request.getRefreshToken());
        RefreshToken existing = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new UnauthorizedException("Invalid or expired refresh token"));

        if (!existing.isUsable()) {
            throw new UnauthorizedException("Invalid or expired refresh token");
        }

        // Rotate: the presented refresh token is single-use. Revoking it here means a
        // stolen-and-replayed old refresh token stops working the moment the
        // legitimate client refreshes.
        existing.setRevoked(true);
        refreshTokenRepository.save(existing);

        return issueTokenPair(existing.getUser());
    }

    @Override
    @Transactional
    public void logout(Long callerId, RefreshRequest request) {
        String hash = TokenHasher.sha256(request.getRefreshToken());
        RefreshToken existing = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new UnauthorizedException("Invalid or expired refresh token"));

        if (!existing.getUser().getId().equals(callerId)) {
            // Don't reveal whether the token belongs to someone else -- same message
            // as "doesn't exist" / "already expired".
            throw new UnauthorizedException("Invalid or expired refresh token");
        }

        existing.setRevoked(true);
        refreshTokenRepository.save(existing);
    }

    private AuthResponse issueTokenPair(User user) {
        String accessToken = jwtService.generateAccessToken(user);

        String rawRefreshToken = TokenHasher.newOpaqueToken();
        RefreshToken refreshToken = new RefreshToken(
                user,
                TokenHasher.sha256(rawRefreshToken),
                LocalDateTime.now().plus(Duration.ofMillis(refreshTokenExpirationMs)));
        refreshTokenRepository.save(refreshToken);

        long expiresInSeconds = jwtService.getAccessTokenExpirationMs() / 1000;
        return new AuthResponse(accessToken, rawRefreshToken, "Bearer", expiresInSeconds);
    }
}
