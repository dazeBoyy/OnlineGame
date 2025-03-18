package com.example.onlinegame.service.matchmaking;

import com.example.onlinegame.model.matchmaking.OpenDotaMatch;
import com.example.onlinegame.model.matchmaking.OpenDotaPlayer;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Random;

@Service
@Slf4j
public class OpenDotaService {

    private static final String OPENDOTA_API = "https://api.opendota.com/api";
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Random random = new Random();

    /**
     * Получает случайный матч из OpenDota API.
     *
     * @return Объект OpenDotaMatch с данными о матче.
     */
    public OpenDotaMatch getRandomMatch() {
        try {
            // Запрос к API для получения списка публичных матчей
            String url = OPENDOTA_API + "/publicMatches";
            String response = restTemplate.getForObject(url, String.class);

            // Десериализация JSON в список матчей
            List<OpenDotaMatch> matches = objectMapper.readValue(response, new TypeReference<>() {});

            if (matches == null || matches.isEmpty()) {
                throw new RuntimeException("No matches found");
            }

            // Выбираем случайный матч
            OpenDotaMatch randomMatch = matches.get(random.nextInt(matches.size()));
            log.info("Selected match: {}", randomMatch.getMatchId());

            return randomMatch;
        } catch (Exception e) {
            log.error("Failed to fetch random match", e);
            throw new RuntimeException("Failed to fetch random match", e);
        }
    }

    /**
     * Получает случайного игрока из матча.
     *
     * @param matchId ID матча.
     * @return Объект OpenDotaPlayer с данными об игроке.
     */
    public OpenDotaPlayer getRandomPlayer(Long matchId) {
        try {
            // Запрос к API для получения деталей матча
            String url = OPENDOTA_API + "/matches/" + matchId;
            String response = restTemplate.getForObject(url, String.class);

            // Десериализация JSON в объект OpenDotaMatch
            OpenDotaMatch match = objectMapper.readValue(response, OpenDotaMatch.class);

            if (match == null || match.getPlayers() == null || match.getPlayers().isEmpty()) {
                throw new RuntimeException("No players found in match");
            }

            // Выбираем случайного игрока
            OpenDotaPlayer randomPlayer = match.getPlayers().get(random.nextInt(match.getPlayers().size()));

            // Получаем предметы игрока (с проверкой на null)
            randomPlayer.setItems(Arrays.asList(
                    Objects.requireNonNullElse(randomPlayer.getItem0(), 0),
                    Objects.requireNonNullElse(randomPlayer.getItem1(), 0),
                    Objects.requireNonNullElse(randomPlayer.getItem2(), 0),
                    Objects.requireNonNullElse(randomPlayer.getItem3(), 0),
                    Objects.requireNonNullElse(randomPlayer.getItem4(), 0),
                    Objects.requireNonNullElse(randomPlayer.getItem5(), 0)
            ));

            // Получаем предметы из рюкзака (с проверкой на null)
            randomPlayer.setBackpack(Arrays.asList(
                    Objects.requireNonNullElse(randomPlayer.getBackpack0(), 0),
                    Objects.requireNonNullElse(randomPlayer.getBackpack1(), 0),
                    Objects.requireNonNullElse(randomPlayer.getBackpack2(), 0)
            ));

            // Получаем нейтральный предмет (с проверкой на null)
            randomPlayer.setNeutralItem(Objects.requireNonNullElse(randomPlayer.getItemNeutral(), 0));

            log.info("Selected player: {}", randomPlayer.getAccountId());
            return randomPlayer;
        } catch (Exception e) {
            log.error("Failed to fetch player from match", e);
            throw new RuntimeException("Failed to fetch player from match", e);
        }
    }
}