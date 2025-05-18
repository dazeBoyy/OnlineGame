package com.example.onlinegame.service.matchmaking.timer;

import com.example.onlinegame.exception.GameSessionNotFoundException;
import com.example.onlinegame.model.matchmaking.RedisGameSession;
import com.example.onlinegame.repo.matchmaking.GameSessionRedisRepository;
import com.example.onlinegame.service.matchmaking.GameService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoundTimerService {
    private final SimpMessagingTemplate messagingTemplate;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public static final int ROUND_DURATION = 30;
    private static final int UPDATE_INTERVAL = 1;
    private static final String ROUND_TIMER_KEY = "game:round_timer:";
    private final GameSessionRedisRepository redisRepo;


    public void startRoundTimer(String roomId) {
        String redisKey = ROUND_TIMER_KEY + roomId;

        log.info("[TIMER START] Room: {}, Duration: {}s", roomId, ROUND_DURATION);

        try {
            // Удаляем старый таймер если был
            redisTemplate.delete(redisKey);

            // Устанавливаем новый таймер
            redisTemplate.opsForValue().set(
                    redisKey,
                    "active",
                    ROUND_DURATION,
                    TimeUnit.SECONDS
            );

            // Запускаем фоновые обновления
            scheduler.scheduleAtFixedRate(() -> {
                try {
                    Long remaining = redisTemplate.getExpire(redisKey, TimeUnit.SECONDS);

                    if (remaining == null || remaining <= 0) {
                        handleRoundEnd(roomId);
                        throw new RuntimeException("Stop scheduler");
                    } else {
                        sendTimeUpdate(roomId, remaining);
                    }
                } catch (Exception e) {
                    log.error("Timer error for room {}", roomId, e);
                    throw e;
                }
            }, 0, UPDATE_INTERVAL, TimeUnit.SECONDS);

        } catch (Exception e) {
            log.error("Failed to start timer for room {}", roomId, e);
            throw new RuntimeException("Timer start failed", e);
        }
    }


    private void sendTimeUpdate(String roomId, Long timeLeft) {
        try {
            long serverTime = System.currentTimeMillis();
            Map<String, Object> message = Map.of(
                    "type", "TIME_UPDATE",
                    "timeLeft", timeLeft,
                    "serverTime", serverTime
            );

            messagingTemplate.convertAndSend("/topic/game/" + roomId, message);
            log.debug("[TIME UPDATE SENT] Room: {}, TimeLeft: {}, ServerTime: {}",
                    roomId, timeLeft, serverTime);

        } catch (Exception e) {
            log.error("[SEND ERROR] Room: {} - {}", roomId, e.getMessage());
        }
    }

    private void handleRoundEnd(String roomId) {
        try {
            // 1. Получаем текущую сессию
            RedisGameSession session = redisRepo.findSession(roomId)
                    .orElseThrow(() -> new GameSessionNotFoundException(roomId));

            // 2. Проверяем угаданных героев
            boolean hasWinner = session.getCurrentVotes().values()
                    .contains(session.getTargetHeroId());

            if (!hasWinner) {
                log.info("No winner in round {}, starting new round", session.getCurrentRound());
//                gameService.startNewRound(session);
            } else {
                // Если есть победитель, GameService сам вызовет endGame
//                gameService.resolveRound(session);
            }

            // 3. Отправляем уведомление
            messagingTemplate.convertAndSend(
                    "/topic/game/" + roomId,
                    Map.of("type", "ROUND_ENDED")
            );

        } catch (Exception e) {
            log.error("Error handling round end for room {}", roomId, e);
        } finally {
            redisTemplate.delete(ROUND_TIMER_KEY + roomId);
        }
    }

    public void stopRoundTimer(String roomId) {
        String timerKey = ROUND_TIMER_KEY + roomId;
        try {
            Long remainingBefore = redisTemplate.getExpire(timerKey, TimeUnit.SECONDS);
            Boolean stopped = redisTemplate.delete(timerKey);
            Long remainingAfter = redisTemplate.getExpire(timerKey, TimeUnit.SECONDS);

            log.info("[TIMER STOP] Room: {}, Stopped: {}, Before: {}s, After: {}s",
                    roomId, stopped, remainingBefore, remainingAfter);

        } catch (Exception e) {
            log.error("[STOP ERROR] Room: {} - {}", roomId, e.getMessage());
        }
    }
}