package com.example.onlinegame.service.matchmaking;

import com.example.onlinegame.dto.session.GameSessionDTO;
import com.example.onlinegame.dto.session.ItemDTO;
import com.example.onlinegame.dto.session.PlayerDTO;
import com.example.onlinegame.model.matchmaking.GameSession;
import com.example.onlinegame.model.matchmaking.GameStatus;
import com.example.onlinegame.model.matchmaking.RedisGameSession;
import com.example.onlinegame.model.user.User;
import com.example.onlinegame.repo.matchmaking.GameSessionRedisRepository;
import com.example.onlinegame.repo.matchmaking.GameSessionRepository;
import com.example.onlinegame.repo.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class MatchmakingService {
    private final GameService gameService;
    private final GameSessionRepository gameSessionRepository;
    private final GameSessionRedisRepository redisRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final UserRepository userRepository;

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public GameSessionDTO findMatch(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // 1. Проверяем сессию в Redis
        Optional<RedisGameSession> existingRedisSession = redisRepository.findPlayerSession(userId);
        if (existingRedisSession.isPresent()) {
            RedisGameSession redisSession = existingRedisSession.get();

            // Конвертируем данные из Redis в DTO
            GameSessionDTO sessionDTO = redisRepository.convertToGameSessionFromRedisDTO(redisSession);

            // Возвращаем сессию, если она активна
            if (sessionDTO.getStatus() != GameStatus.COMPLETED &&
                    sessionDTO.getStatus() != GameStatus.CANCELLED) {
                return sessionDTO;
            }

            // Если сессия завершена или отменена, удаляем её из Redis
            redisRepository.removeSession(redisSession.getSessionId());
        }

        // 2. Проверяем доступную сессию в базе данных
        Optional<GameSession> existingSession = gameSessionRepository.findByStatusAndSessionPlayersSizeLessThan(
                        GameStatus.WAITING, 2)
                .filter(session -> !session.hasPlayer(userId));

        if (existingSession.isPresent()) {
            return joinExistingSession(user, existingSession.get());
        }

        // 3. Создаём новую сессию
        return createNewSession(user);
    }



    @Transactional
    protected GameSessionDTO createNewSession(User user) {
        Optional<RedisGameSession> redisSessionOpt = redisRepository.findPlayerSession(user.getId());
        if (redisSessionOpt.isPresent()) {
            RedisGameSession redisSession = redisSessionOpt.get();
            return redisRepository.convertToGameSessionFromRedisDTO(redisSession);
        }

        GameSession session = gameService.createGameSession();
        session.setStatus(GameStatus.WAITING); // Устанавливаем начальный статус
        session.addPlayer(user);

        // Проверяем статус после добавления игрока
        updateSessionStatus(session);

        session = gameSessionRepository.save(session);

        // Сохраняем в Redis
        redisRepository.saveSession(RedisGameSession.fromGameSession(session));

        GameSessionDTO sessionDTO = convertToGameSessionDTO(session);

        messagingTemplate.convertAndSend("/topic/matchmaking/" + user.getId(),
                Map.of(
                        "status", session.getStatus().toString(),
                        "gameSession", sessionDTO
                ));

        return sessionDTO;
    }


    @Transactional(isolation = Isolation.SERIALIZABLE)
    protected GameSessionDTO joinExistingSession(User user, GameSession session) {
        // Проверяем, если пользователь уже в сессии, выбрасываем исключение
        if (session.hasPlayer(user.getId())) {
            throw new IllegalStateException("User is already part of the session");
        }

        // Добавляем пользователя в сессию
        session.addPlayer(user);

        // Проверяем и обновляем статус игры
        updateSessionStatus(session);

        // Сохраняем изменения в базе данных
        session = gameSessionRepository.save(session);

        // Обновляем сессию в Redis
        redisRepository.saveSession(RedisGameSession.fromGameSession(session));

        // Конвертируем сессию в DTO
        GameSessionDTO sessionDTO = convertToGameSessionDTO(session);

        // Отправляем уведомление всем игрокам
        GameSession finalSession = session;
        session.getSessionPlayers().forEach(player -> {
            String status = finalSession.getStatus() == GameStatus.IN_PROGRESS ?
                    "IN_PROGRESS" : "WAITING";
            messagingTemplate.convertAndSend("/topic/matchmaking/" + player.getId(),
                    Map.of(
                            "status", status,
                            "gameSession", sessionDTO
                    ));
        });

        // Если игра началась, отправляем дополнительное уведомление
        if (session.getStatus() == GameStatus.IN_PROGRESS) {
            messagingTemplate.convertAndSend("/topic/game/" + session.getRoomId(), sessionDTO);
        }

        return sessionDTO;
    }

    @Transactional
    public void cancelMatchmaking(Long userId) {
        gameSessionRepository
                .findByStatusAndSessionPlayersId(GameStatus.WAITING, userId)
                .ifPresent(session -> {
                    // Удаляем игрока из сессии
                    session.getSessionPlayers().removeIf(player ->
                            player.getId().equals(userId));

                    // Обновляем статус сессии
                    updateSessionStatus(session);

                    if (session.getStatus() == GameStatus.CANCELLED) {
                        gameSessionRepository.delete(session);
                        redisRepository.removeSession(session.getRoomId());
                    } else {
                        session = gameSessionRepository.save(session);
                        redisRepository.saveSession(RedisGameSession.fromGameSession(session));

                        // Уведомляем оставшихся игроков
                        GameSessionDTO sessionDTO = convertToGameSessionDTO(session);
                        session.getSessionPlayers().forEach(player ->
                                messagingTemplate.convertAndSend("/topic/matchmaking/" + player.getId(),
                                        Map.of(
                                                "status", sessionDTO.getStatus().toString(),
                                                "gameSession", sessionDTO
                                        ))
                        );
                    }

                    // Уведомляем игрока, который покинул сессию
                    messagingTemplate.convertAndSend("/topic/matchmaking/" + userId,
                            Map.of("status", "CANCELLED"));
                });
    }

    public Optional<GameSession> getCurrentSession(Long userId) {
        Optional<RedisGameSession> redisSession = redisRepository.findPlayerSession(userId);
        if (redisSession.isPresent()) {
            return  gameSessionRepository.findByRoomId(redisSession.get().getSessionId());
        }
        return Optional.empty();
    }

    // Добавляем метод для обновления статуса сессии
    private void updateSessionStatus(GameSession session) {
        if (session.isFull()) {
            session.setStatus(GameStatus.IN_PROGRESS);
            log.info("Session {} is now IN_PROGRESS with {} players",
                    session.getRoomId(), session.getSessionPlayers().size());
        } else if (session.getSessionPlayers().isEmpty()) {
            session.setStatus(GameStatus.CANCELLED);
            log.info("Session {} is now CANCELLED (no players)",
                    session.getRoomId());
        } else {
            session.setStatus(GameStatus.WAITING);
            log.info("Session {} is now WAITING with {} players",
                    session.getRoomId(), session.getSessionPlayers().size());
        }
    }


    // Вспомогательный метод для конвертации GameSession в DTO
    private GameSessionDTO convertToGameSessionDTO(GameSession session) {
        return GameSessionDTO.builder()
                .roomId(session.getRoomId())
                .matchId(String.valueOf(session.getMatchId()))
                .status(session.getStatus())
                .players(session.getSessionPlayers().stream()
                        .map(player -> PlayerDTO.builder()
                                .id(player.getId())
                                .username(player.getUsername())
                                .build())
                        .collect(Collectors.toList()))
                .items(session.getItems().stream()
                        .map(item -> ItemDTO.builder()
                                .id(item.getId())
                                .name(item.getDname())
                                .img(item.getImg())
                                .cost(item.getCost())
                                .build())
                        .collect(Collectors.toList()))
                .backpacks(session.getBackpack().stream()
                        .map(item -> ItemDTO.builder()
                                .id(item.getId())
                                .name(item.getDname())
                                .img(item.getImg())
                                .cost(item.getCost())
                                .build())
                        .collect(Collectors.toList()))
                .neutralItem(session.getNeutralItem() != null ?
                        ItemDTO.builder()
                                .id(session.getNeutralItem().getId())
                                .name(session.getNeutralItem().getDname())
                                .img(session.getNeutralItem().getImg())
                                .cost(session.getNeutralItem().getCost())
                                .build()
                        : null)
                .build();
    }
}