package com.example.onlinegame.service.matchmaking.timer;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoundTimerService {
    private final SimpMessagingTemplate messagingTemplate;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ConcurrentMap<String, ScheduledFuture<?>> activeTimers = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AtomicBoolean> timerFlags = new ConcurrentHashMap<>();

    public static final int ROUND_DURATION = 30;
    private static final int UPDATE_INTERVAL = 1;
    private static final String ROUND_TIMER_KEY = "game:round_timer:";

    private final TaskScheduler taskScheduler;

    public void startRoundTimer(String roomId, Runnable onComplete) {

        timerFlags.computeIfAbsent(roomId, k -> new AtomicBoolean()).set(false);


        String redisKey = ROUND_TIMER_KEY + roomId;
        log.info("[TIMER START] Room: {}, Duration: {}s", roomId, ROUND_DURATION);

        try {
            redisTemplate.execute((RedisCallback<String>) connection -> {
                connection.del(redisKey.getBytes());
                connection.setEx(redisKey.getBytes(), ROUND_DURATION, "active".getBytes());
                return null;
            });

            AtomicBoolean timerFlag = timerFlags.get(roomId);

            ScheduledFuture<?> future = taskScheduler.scheduleAtFixedRate(() -> {
                if (timerFlag.get()) {
                    return;
                }

                try {
                    Long remaining = redisTemplate.getExpire(redisKey, TimeUnit.SECONDS);

                    if (remaining == null || remaining <= 0) {
                        if (timerFlag.compareAndSet(false, true)) {
                            log.info("[TIMER COMPLETE] Room: {}", roomId);
                            try {
                                onComplete.run();
                            } finally {
                                stopRoundTimer(roomId);
                            }
                        }
                    } else {
                        log.info("[TIMER] Room: {}, Duration: {}s", roomId, remaining);
                        sendTimeUpdate(roomId, remaining);
                    }
                } catch (Exception e) {
                    log.error("[TIMER ERROR] Room: {}", roomId, e);
                }
            }, Duration.ofSeconds(UPDATE_INTERVAL));

            activeTimers.put(roomId, future);

        } catch (Exception e) {
            log.error("Failed to start timer for room {}", roomId, e);
            throw new RuntimeException("Timer start failed", e);
        }
    }

    public void stopRoundTimer(String roomId) {
        String timerKey = ROUND_TIMER_KEY + roomId;
        try {

            AtomicBoolean timerFlag = timerFlags.get(roomId);
            if (timerFlag != null) {
                timerFlag.set(true);
            }

            ScheduledFuture<?> timer = activeTimers.remove(roomId);
            if (timer != null) {
                timer.cancel(false);
            }

            Long remainingBefore = redisTemplate.getExpire(timerKey, TimeUnit.SECONDS);
            redisTemplate.delete(timerKey);
            Long remainingAfter = redisTemplate.getExpire(timerKey, TimeUnit.SECONDS);

            log.info("[TIMER STOP] Room: {}, Stopped: true, Before: {}s, After: {}s, Timer Cancelled: {}",
                    roomId, remainingBefore, remainingAfter, timer != null);

        } catch (Exception e) {
            log.error("[STOP ERROR] Room: {} - {}", roomId, e.getMessage());
        }
    }

    @PreDestroy
    public void shutdown() {
        activeTimers.forEach((roomId, timer) -> {
            timer.cancel(false);
            log.info("[SHUTDOWN] Cancelled timer for room: {}", roomId);
        });
        activeTimers.clear();
        timerFlags.clear();
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
}