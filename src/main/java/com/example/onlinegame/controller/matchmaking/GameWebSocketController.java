package com.example.onlinegame.controller.matchmaking;

import com.example.onlinegame.dto.request.GuessRequest;
import com.example.onlinegame.error.ErrorResponse;
import com.example.onlinegame.security.UserPrincipal;
import com.example.onlinegame.service.matchmaking.GameService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
@Slf4j
public class GameWebSocketController {
    private final GameService gameService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/game/guess")
    public void processGuess(@Payload GuessRequest request, @AuthenticationPrincipal UserPrincipal  principal) {

        try {
            gameService.processGuess(
                    request.getRoomId(),
                    principal.getUserId(),
                    request.getHeroId()
            );

        } catch (Exception e) {
            log.error("Error processing guess: {}", e.getMessage());
            messagingTemplate.convertAndSendToUser(
                    principal.getUsername(),
                    "/queue/errors",
                    ErrorResponse.create(
                            e,
                            HttpStatus.BAD_REQUEST,
                            "Error processing guess"
                    )
            );
        }
    }
}