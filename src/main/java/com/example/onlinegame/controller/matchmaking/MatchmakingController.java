package com.example.onlinegame.controller.matchmaking;


import com.example.onlinegame.dto.request.MatchmakingRequest;
import com.example.onlinegame.dto.session.GameSessionDTO;
import com.example.onlinegame.dto.session.ItemDTO;
import com.example.onlinegame.dto.session.PlayerDTO;
import com.example.onlinegame.model.matchmaking.GameSession;
import com.example.onlinegame.model.matchmaking.GameStatus;
import com.example.onlinegame.model.matchmaking.RedisGameSession;
import com.example.onlinegame.repo.matchmaking.GameSessionRedisRepository;
import com.example.onlinegame.service.matchmaking.MatchmakingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/matchmaking")
@RequiredArgsConstructor
@Slf4j
public class MatchmakingController {
    private final MatchmakingService matchmakingService;
    private final GameSessionRedisRepository redisRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @PostMapping("/find")
    public ResponseEntity<GameSessionDTO> findMatch(@RequestBody MatchmakingRequest request) {
        try {
            GameSessionDTO session = matchmakingService.findMatch(request.getUserId());

            // Рассылаем обновление всем игрокам в сессии
            session.getPlayers().forEach(player ->
                    messagingTemplate.convertAndSend(
                            "/topic/matchmaking/" + player.getId(),
                            session
                    )
            );

            return ResponseEntity.ok(session);
        } catch (Exception e) {
            log.error("Error in findMatch", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/cancel")
    public ResponseEntity<Void> cancelMatchmaking(@RequestBody MatchmakingRequest request) {
        try {
            matchmakingService.cancelMatchmaking(request.getUserId());

            // Удаляем сессию из Redis
            redisRepository.findWaitingSessions().stream()
                    .map(redisRepository::findSession)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .filter(session -> session.getPlayerIds().contains(request.getUserId()))
                    .forEach(session -> redisRepository.removeSession(session.getSessionId()));

            messagingTemplate.convertAndSend(
                    "/topic/matchmaking/" + request.getUserId(),
                    Map.of("status", "CANCELLED")
            );

            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Error in cancelMatchmaking", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/status/{userId}")
    public ResponseEntity<String> getMatchmakingStatus(@PathVariable String userId) {
        try {
            // Проверяем сначала активные сессии в Redis
            Optional<RedisGameSession> activeSession = redisRepository.findWaitingSessions().stream()
                    .map(redisRepository::findSession)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .filter(session -> session.getPlayerIds().contains(userId))
                    .findFirst();

            if (activeSession.isPresent()) {
                RedisGameSession session = activeSession.get();
                return ResponseEntity.ok(session.getStatus().toString());
            }

            // Если в Redis ничего не найдено, возвращаем NOT_SEARCHING
            return ResponseEntity.ok("NOT_SEARCHING");

        } catch (Exception e) {
            log.error("Error getting matchmaking status for user {}", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/current-session/{userId}")
    public ResponseEntity<?> getCurrentSession(@PathVariable Long userId) {
        try {
            Optional<GameSession> session = matchmakingService.getCurrentSession(userId);
            return ResponseEntity.ok(session);
        } catch (Exception e) {
            log.error("Error getting current session", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}