package com.example.onlinegame.repo.matchmaking;

import com.example.onlinegame.dto.session.GameSessionDTO;
import com.example.onlinegame.dto.session.ItemDTO;
import com.example.onlinegame.dto.session.PlayerDTO;
import com.example.onlinegame.model.matchmaking.RedisGameSession;
import com.example.onlinegame.model.matchmaking.GameStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Repository
@Slf4j
@RequiredArgsConstructor
public class GameSessionRedisRepository {
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String SESSION_KEY = "game:session:";
    private static final String WAITING_PLAYERS_KEY = "game:waiting:players";
    private static final String ACTIVE_SESSIONS_KEY = "game:active:sessions";
    private static final String PLAYER_SESSION_KEY = "game:player:";
    private static final long SESSION_TTL = 2; // hours

    public void saveSession(RedisGameSession session) {
        String key = SESSION_KEY + session.getSessionId();
        redisTemplate.opsForHash().putAll(key, convertToMap(session));

        // Сохраняем связи игрок -> сессия
        session.getPlayerIds().forEach(playerId -> {
            redisTemplate.opsForValue().set(
                    PLAYER_SESSION_KEY + playerId,
                    session.getSessionId(),
                    SESSION_TTL,
                    TimeUnit.HOURS
            );
        });

        // Управление списками активных и ожидающих сессий
        if (session.getStatus() == GameStatus.WAITING) {
            redisTemplate.opsForSet().add(WAITING_PLAYERS_KEY, session.getSessionId());
            redisTemplate.opsForSet().remove(ACTIVE_SESSIONS_KEY, session.getSessionId());
        } else if (session.getStatus() == GameStatus.IN_PROGRESS) {
            redisTemplate.opsForSet().remove(WAITING_PLAYERS_KEY, session.getSessionId());
            redisTemplate.opsForSet().add(ACTIVE_SESSIONS_KEY, session.getSessionId());
        } else {
            // Для завершенных сессий удаляем из обоих списков
            redisTemplate.opsForSet().remove(WAITING_PLAYERS_KEY, session.getSessionId());
            redisTemplate.opsForSet().remove(ACTIVE_SESSIONS_KEY, session.getSessionId());
        }

        redisTemplate.expire(key, SESSION_TTL, TimeUnit.HOURS);
    }

    public Optional<RedisGameSession> findPlayerSession(Long playerId) {
        String sessionId = (String) redisTemplate.opsForValue().get(PLAYER_SESSION_KEY + playerId);
        if (sessionId == null) {
            return Optional.empty();
        }
        return findSession(sessionId);
    }


    public Optional<RedisGameSession> findSession(String sessionId) {
        String key = SESSION_KEY + sessionId;
        Map<Object, Object> data = redisTemplate.opsForHash().entries(key);
        if (data.isEmpty()) {
            return Optional.empty();
        }
        try {
            RedisGameSession session = convertFromMap(data);
            return Optional.of(session);
        } catch (Exception e) {
            log.error("Error converting session data for sessionId: {}", sessionId, e);
            return Optional.empty();
        }
    }

    public void removeSession(String sessionId) {
        RedisGameSession session = findSession(sessionId).orElse(null);
        if (session != null) {
            // Удаляем связи игрок -> сессия
            session.getPlayerIds().forEach(playerId ->
                    redisTemplate.delete(PLAYER_SESSION_KEY + playerId));
        }

        String key = SESSION_KEY + sessionId;
        redisTemplate.delete(key);
        redisTemplate.opsForSet().remove(WAITING_PLAYERS_KEY, sessionId);
        redisTemplate.opsForSet().remove(ACTIVE_SESSIONS_KEY, sessionId);
    }

    /**
     * Удаляет игрока из очереди ожидания
     *
     * @param playerId ID игрока, которого нужно удалить из очереди
     */
    public void removePlayerFromQueue(Long playerId) {
        // Удаляем связь игрока с сессией
        String playerKey = PLAYER_SESSION_KEY + playerId;
        String sessionId = (String) redisTemplate.opsForValue().get(playerKey);

        if (sessionId != null) {
            // Удаляем сессию из списка ожидающих
            redisTemplate.opsForSet().remove(WAITING_PLAYERS_KEY, sessionId);
            // Удаляем связь игрока с сессией
            redisTemplate.delete(playerKey);

            log.info("Player {} removed from queue, session {} cleared", playerId, sessionId);
        } else {
            log.debug("No active session found for player {}", playerId);
        }
    }


    public Set<String> findWaitingSessions() {
        return redisTemplate.opsForSet().members(WAITING_PLAYERS_KEY)
                .stream()
                .map(Object::toString)
                .collect(Collectors.toSet());
    }


    private Map<String, String> convertToMap(RedisGameSession session) {
        Map<String, String> map = new HashMap<>();
        map.put("sessionId", session.getSessionId());
        map.put("dbId", String.valueOf(session.getDbId()));
        map.put("roomId", session.getRoomId());
        map.put("matchId", String.valueOf(session.getMatchId()));
        map.put("heroId", String.valueOf(session.getHeroId()));
        map.put("itemIds", String.join(",",
                session.getItemIds().stream().map(String::valueOf).collect(Collectors.toList())));
        map.put("backpackIds", String.join(",",
                session.getBackpackIds().stream().map(String::valueOf).collect(Collectors.toList())));
        map.put("neutralItemId",
                session.getNeutralItemId() != null ? String.valueOf(session.getNeutralItemId()) : "");
        // Изменяем сохранение playerIds - преобразуем Long в String
        map.put("playerIds", String.join(",",
                session.getPlayerIds().stream()
                        .map(String::valueOf)
                        .collect(Collectors.toList())));
        map.put("status", session.getStatus().name());
        map.put("winnerId", session.getWinnerId() != null ?
                String.valueOf(session.getWinnerId()) : "");
        map.put("createdAt", String.valueOf(session.getCreatedAt()));
        return map;
    }

    private RedisGameSession convertFromMap(Map<Object, Object> map) {
        String playerIdsStr = map.get("playerIds").toString();
        Set<Long> playerIds = playerIdsStr.isEmpty() ?
                new HashSet<>() :
                Arrays.stream(playerIdsStr.split(","))
                        .map(Long::valueOf)
                        .collect(Collectors.toSet());

        return RedisGameSession.builder()
                .sessionId(map.get("sessionId").toString())
                .dbId(Long.valueOf(map.get("dbId").toString()))
                .roomId(map.get("roomId").toString())
                .matchId(Long.valueOf(map.get("matchId").toString()))
                .itemIds(Arrays.stream(map.get("itemIds").toString().split(","))
                        .filter(s -> !s.isEmpty())
                        .map(Long::valueOf)
                        .collect(Collectors.toList()))
                .backpackIds(Arrays.stream(map.get("backpackIds").toString().split(","))
                        .filter(s -> !s.isEmpty())
                        .map(Long::valueOf)
                        .collect(Collectors.toList()))
                .neutralItemId(map.get("neutralItemId").toString().isEmpty() ? null :
                        Long.valueOf(map.get("neutralItemId").toString()))
                .playerIds(playerIds)  // Используем преобразованный Set<Long>
                .status(GameStatus.valueOf(map.get("status").toString()))
                .winnerId(map.get("winnerId").toString().isEmpty() ? null :
                        Long.valueOf(map.get("winnerId").toString()))
                .createdAt(Long.valueOf(map.get("createdAt").toString()))
                .build();
    }




}