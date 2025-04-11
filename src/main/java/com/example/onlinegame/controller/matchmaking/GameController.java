package com.example.onlinegame.controller.matchmaking;

import com.example.onlinegame.dto.session.GameSessionDTO;
import com.example.onlinegame.dto.session.PlayerDTO;
import com.example.onlinegame.model.matchmaking.GameSession;

import com.example.onlinegame.model.matchmaking.RedisGameSession;
import com.example.onlinegame.repo.matchmaking.GameSessionRedisRepository;
import com.example.onlinegame.service.matchmaking.GameService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/game")
@RequiredArgsConstructor
public class GameController {
    private final GameService gameService;
    private final GameSessionRedisRepository redisRepository;

    @GetMapping("/{roomId}")
    public ResponseEntity<GameSessionDTO> getSession(@PathVariable String roomId) {
        // Сначала проверяем в Redis
        Optional<RedisGameSession> redisSession = redisRepository.findSession(roomId);
        if (redisSession.isPresent()) {
            return ResponseEntity.ok(gameService.toDTO(gameService.getSession(roomId)));
        }

        // Если нет в Redis, ищем в БД
        try {
            GameSession session = gameService.getSession(roomId);
            // Кешируем в Redis для будущих запросов
            redisRepository.saveSession(RedisGameSession.fromGameSession(session));
            return ResponseEntity.ok(gameService.toDTO(session));
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

}