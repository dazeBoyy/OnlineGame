package com.example.onlinegame.dto.event;

import com.example.onlinegame.model.matchmaking.status.MatchmakingStatus;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SessionEvent {
    private String roomId;
    private MatchmakingStatus status;
    private Object data;

    public SessionEvent(String roomId, MatchmakingStatus status) {
        this(roomId, status, null);
    }
}