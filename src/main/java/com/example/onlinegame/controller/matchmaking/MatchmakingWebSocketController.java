package com.example.onlinegame.controller.matchmaking;

import com.example.onlinegame.dto.session.GameSessionDTO;
import com.example.onlinegame.dto.session.PlayerDTO;
import com.example.onlinegame.model.matchmaking.GameSession;
import com.example.onlinegame.service.matchmaking.MatchmakingService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class MatchmakingWebSocketController {
    private final MatchmakingService matchmakingService;

    @MessageMapping("/matchmaking/find")
    @SendTo("/topic/matchmaking/{userId}")
    public GameSessionDTO findMatch(Long userId) {
        GameSessionDTO session = matchmakingService.findMatch(userId);
        return session;
    }

    @MessageMapping("/matchmaking/cancel")
    @SendTo("/topic/matchmaking/{userId}")
    public Map<String, String> cancelMatchmaking(Long userId) {
        matchmakingService.cancelMatchmaking(userId);
        return Map.of("status", "CANCELLED");
    }
}