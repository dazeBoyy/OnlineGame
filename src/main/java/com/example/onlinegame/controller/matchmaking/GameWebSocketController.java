package com.example.onlinegame.controller.matchmaking;

import com.example.onlinegame.dto.request.GuessRequest;
import com.example.onlinegame.model.matchmaking.GameSession;
import com.example.onlinegame.model.matchmaking.GameStatus;
import com.example.onlinegame.repo.game.HeroRepository;
import com.example.onlinegame.service.matchmaking.GameService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.HashMap;
import java.util.Map;

@Controller
@RequiredArgsConstructor
@Slf4j
public class GameWebSocketController {
    private final GameService gameService;
    private final SimpMessagingTemplate messagingTemplate;
    private final HeroRepository heroRepository;

    @MessageMapping("/game/guess")
    public void guess(GuessRequest request) {
        log.info("[{}] Received guess request: roomId={}, userId={}, heroId={}",
                request.getUserId(), request.getRoomId(), request.getHeroId());

        try {
            // Выполняем логику угадывания
            GameSession session = gameService.makeGuess(request.getRoomId(), request.getUserId(), request.getHeroId());

            // Формируем уведомление
            Map<String, Object> gameUpdate = new HashMap<>();
            gameUpdate.put("status", session.getStatus());
            gameUpdate.put("roomId", session.getRoomId());

            if (session.getStatus() == GameStatus.COMPLETED) {
                // Если игра завершена
                gameUpdate.put("winnerId", session.getWinnerId());
                gameUpdate.put("heroName", heroRepository.findById(session.getHeroId()).get().getName());
                log.info("Game completed. Winner: {}", session.getWinnerId());
            } else {
                // Если предположение неверное
                gameUpdate.put("incorrectGuess", true);
                gameUpdate.put("guesserId", request.getUserId());
                gameUpdate.put("guessedHeroName", heroRepository.findById(request.getHeroId()).get().getName());
                log.info("Incorrect guess by user [{}] in room [{}] incorrect hero [{}]", request.getUserId(), request.getRoomId(), heroRepository.findById(request.getHeroId()).get().getName());
            }

            // Отправляем уведомление в комнату
            String gameTopicDestination = "/topic/game/" + request.getRoomId();
            messagingTemplate.convertAndSend(gameTopicDestination, gameUpdate);

        } catch (Exception e) {
            log.error("[{}] Error processing guess: {}", request.getUserId(), e.getMessage(), e);

            // Отправляем сообщение об ошибке
            Map<String, Object> errorData = Map.of(
                    "error", e.getMessage(),
                    "userId", request.getUserId()
            );

            String errorTopicDestination = "/topic/game/" + request.getRoomId() + "/error";
            messagingTemplate.convertAndSend(errorTopicDestination, errorData);
        }
    }
}