package com.example.onlinegame.dto.session;

import com.example.onlinegame.model.matchmaking.RedisGameSession;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class GameStateDTO {
    private String roomId;
    private int currentRound;
    private int timeLeft;
    private Map<Long, Long> votes;
    private List<Long> items;
    private List<Long> backpack;
    private Long neutralItemId;

    public static GameStateDTO from(RedisGameSession session) {
        return GameStateDTO.builder()
                .roomId(session.getRoomId())
                .currentRound(session.getCurrentRound())
                .votes(session.getCurrentVotes())
                .items(session.getItemIds())
                .backpack(session.getBackpackIds())
                .neutralItemId(session.getNeutralItemId())
                .build();
    }
}