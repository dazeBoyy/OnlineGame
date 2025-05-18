package com.example.onlinegame.service.user;

import com.example.onlinegame.model.user.RefreshToken;
import com.example.onlinegame.model.user.User;
import com.example.onlinegame.repo.user.RefreshTokenRepository;
import com.example.onlinegame.repo.user.UserRepository;
import com.example.onlinegame.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;

    public String createRefreshToken(Long userId, long expirationMinutes) {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken(UUID.randomUUID().toString());
        refreshToken.setUser(userRepository.findById(userId).orElseThrow());
        refreshToken.setExpiresAt(LocalDateTime.now().plusMinutes(expirationMinutes));
        refreshToken.setIsActive(true);
        refreshTokenRepository.save(refreshToken);
        return refreshToken.getToken();
    }

    public RefreshToken getRefreshToken(String token) {
        return refreshTokenRepository.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Invalid refresh token"));
    }

    public boolean validateRefreshToken(String token) {
        Optional<RefreshToken> refreshTokenOpt = refreshTokenRepository.findByToken(token);

        if (refreshTokenOpt.isEmpty()) {
            return false;
        }

        RefreshToken refreshToken = refreshTokenOpt.get();
        return refreshToken.getIsActive() && refreshToken.getExpiresAt().isAfter(LocalDateTime.now());
    }

    public void deactivateRefreshToken(String token) {
        Optional<RefreshToken> refreshTokenOpt = refreshTokenRepository.findByToken(token);

        refreshTokenOpt.ifPresent(refreshToken -> {
            refreshToken.setIsActive(false);
            refreshTokenRepository.save(refreshToken);
        });
    }
    public Optional<RefreshToken> findActiveRefreshTokenByUser(Long user) {
        return refreshTokenRepository.findByUserIdAndIsActiveTrue(user);
    }


}