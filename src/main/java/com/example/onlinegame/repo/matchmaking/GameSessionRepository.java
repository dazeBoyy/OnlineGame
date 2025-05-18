package com.example.onlinegame.repo.matchmaking;

import com.example.onlinegame.model.matchmaking.GameSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GameSessionRepository extends JpaRepository<GameSession, Long> {

    @Query("SELECT g FROM GameSession g WHERE :userId MEMBER OF g.playerIds")
    Optional<GameSession> findByPlayerId(@Param("userId") Long userId);
}