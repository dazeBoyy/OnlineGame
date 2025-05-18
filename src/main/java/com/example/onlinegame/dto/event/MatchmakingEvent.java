package com.example.onlinegame.dto.event;

import com.example.onlinegame.model.matchmaking.status.MatchmakingStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@AllArgsConstructor
@Builder
public class MatchmakingEvent {
    private MatchmakingStatus status;
    private String message;
}