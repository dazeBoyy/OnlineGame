package com.example.onlinegame.model.matchmaking;


import com.example.onlinegame.model.dota2.Item;
import com.example.onlinegame.model.user.User;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;

import java.util.*;
import java.util.stream.Collectors;
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RedisGameSession {
    private String sessionId;
    private Long dbId;
    private String roomId;
    private Long matchId;
    private Long heroId;
    private List<Long> itemIds;
    private List<Long> backpackIds;
    private Long neutralItemId;
    private Set<Long> playerIds;
    private GameStatus status;
    private Long winnerId;
    private Long createdAt;

    public static RedisGameSession fromGameSession(GameSession session) {
        return RedisGameSession.builder()
                .sessionId(session.getRoomId())
                .dbId(session.getId())
                .roomId(session.getRoomId())
                .matchId(session.getMatchId())
                .heroId(session.getHeroId())
                .itemIds(session.getItems().stream()
                        .map(Item::getId)
                        .collect(Collectors.toList()))
                .backpackIds(session.getBackpack().stream()
                        .map(Item::getId)
                        .collect(Collectors.toList()))
                .neutralItemId(Optional.ofNullable(session.getNeutralItem())
                        .map(Item::getId)
                        .orElse(null))
                .playerIds(session.getSessionPlayers().stream()
                        .map(User::getId)
                        .collect(Collectors.toSet()))
                .status(session.getStatus())
                .winnerId(session.getWinnerId())
                .createdAt(System.currentTimeMillis())
                .build();
    }
}