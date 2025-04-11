package com.example.onlinegame.service.matchmaking;

import com.example.onlinegame.dto.session.GameSessionDTO;
import com.example.onlinegame.dto.session.PlayerDTO;
import com.example.onlinegame.model.dota2.Hero;
import com.example.onlinegame.model.dota2.Item;
import com.example.onlinegame.model.matchmaking.GameSession;
import com.example.onlinegame.model.matchmaking.GameStatus;
import com.example.onlinegame.model.matchmaking.OpenDotaMatch;
import com.example.onlinegame.model.matchmaking.OpenDotaPlayer;
import com.example.onlinegame.model.user.User;
import com.example.onlinegame.repo.game.HeroRepository;
import com.example.onlinegame.repo.game.ItemRepository;
import com.example.onlinegame.repo.matchmaking.GameSessionRedisRepository;
import com.example.onlinegame.repo.matchmaking.GameSessionRepository;
import com.example.onlinegame.repo.user.UserRepository;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class GameService {
    private final HeroRepository heroRepository;
    private final ItemRepository itemRepository;
    private final GameSessionRepository gameSessionRepository;
    private final OpenDotaService openDotaService;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final GameSessionRedisRepository redisRepository;

    @Transactional
    public GameSession createGameSession() {
        try {
            OpenDotaMatch match = openDotaService.getRandomMatch();
            OpenDotaPlayer player = openDotaService.getRandomPlayer(match.getMatchId());

            Integer heroId = player.getHeroId();
            if (heroId == null) {
                throw new RuntimeException("Hero ID is null for player: " + player.getAccountId());
            }

            // Проверяем существование героя, но сохраняем только ID
            heroRepository.findById(heroId.longValue())
                    .orElseThrow(() -> new RuntimeException("Hero not found: " + heroId));

            List<Item> items = player.getItems().stream()
                    .filter(itemId -> itemId != 0)
                    .map(itemId -> itemRepository.findByItemId(itemId.longValue()))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .collect(Collectors.toList());

            List<Item> backpacks = player.getBackpacks().stream()
                    .filter(backpackId -> backpackId != 0)
                    .map(backpackId -> itemRepository.findByItemId(backpackId.longValue()))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .collect(Collectors.toList());

            Item neutralItem = Optional.ofNullable(player.getNeutralItem())
                    .filter(id -> id != 0)
                    .map(id -> itemRepository.findByItemId(id.longValue()))
                    .flatMap(item -> item)
                    .orElse(null);


            log.info("Session info. Items - {}. Backpacks - {}. NeutralItems -  {}. UserInfo - {}",
                    items
                            .stream()
                            .map(Item::toString)
                            .collect(Collectors.joining(", ")),
                    backpacks
                            .stream()
                            .map(Item::toString)
                            .collect(Collectors.joining(", ")),
                    Optional.ofNullable(neutralItem)
                            .map(Item::getId)
                            .map(String::valueOf)
                            .orElse("No neutral item"), // Добавляем безопасную обработку null
                    player.getPersonaname());

            GameSession session = new GameSession();
            session.setRoomId(UUID.randomUUID().toString());
            session.setMatchId(match.getMatchId());
            session.setHeroId(heroId.longValue());
            session.setBackpack(backpacks);// Сохраняем просто ID
            session.setItems(items);
            session.setNeutralItem(neutralItem);

            return gameSessionRepository.save(session);
        } catch (Exception e) {
            log.error("Failed to create game session", e);
            throw new RuntimeException("Failed to create game session", e);
        }
    }

    @Transactional
    public GameSession makeGuess(String roomId, Long userId, Long heroId) {
        GameSession session = gameSessionRepository.findByRoomId(roomId)
                .orElseThrow(() -> new RuntimeException("Session not found"));

        Hero heroName = heroRepository.findById(heroId)
                .orElseThrow(() -> new RuntimeException("Hero not found"));

        if (session.getStatus() != GameStatus.IN_PROGRESS) {
            throw new RuntimeException("Game is not in progress");
        }

        if (!session.hasPlayer(userId)) {
            throw new RuntimeException("User is not in this session");
        }

        // Сравниваем просто heroId
        boolean isCorrect = heroId.equals(session.getHeroId());
        session.getPlayers().put(userId, isCorrect);

        if (isCorrect) {
            // Если угадан правильно
            session.setStatus(GameStatus.COMPLETED);
            session.setWinnerId(userId);

            User winner = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            winner.setWins(winner.getWins() + 1);

            session.getSessionPlayers().stream()
                    .filter(player -> !player.getId().equals(userId))
                    .forEach(player -> {
                        User user = userRepository.findById(player.getId())
                                .orElseThrow(() -> new RuntimeException("User not found"));
                        user.setLosses(user.getLosses() + 1);
                    });

            session = gameSessionRepository.save(session);
            redisRepository.removeSession(roomId);
            session.getSessionPlayers().forEach(player ->
                    redisRepository.removePlayerFromQueue(player.getId()));
        } else {
            // Если угадан неправильно

            log.info("User [{}] made an incorrect guess for heroId [{}] in room [{}]",
                    userId, heroName , roomId);

            // Сохраняем состояние сессии
            session = gameSessionRepository.save(session);
        }

        return session;
    }

    public GameSession getSession(String roomId) {
        return gameSessionRepository.findByRoomId(roomId)
                .orElseThrow(() -> new RuntimeException("Session not found"));
    }


    public GameSessionDTO toDTO(GameSession session) {
        return GameSessionDTO.builder()
                .roomId(session.getRoomId())
                .status(session.getStatus())
                .players(session.getSessionPlayers().stream()
                        .map(player -> PlayerDTO.builder()
                                .id(player.getId())
                                .username(player.getUsername())
                                .build())
                        .collect(Collectors.toList()))
                .build();
    }
}