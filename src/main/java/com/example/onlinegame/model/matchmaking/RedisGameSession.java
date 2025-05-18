package com.example.onlinegame.model.matchmaking;


import com.example.onlinegame.model.dota2.Item;
import com.example.onlinegame.model.matchmaking.status.GameStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;

import java.util.*;

import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@RedisHash("GameSession")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RedisGameSession {
    @Id
    private String roomId;
    private String sessionId;
    private Long matchId;
    private Long userId;
    private String userName;
    private Set<Long> playerIds;
    private Long targetHeroId;
    private List<Long> itemIds;
    private List<Long> backpackIds;
    private Long neutralItemId;
    private GameStatus status;
    private Long winnerId;
    private Integer currentRound;
    private Map<Long, Long> currentVotes;
    private Integer timeLeft;
    private Long roundStartTime;


    @Builder.Default
    @TimeToLive(unit = TimeUnit.MINUTES)
    private Long ttl = 60L; // Автоудаление через час

}