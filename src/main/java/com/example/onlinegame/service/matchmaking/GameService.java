package com.example.onlinegame.service.matchmaking;

import com.example.onlinegame.model.dota2.Hero;
import com.example.onlinegame.model.dota2.Item;
import com.example.onlinegame.model.matchmaking.GameSession;
import com.example.onlinegame.model.matchmaking.OpenDotaMatch;
import com.example.onlinegame.model.matchmaking.OpenDotaPlayer;
import com.example.onlinegame.repo.game.HeroRepository;
import com.example.onlinegame.repo.game.ItemRepository;
import com.example.onlinegame.repo.matchmaking.GameSessionRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@AllArgsConstructor
public class GameService {

    private final HeroRepository heroRepository;
    private final ItemRepository itemRepository;
    private final GameSessionRepository gameSessionRepository;
    private final OpenDotaService openDotaService;

    /**
     * Создает новую игровую сессию.
     *
     * @return Объект GameSession.
     */
    public GameSession createGameSession() {
        try {
            // Получаем случайный матч и игрока
            OpenDotaMatch match = openDotaService.getRandomMatch();
            OpenDotaPlayer player = openDotaService.getRandomPlayer(match.getMatchId());

            // Логируем данные игрока для отладки
            log.info("Player data: {}", player);

            // Проверяем, что heroId не равен null
            Integer heroId = player.getHeroId();
            if (heroId == null) {
                throw new RuntimeException("Hero ID is null for player: " + player.getAccountId());
            }

            // Получаем объекты Hero и Item из базы данных
            Hero hero = heroRepository.findById(heroId.longValue())
                    .orElseThrow(() -> new RuntimeException("Hero not found"));

            List<Item> items = player.getItems().stream()
                    .map(itemId -> this.itemRepository.findById(itemId.longValue()))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .collect(Collectors.toList());

            // Получаем нейтральный предмет
            Item neutralItem = null;
            Integer neutralItemId = player.getNeutralItem();
            if (neutralItemId != null && neutralItemId != 0) {
                neutralItem = itemRepository.findById(neutralItemId.longValue())
                        .orElse(null); // или используйте предмет по умолчанию
                if (neutralItem == null) {
                    log.warn("Neutral item with ID {} not found", neutralItemId);
                }
            }

            // Создаем сессию игры
            GameSession session = new GameSession();
            session.setRoomId(UUID.randomUUID().toString());
            session.setMatchId(match.getMatchId());
            session.setHero(hero); // Устанавливаем объект Hero
            session.setItems(items); // Устанавливаем список объектов Item
            session.setNeutralItem(neutralItem); // Устанавливаем объект Item
            session.setStartTime(LocalDateTime.now());
            session.setEndTime(LocalDateTime.now().plusSeconds(20)); // 20 секунд на раунд
            session.setVotes(new HashMap<>());

            return gameSessionRepository.save(session);
        } catch (Exception e) {
            log.error("Failed to create game session", e);
            throw new RuntimeException("Failed to create game session", e);
        }
    }

    /**
     * Обрабатывает голос пользователя.
     *
     * @param roomId  ID комнаты.
     * @param userId  ID пользователя.
     * @param heroId  ID героя, за которого проголосовал пользователь.
     */
    public void vote(String roomId, String userId, Integer heroId) {
        GameSession session = gameSessionRepository.findByRoomId(roomId)
                .orElseThrow(() -> new RuntimeException("Session not found"));

        session.getVotes().put(userId, Long.valueOf(heroId));
        gameSessionRepository.save(session);
    }

    /**
     * Получает игровую сессию по ID комнаты.
     *
     * @param roomId ID комнаты.
     * @return Объект GameSession.
     */
    public GameSession getSession(String roomId) {
        return gameSessionRepository.findByRoomId(roomId)
                .orElseThrow(() -> new RuntimeException("Session not found"));
    }
}