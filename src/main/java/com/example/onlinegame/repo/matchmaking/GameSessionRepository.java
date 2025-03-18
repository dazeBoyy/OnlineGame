package com.example.onlinegame.repo.matchmaking;

import com.example.onlinegame.model.matchmaking.GameSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GameSessionRepository extends JpaRepository<GameSession, Long> {
    Optional<GameSession> findByRoomId(String roomId);
}