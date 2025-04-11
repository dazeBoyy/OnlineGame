package com.example.onlinegame.model.matchmaking;

import com.example.onlinegame.model.dota2.Hero;
import com.example.onlinegame.model.dota2.Item;
import com.example.onlinegame.model.user.User;
import jakarta.persistence.*;
import lombok.Data;

import java.util.*;

@Entity
@Table(name = "game_sessions")
@Data
public class GameSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String roomId;
    private Long matchId;

    private Long heroId;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "game_session_items",
            joinColumns = @JoinColumn(name = "game_session_id"),
            inverseJoinColumns = @JoinColumn(name = "item_id")
    )
    private List<Item> items;

    // Предметы в рюкзаке
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "game_session_backpack", // Используем отдельную таблицу
            joinColumns = @JoinColumn(name = "game_session_id"),
            inverseJoinColumns = @JoinColumn(name = "backpack_item_id")
    )
    private List<Item> backpack;


    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "neutral_item_id")
    private Item neutralItem;

    @ElementCollection()
    private Map<Long, Boolean> players = new HashMap<>(); // userId -> isCorrect

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "session_players",
            joinColumns = @JoinColumn(name = "session_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    private Set<User> sessionPlayers = new HashSet<>();

    @Enumerated(EnumType.STRING)
    private GameStatus status = GameStatus.WAITING; // Initial status

    private Long winnerId;

    public boolean isFull() {
        return sessionPlayers.size() >= 2;
    }

    public boolean isEmpty() {
        return sessionPlayers.isEmpty();
    }
    public boolean hasPlayer(Long userId) {
        return sessionPlayers.stream()
                .anyMatch(player -> player.getId().equals(userId));
    }

    public void addPlayer(User player) {
        if (!hasPlayer(player.getId())) {
            sessionPlayers.add(player);
        }
    }

    public void removePlayer(Long userId) {
        sessionPlayers.removeIf(player -> player.getId().equals(userId));
    }
}