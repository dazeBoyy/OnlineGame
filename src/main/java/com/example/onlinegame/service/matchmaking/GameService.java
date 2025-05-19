package com.example.onlinegame.service.matchmaking;

import com.example.onlinegame.dto.session.GameStateDTO;
import com.example.onlinegame.exception.GameEndException;
import com.example.onlinegame.exception.GameSessionNotFoundException;
import com.example.onlinegame.exception.PlayerAlreadyVotedException;
import com.example.onlinegame.exception.PlayerNotInSessionException;
import com.example.onlinegame.model.matchmaking.*;
import com.example.onlinegame.model.matchmaking.status.GameStatus;
import com.example.onlinegame.model.user.User;
import com.example.onlinegame.repo.matchmaking.GameSessionRedisRepository;
import com.example.onlinegame.repo.matchmaking.GameSessionRepository;
import com.example.onlinegame.repo.user.UserRepository;
import com.example.onlinegame.service.matchmaking.timer.RoundTimerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class GameService {
    private final GameSessionRedisRepository redisRepo;
    private final GameSessionRepository dbRepo;
    private final OpenDotaService openDotaService;
    private final SimpMessagingTemplate messagingTemplate;
    private final UserRepository userRepository;

    private final RoundTimerService roundTimerService;
    private final TaskScheduler taskScheduler;
    // Создание новой игровой сессии
    public RedisGameSession createSession(List<Long> playerIds) {
        OpenDotaMatch match = openDotaService.getRandomMatch();
        OpenDotaPlayer player = openDotaService.getRandomPlayer(match.getMatchId());

        if (new HashSet<>(playerIds).size() < 2) {
            log.warn("Нельзя создать сессию с одинаковыми игроками");
            return null;
        }

        RedisGameSession session = RedisGameSession.builder()
                .roomId(UUID.randomUUID().toString())
                .playerIds(new HashSet<>(playerIds))
                .matchId(match.getMatchId())
                .userId(player.getAccountId())
                .userName(player.getPersonaname())
                .targetHeroId(Long.valueOf(player.getHeroId()))
                .itemIds(convertItems(player.getItems()))
                .backpackIds(convertItems(player.getBackpacks()))
                .neutralItemId(Long.valueOf(player.getNeutralItem()))
                .status(GameStatus.IN_PROGRESS)
                .roundResolved(false)
                .currentRound(0)
                .ttl(60L)
                .build();
        log.info("Created new session" + session.getRoomId() + ":" + session.getPlayerIds());
        redisRepo.saveSession(session);

        // Запускаем таймер первого раунда
        startNewRound(session);

        return session;
    }

    public void processGuess(String roomId, Long userId, Long heroId) {
        RedisGameSession session = redisRepo.findSession(roomId)
                .orElseThrow(() -> new GameSessionNotFoundException(roomId));

        if (!session.getPlayerIds().contains(userId)) {
            throw new PlayerNotInSessionException(userId, roomId);
        }

        if (session.getCurrentVotes().containsKey(userId)) {
            throw new PlayerAlreadyVotedException(userId, roomId);
        }
        log.info("Предположение пользователя добавленно в комнату: " + roomId + " userId: " + userId + " heroId: " + heroId);
        session.getCurrentVotes().put(userId, heroId);
        redisRepo.saveSession(session);

        // Отправляем сообщение в топик комнаты: кто проголосовал
        messagingTemplate.convertAndSend(
                "/topic/game/" + roomId,
                Map.of(
                        "type", "USER_VOTE",
                        "userId", userId
                )
        );

        checkRoundCompletion(session);
    }


    // Проверка завершения раунда
    private void checkRoundCompletion(RedisGameSession session) {
        String roomId = session.getRoomId();

        try {
            synchronized (roomId.intern()) {
                RedisGameSession latestSession = redisRepo.findSession(roomId)
                        .orElseThrow(() -> new GameSessionNotFoundException(roomId));

                if (Boolean.TRUE.equals(latestSession.isRoundResolved())) {
                    log.info("Раунд уже завершён в комнате: {}", roomId);
                    return;
                }

                Long remaining = redisRepo.getRemainingRoundTime(roomId);
                boolean isRoundComplete = latestSession.getCurrentVotes().size() >= latestSession.getPlayerIds().size()
                        || remaining == null || remaining <= 0;

                if (isRoundComplete) {
                    latestSession.setRoundResolved(true);
                    redisRepo.saveSession(latestSession);

                    resolveRound(latestSession);

                    if (latestSession.getStatus().equals(GameStatus.IN_PROGRESS)) {
                        taskScheduler.schedule(() -> {
                            startNewRound(latestSession);
                        }, Instant.now().plusMillis(100));
                    }

                    broadcastUpdate(latestSession);
                }
            }
        } catch (Exception e) {
            log.error("Failed to check round completion for room: {}", roomId, e);
        }
    }


    // Завершение раунда
    public void resolveRound(RedisGameSession session) {
        Optional<Long> winner = session.getCurrentVotes().entrySet().stream()
                .filter(e -> e.getValue().equals(session.getTargetHeroId()))
                .map(Map.Entry::getKey)
                .findFirst();

        if (winner.isPresent()) {
            endGame(session, winner.get());
        } else {
            log.info("Победитель в " + session.getCurrentRound() + " раунде не был найден!");
        }
    }

    // Начало нового раунда
    public void startNewRound(RedisGameSession session) {
        // 1. Обновляем состояние сессии
        int newRoundNumber = session.getCurrentRound() + 1;
        session.setCurrentRound(newRoundNumber);
        session.setRoundStartTime(System.currentTimeMillis());
        session.setCurrentVotes(new HashMap<>());
        session.setRoundResolved(false);
        redisRepo.saveSession(session);

        // 2. Запускаем таймер раунда
        roundTimerService.startRoundTimer(session.getRoomId(), () -> {
            RedisGameSession latest = redisRepo.findSession(session.getRoomId())
                    .orElseThrow(() -> new GameSessionNotFoundException(session.getRoomId()));
            if (!latest.isRoundResolved()) {
                checkRoundCompletion(latest);
            }
        });
        // 3. Отправляем уведомление клиентам
        Map<String, Object> message = new HashMap<>();
        message.put("type", "ROUND");
        message.put("round", newRoundNumber);
        message.put("duration", RoundTimerService.ROUND_DURATION);
        message.put("startTime", session.getRoundStartTime());

        messagingTemplate.convertAndSend(
                "/topic/game/" + session.getRoomId(),
                message
        );

        log.info("Раунд номер: {} в комнате: {}", newRoundNumber, session.getRoomId());
    }

    // Завершение игры
    private void endGame(RedisGameSession session, Long winnerId) {
        try {

            roundTimerService.stopRoundTimer(session.getRoomId());
            session.setStatus(GameStatus.FINISHED);
            session.setWinnerId(winnerId);

            // Обновляем статистику игроков
            updatePlayersStatistics(session);

            // Сохраняем в основную БД
            saveToDatabase(session);
            log.info("Игра закончена в комнате номер: {}, Победитель: {}", session.getRoomId(), winnerId);

            // Удаляем из Redis
            redisRepo.deleteSessionAndRelatedData(session.getRoomId());

            // Отправляем финальные результаты
            broadcastResult(session);

        } catch (Exception e) {
            log.error("Failed to end game for room: " + session.getRoomId(), e);
            throw new GameEndException("Could not properly end the game", e);
        }
    }

    // Сохранение в базу данных
    private void saveToDatabase(RedisGameSession redisSession) {
        GameSession dbSession = GameSession.builder()
                .roomId(redisSession.getRoomId())
                .matchId(redisSession.getMatchId())
                .targetHeroId(redisSession.getTargetHeroId())
                .itemIds(redisSession.getItemIds())
                .backpackIds(redisSession.getBackpackIds())
                .neutralItemId(redisSession.getNeutralItemId())
                .playerIds(redisSession.getPlayerIds())
                .winnerId(redisSession.getWinnerId())
                .totalRounds(redisSession.getCurrentRound())
                .status(redisSession.getStatus())
                .build();
        dbSession.finishGame();
        log.info("Save to database: " + dbSession);
        dbRepo.save(dbSession);
    }

    // Метод для обновления статистики игроков
    private void updatePlayersStatistics(RedisGameSession session) {
        try {
            Long winnerId = session.getWinnerId();
            Set<Long> playerIds = session.getPlayerIds();

            List<User> players = userRepository.findAllById(playerIds);

            for (User player : players) {
                if (player.getId().equals(winnerId)) {
                    player.setWins(player.getWins() + 1);
                    log.info("Увеличено количество побед для игрока {}: {}",
                            player.getId(), player.getWins());
                } else {
                    player.setLosses(player.getLosses() + 1);
                    log.info("Увеличено количество поражений для игрока {}: {}",
                            player.getId(), player.getLosses());
                }
            }
            userRepository.saveAll(players);
            log.info("Статистика обновлена для {} игроков в комнате {}",
                    players.size(), session.getRoomId());

        } catch (Exception e) {
            log.error("Failed to update players statistics for room: " + session.getRoomId(), e);
        }
    }

    // Отправка обновлений игрокам
    private void broadcastUpdate(RedisGameSession session) {
        messagingTemplate.convertAndSend(
                "/topic/game/" + session.getRoomId(),
                GameStateDTO.from(session)
        );
    }

    // Отправка результатов
    private void broadcastResult(RedisGameSession session) {
        Map<String, Object> message = new HashMap<>();
        message.put("type", "GAME_ENDED");
        message.put("roomId", session.getRoomId());
        message.put("winnerId", session.getWinnerId());
        message.put("finalRound", session.getCurrentRound());

        messagingTemplate.convertAndSend(
                "/topic/game/" + session.getRoomId(),
                message
        );
    }

    // Конвертация предметов
    private List<Long> convertItems(List<Integer> items) {
        return items.stream()
                .filter(id -> id != 0)
                .map(Long::valueOf)
                .collect(Collectors.toList());
    }
}