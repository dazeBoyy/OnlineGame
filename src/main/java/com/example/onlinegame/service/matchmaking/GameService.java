package com.example.onlinegame.service.matchmaking;

import com.example.onlinegame.dto.session.GameStateDTO;
import com.example.onlinegame.exception.GameEndException;
import com.example.onlinegame.exception.GameSessionNotFoundException;
import com.example.onlinegame.exception.PlayerAlreadyVotedException;
import com.example.onlinegame.exception.PlayerNotInSessionException;
import com.example.onlinegame.model.matchmaking.*;
import com.example.onlinegame.model.matchmaking.status.GameStatus;
import com.example.onlinegame.repo.matchmaking.GameSessionRedisRepository;
import com.example.onlinegame.repo.matchmaking.GameSessionRepository;
import com.example.onlinegame.service.matchmaking.timer.RoundTimerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class GameService {
    private final GameSessionRedisRepository redisRepo;
    private final GameSessionRepository dbRepo;
    private final OpenDotaService openDotaService;
    private final SimpMessagingTemplate messagingTemplate;

    @Lazy
    private final RoundTimerService roundTimerService;
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
                .currentRound(0)
                .timeLeft(30)
                .ttl(60L)
                .build();
        log.info("Created new session" + session.getRoomId() + ":" + session.getPlayerIds() + ":" + session.getTimeLeft());
        redisRepo.saveSession(session);

        // Запускаем таймер первого раунда
        startNewRound(session); // Важно! Первый раунд стартует сразу

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
        boolean isRoundComplete = session.getCurrentVotes().size() >= session.getPlayerIds().size()
                || session.getTimeLeft() <= 0;

        if (isRoundComplete) {
            resolveRound(session);
            broadcastUpdate(session);
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
            startNewRound(session);
        }
    }

    // Начало нового раунда
    public void startNewRound(RedisGameSession session) {
        // 1. Обновляем состояние сессии
        int newRoundNumber = session.getCurrentRound() + 1;
        session.setCurrentRound(newRoundNumber);
        session.setTimeLeft(RoundTimerService.ROUND_DURATION); // Используем константу из сервиса
        session.setRoundStartTime(System.currentTimeMillis()); // Добавляем время начала раунда
        redisRepo.saveSession(session);

        // 2. Запускаем таймер раунда
        roundTimerService.startRoundTimer(session.getRoomId());

        // 3. Отправляем уведомление клиентам
        Map<String, Object> message = new HashMap<>();
        message.put("type", "NEW_ROUND");
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
            // 1. Останавливаем таймер (если активен)
            roundTimerService.stopRoundTimer(session.getRoomId());

            // 2. Обновляем статус игры
            session.setStatus(GameStatus.FINISHED);
            session.setWinnerId(winnerId);

            // 3. Сохраняем в основную БД
            saveToDatabase(session);
            log.info("Игра законченна в - комнате номер: {}, Победитель: {}", session.getRoomId(), winnerId);

            // 4. Удаляем из Redis
            redisRepo.deleteSessionAndRelatedData(session.getRoomId());

            // 5. Отправляем финальные результаты
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
                .targetHeroId(redisSession.getTargetHeroId())
                .itemIds(redisSession.getItemIds())
                .backpackIds(redisSession.getBackpackIds())
                .neutralItemId(redisSession.getNeutralItemId())
                .winnerId(redisSession.getWinnerId())
                .totalRounds(redisSession.getCurrentRound())
                .status(redisSession.getStatus())
                .build();
        dbSession.finishGame();
        log.info("Save to database: " + dbSession);
        dbRepo.save(dbSession);
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