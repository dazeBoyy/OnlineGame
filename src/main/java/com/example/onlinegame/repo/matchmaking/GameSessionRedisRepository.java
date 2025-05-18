package com.example.onlinegame.repo.matchmaking;

import com.example.onlinegame.exception.RedisOperationException;
import com.example.onlinegame.model.matchmaking.status.GameStatus;
import com.example.onlinegame.model.matchmaking.RedisGameSession;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.stream.Collectors;


@Repository
@RequiredArgsConstructor
@Slf4j
public class GameSessionRedisRepository {
    private final RedisTemplate<String, Object> redisTemplate;

    // Keys
    private static final String MATCHMAKING_QUEUE = "matchmaking:queue";
    private static final String SESSION_KEY = "sessions:session:";
    private static final String ROUND_TIMER_KEY = "game:round_timer:";
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void addToQueue(Long userId) {
        // Проверяем, что пользователя еще нет в очереди
        Long count = redisTemplate.opsForList().remove(MATCHMAKING_QUEUE, 1, userId);
        if (count != null && count > 0) {
            log.warn("Пользователь {} уже был в очереди и был удален перед повторным добавлением", userId);
        }

        redisTemplate.opsForList().rightPush(MATCHMAKING_QUEUE, userId);
        log.info("Игрок {} добавлен в очередь", userId);
    }

    public List<Long> pollTwoPlayers() {
        try {
            List<Object> players = redisTemplate.opsForList().range(MATCHMAKING_QUEUE, 0, 1);
            if (players == null || players.size() < 2) {
                log.debug("Not enough players in queue (found: {})", players != null ? players.size() : 0);
                return List.of();
            }

            List<Long> result = players.stream()
                    .map(obj -> Long.valueOf(obj.toString()))
                    .collect(Collectors.toList());

            log.info("Polled 2 players from queue: {}", result);
            return result;
        } catch (Exception e) {
            log.error("Failed to poll players from queue", e);
            throw e;
        }
    }

    // Сохранение сессии в Redis
    public void saveSession(RedisGameSession session) {
        try {
            String sessionKey = SESSION_KEY + session.getRoomId();
            Map<String, String> sessionMap = convertToMap(session);
            redisTemplate.opsForHash().putAll(sessionKey, sessionMap);
            log.debug("Session {} saved successfully", session.getRoomId());
        } catch (Exception e) {
            log.error("Failed to save session {}", session.getRoomId(), e);
            throw e;
        }
    }

    public Optional<RedisGameSession> findSession(String roomId) {
        try {
            String key = SESSION_KEY + roomId;
            Map<Object, Object> data = redisTemplate.opsForHash().entries(key);
            if (data.isEmpty()) {
                log.debug("Session {} not found in Redis", roomId);
                return Optional.empty();
            }

            Map<String, String> stringData = data.entrySet().stream()
                    .collect(Collectors.toMap(
                            e -> String.valueOf(e.getKey()),
                            e -> String.valueOf(e.getValue())
                    ));

            RedisGameSession session = convertFromMap(stringData);
            log.debug("Session {} found and converted successfully", roomId);
            return Optional.of(session);
        } catch (Exception e) {
            log.error("Error converting session data for sessionId: {}", roomId, e);
            return Optional.empty();
        }
    }

    public boolean isUserInQueue(Long userId) {
        Long position = redisTemplate.opsForList().indexOf(MATCHMAKING_QUEUE, userId);
        return position != null && position >= 0;
    }


