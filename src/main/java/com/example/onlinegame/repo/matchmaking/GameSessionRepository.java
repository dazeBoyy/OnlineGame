package com.example.onlinegame.repo.matchmaking;

import com.example.onlinegame.model.matchmaking.GameSession;
import com.example.onlinegame.model.matchmaking.GameStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GameSessionRepository extends JpaRepository<GameSession, Long> {
    Optional<GameSession> findByRoomId(String roomId);

    @Query("SELECT gs FROM GameSession gs WHERE gs.status = :status AND SIZE(gs.sessionPlayers) < :maxPlayers")
    Optional<GameSession> findByStatusAndSessionPlayersSizeLessThan(
            @Param("status") GameStatus status,
            @Param("maxPlayers") int maxPlayers
    );

    @Query("SELECT gs FROM GameSession gs JOIN gs.sessionPlayers sp WHERE gs.status = :status AND sp.id = :playerId")
    Optional<GameSession> findByStatusAndSessionPlayersId(
            @Param("status") GameStatus status,
            @Param("playerId") Long playerId
    );
}