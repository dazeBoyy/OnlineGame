package com.example.onlinegame.repo.user;

import com.example.onlinegame.model.user.RefreshToken;
import com.example.onlinegame.model.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByToken(String token);

    Optional<RefreshToken> findByUserAndIsActiveTrue(User user);

}