    public Optional<RedisGameSession> findPlayerSession(Long userId) {
        try {
            Set<String> sessionKeys = redisTemplate.keys(SESSION_KEY + "*");
            if (sessionKeys == null || sessionKeys.isEmpty()) {
                log.debug("No active sessions found for player {}", userId);
                return Optional.empty();
            }

            Optional<RedisGameSession> result = sessionKeys.stream()
                    .map(key -> {
                        try {
                            Map<Object, Object> data = redisTemplate.opsForHash().entries(key);
                            Map<String, String> stringData = data.entrySet().stream()
                                    .collect(Collectors.toMap(
                                            e -> String.valueOf(e.getKey()),
                                            e -> String.valueOf(e.getValue())
                                    ));
                            return convertFromMap(stringData);
                        } catch (Exception e) {
                            log.error("Error processing session with key {}", key, e);
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .filter(session -> session.getPlayerIds().contains(userId))
                    .findFirst();

            if (result.isPresent()) {
                log.debug("Found session for player {}: {}", userId, result.get().getRoomId());
            } else {
                log.debug("No session found for player {}", userId);
            }

            return result;
        } catch (Exception e) {
            log.error("Error finding session for player {}", userId, e);
            return Optional.empty();
        }
    }

    public void deleteSessionAndRelatedData(String roomId) {
        try {
            // Формируем все ключи, которые нужно удалить
            String sessionKey = SESSION_KEY + roomId;
            String timerKey = ROUND_TIMER_KEY + roomId;
            String voteKey = "game:votes:" + roomId; // пример дополнительного ключа

            // Атомарное удаление всех связанных данных
            List<String> keysToDelete = Arrays.asList(sessionKey, timerKey, voteKey);
            Long deletedCount = redisTemplate.delete(keysToDelete);

            if (deletedCount == null || deletedCount == 0) {
                log.warn("No data found to delete for room: {}", roomId);
            } else {
                log.info("Deleted {} items for room: {}", deletedCount, roomId);
            }

            // Дополнительная очистка (если нужно)
            redisTemplate.getConnectionFactory().getConnection()
                    .publish(("game:cleanup:" + roomId).getBytes(), "done".getBytes());

        } catch (Exception e) {
            log.error("Failed to delete session data for room: {}", roomId, e);
            throw new RedisOperationException("Session deletion failed", e);
        }
    }
    public void removeFromQueue(Long userId) {
        try {
            Long removed = redisTemplate.opsForList().remove(MATCHMAKING_QUEUE, 0, userId);
            if (removed != null && removed > 0) {
                log.info("Player {} removed from queue", userId);
            } else {
                log.debug("Player {} not found in queue", userId);
            }
        } catch (Exception e) {
            log.error("Failed to remove player {} from queue", userId, e);
            throw e;
        }
    }

    public void trimQueue(int count) {
        try {
            redisTemplate.opsForList().trim(MATCHMAKING_QUEUE, count, -1);
            log.info("Queue trimmed to {} elements", count);
        } catch (Exception e) {
            log.error("Failed to trim queue", e);
            throw e;
        }
    }

    private Map<String, String> convertToMap(RedisGameSession session) {
        Map<String, String> map = new HashMap<>();

        map.put("sessionId", nullSafe(session.getSessionId()));
        map.put("roomId", nullSafe(session.getRoomId()));
        map.put("matchId", String.valueOf(session.getMatchId()));
        map.put("targetHeroId", String.valueOf(session.getTargetHeroId()));
        map.put("neutralItemId", session.getNeutralItemId() != null ? String.valueOf(session.getNeutralItemId()) : "");
        map.put("winnerId", session.getWinnerId() != null ? String.valueOf(session.getWinnerId()) : "");
        map.put("status", session.getStatus().name());
        map.put("currentRound", String.valueOf(session.getCurrentRound()));
        map.put("timeLeft", String.valueOf(session.getTimeLeft()));

        try {
            map.put("playerIds", objectMapper.writeValueAsString(session.getPlayerIds()));
            map.put("itemIds", objectMapper.writeValueAsString(session.getItemIds()));
            map.put("backpackIds", objectMapper.writeValueAsString(session.getBackpackIds()));
            map.put("currentVotes", objectMapper.writeValueAsString(session.getCurrentVotes()));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Ошибка сериализации сложных полей RedisGameSession", e);
        }

        return map;
    }


    private RedisGameSession convertFromMap(Map<String, String> map) {
        try {
            return RedisGameSession.builder()
                    .roomId(unquote(map.get("roomId")))
                    .sessionId(unquote(map.get("sessionId")))
                    .matchId(toLongSafe(map.get("matchId")))
                    .targetHeroId(toLongSafe(map.get("targetHeroId")))
                    .neutralItemId(toLongSafe(map.get("neutralItemId")))
                    .winnerId(toLongSafe(map.get("winnerId")))
                    .status(GameStatus.valueOf(unquote(map.get("status"))))
                    .currentRound(toIntSafe(map.get("currentRound")))
                    .timeLeft(toIntSafe(map.get("timeLeft")))
                    .playerIds(parseLongSet(map.get("playerIds")))
                    .itemIds(parseLongList(map.get("itemIds")))
                    .backpackIds(parseLongList(map.get("backpackIds")))
                    .currentVotes(parseLongMap(map.get("currentVotes")))
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Ошибка при десериализации RedisGameSession", e);
        }
    }

    private String nullSafe(String s) {
        return s == null ? "" : s;
    }


    private String unquote(String s) {
        if (s == null) return null;
        return s.replaceAll("^\"|\"$", ""); // удаляет кавычки
    }

    private Long toLongSafe(String s) {
        if (s == null || s.isEmpty() || s.equalsIgnoreCase("null")) return null;
        return Long.valueOf(s);
    }

    private Integer toIntSafe(String s) {
        if (s == null || s.isEmpty() || s.equalsIgnoreCase("null")) return 0;
        return Integer.valueOf(s);
    }

    private List<Long> parseLongList(String s) throws JsonProcessingException {
        if (s == null || s.isEmpty() || s.equalsIgnoreCase("null")) return new ArrayList<>();
        return objectMapper.readValue(s, new TypeReference<List<Long>>() {});
    }

    private Set<Long> parseLongSet(String s) throws JsonProcessingException {
        if (s == null || s.isEmpty() || s.equalsIgnoreCase("null")) return new HashSet<>();
        return objectMapper.readValue(s, new TypeReference<Set<Long>>() {});
    }

    private Map<Long, Long> parseLongMap(String s) throws JsonProcessingException {
        if (s == null || s.isEmpty() || s.equalsIgnoreCase("null")) return new HashMap<>();
        return objectMapper.readValue(s, new TypeReference<Map<Long, Long>>() {});
    }




}