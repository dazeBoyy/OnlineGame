package com.example.onlinegame.service.matchmaking;

import com.example.onlinegame.dto.event.MatchmakingEvent;
import com.example.onlinegame.dto.event.SessionEvent;
import com.example.onlinegame.dto.session.GameSessionDTO;
import com.example.onlinegame.dto.session.ItemDTO;
import com.example.onlinegame.dto.session.PlayerDTO;
import com.example.onlinegame.exception.AlreadyInSessionException;
import com.example.onlinegame.exception.MatchmakingException;
import com.example.onlinegame.exception.UserNotFoundException;
import com.example.onlinegame.model.matchmaking.GameSession;
import com.example.onlinegame.model.matchmaking.status.GameStatus;
import com.example.onlinegame.model.matchmaking.status.MatchmakingStatus;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class MatchmakingService {
    private final GameSessionRedisRepository redisRepo;
    private final GameSessionRepository dbRepo;
    private final UserRepository userRepo;
    private final SimpMessagingTemplate messagingTemplate;
    private final GameService gameService;
    private final ItemCache itemCache;

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public GameSessionDTO findMatch(Long userId) {
        try {
            // 1. Проверяем существование пользователя
            User user = userRepo.findById(userId)
                    .orElseThrow(() -> new UserNotFoundException(userId));

            // 2. Проверяем и обрабатываем возможные конфликты
            Optional<RedisGameSession> existingSession = redisRepo.findPlayerSession(user.getId());
            if (existingSession.isPresent()) {
                RedisGameSession session = existingSession.get();
                if (session.getStatus() == GameStatus.IN_PROGRESS) {
                    log.warn("Пользователь {} уже в активной сессии {}", user.getId(), session.getRoomId());
                    return convertToDto(session);
                }
            }
            // 3. Проверяем, не находится ли пользователь уже в очереди
            if (redisRepo.isUserInQueue(user.getId())) {
                log.warn("Пользователь {} уже в очереди поиска", user.getId());
                return GameSessionDTO.builder()
                        .roomId(null)
                        .status(GameStatus.SEARCHING)
                        .players(List.of(convertToPlayerDto(user.getId())))
                        .build();
            }

            // 4. Начинаем поиск матча
            log.info("Пользователь {} начал поиск матча", user.getId());
            notifyUser(user.getId(), MatchmakingStatus.SEARCHING, "Поиск соперника...");
            redisRepo.addToQueue(user.getId());

            // 5. Пытаемся найти пару
            List<Long> players = findTwoDistinctPlayers(user.getId());
            if (players.size() == 2) {
                log.info("Найдена пара: {}", players);
                redisRepo.trimQueue(2);
                return convertToDto(gameService.createSession(players));
            }

            return GameSessionDTO.builder()
                    .roomId(null)
                    .status(GameStatus.SEARCHING)
                    .players(List.of(convertToPlayerDto(user.getId())))
                    .build();

        } catch (Exception e) {
            log.error("Ошибка поиска матча для пользователя {}", userId, e);
            cleanupUserState(userId);
            handleMatchmakingError(userId, e);
            throw new MatchmakingException("Не удалось найти матч" + e);
        }
    }

    private void cleanupUserState(Long userId) {
        try {
            // Удаляем из очереди
            redisRepo.removeFromQueue(userId);

            // Проверяем сессию (если была создана ошибочно)
            redisRepo.findPlayerSession(userId).ifPresent(session -> {
                if (session.getPlayerIds().size() == 1) {
                    redisRepo.deleteSessionAndRelatedData(session.getRoomId());
                } else {
                    session.getPlayerIds().remove(userId);
                    redisRepo.saveSession(session);
                }
            });
        } catch (Exception e) {
            log.error("Ошибка очистки состояния для пользователя {}", userId, e);
        }
    }

    private List<Long> findTwoDistinctPlayers(Long currentUserId) {
        List<Long> candidates = redisRepo.pollTwoPlayers();

        // Фильтруем дубликаты и текущего пользователя
        List<Long> distinctPlayers = candidates.stream()
                .distinct()
                .filter(id -> !id.equals(currentUserId))
                .collect(Collectors.toList());

        // Если нашли хотя бы одного уникального соперника
        if (distinctPlayers.size() >= 1) {
            return List.of(currentUserId, distinctPlayers.get(0));
        }

        return List.of();
    }

    @Transactional
    public void cancelMatchmaking(Long userId) {
        try {
            // Удаление из очереди
            redisRepo.removeFromQueue(userId);

            // Отмена активной сессии
            redisRepo.findPlayerSession(userId).ifPresent(session -> {
                if (session.getPlayerIds().size() == 1) {
                    redisRepo.deleteSessionAndRelatedData(session.getRoomId());
                    notifySession(session, MatchmakingStatus.CANCELLED);
                } else {
                    session.getPlayerIds().remove(userId);
                    redisRepo.saveSession(session);
                    notifySession(session, MatchmakingStatus.PLAYER_LEFT);
                }
            });

            notifyUser(userId, MatchmakingStatus.CANCELLED, "Поиск отменён");

        } catch (Exception e) {
            log.error("Ошибка отмены поиска: {}", userId, e);
            handleMatchmakingError(userId, e);
        }
    }

    public Optional<GameSessionDTO> getCurrentSession(Long userId) {
        // Проверяем активную сессию в Redis
        Optional<GameSessionDTO> activeSession = redisRepo.findPlayerSession(userId)
                .map(this::convertToDto)
                .filter(dto -> dto.getStatus() != GameStatus.FINISHED);

        if (activeSession.isPresent()) {
            return activeSession;
        }

        return dbRepo.findByPlayerId(userId)
                .map(this::convertToDto)
                .filter(dto -> dto.getStatus() == GameStatus.FINISHED)
                .filter(dto -> dto.getFinishTime().isAfter(LocalDateTime.now().minusHours(24)));
    }


    private GameSessionDTO convertToDto(RedisGameSession session) {
        return GameSessionDTO.builder()
                .roomId(session.getRoomId())
                .status(session.getStatus())
                .players(session.getPlayerIds().stream()
                        .map(this::convertToPlayerDto)
                        .collect(Collectors.toList()))
                .items(convertItems(session.getItemIds()))
                .backpacks(convertItems(session.getBackpackIds()))
                .neutralItem(convertItem(session.getNeutralItemId()))
                .currentRound(session.getCurrentRound())
                .build();
    }

    private GameSessionDTO convertToDto(GameSession entity) {
        return GameSessionDTO.builder()
                .roomId(entity.getRoomId())
                .status(entity.getStatus())
                .players(entity.getPlayerIds().stream()
                        .map(this::convertToPlayerDto)
                        .collect(Collectors.toList()))                .items(convertItems(entity.getItemIds()))
                .backpacks(convertItems(entity.getBackpackIds()))
                .neutralItem(convertItem(entity.getNeutralItemId()))
                .finishTime(entity.getFinishedAt())
                .build();
    }

    private PlayerDTO convertToPlayerDto(Long userId) {
        return userRepo.findById(userId)
                .map(user -> new PlayerDTO(user.getId(), user.getUsername()))
                .orElse(new PlayerDTO(userId, "Unknown"));
    }

    private List<ItemDTO> convertItems(List<Long> itemIds) {
        return itemIds.stream()
                .map(this::convertItem)
                .collect(Collectors.toList());
    }

    private ItemDTO convertItem(Long itemId) {
        return itemCache.getItem(itemId);
    }

    private void notifyUser(Long userId, MatchmakingStatus status, String message) {
        messagingTemplate.convertAndSendToUser(
                userId.toString(),
                "/queue/matchmaking",
                new MatchmakingEvent(status, message));
    }

    private void notifySession(RedisGameSession session, MatchmakingStatus status) {
        session.getPlayerIds().forEach(playerId ->
                messagingTemplate.convertAndSendToUser(
                        playerId.toString(),
                        "/queue/game",
                        new SessionEvent(session.getRoomId(), status)));
    }

    private void handleMatchmakingError(Long userId, Exception e) {
        log.error("Ошибка поиска матча: {}", userId, e);
        notifyUser(userId, MatchmakingStatus.ERROR,
                "Ошибка: " + e.getMessage());

        redisRepo.removeFromQueue(userId);
        redisRepo.findPlayerSession(userId).ifPresent(session -> {
            if (session.getPlayerIds().contains(userId)) {
                session.getPlayerIds().remove(userId);
                redisRepo.saveSession(session);
            }
        });
    }
}