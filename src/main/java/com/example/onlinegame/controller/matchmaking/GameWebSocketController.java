package com.example.onlinegame.controller.matchmaking;

import lombok.AllArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
@AllArgsConstructor
public class GameWebSocketController {

    private SimpMessagingTemplate template;

    @MessageMapping("/vote")
    public void vote(String roomId, String userId, Integer heroId) {
        // Обработка голоса
        template.convertAndSend("/topic/game/" + roomId, "Vote received");
    }
}