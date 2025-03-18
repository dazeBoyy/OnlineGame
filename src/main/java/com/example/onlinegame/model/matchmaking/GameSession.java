package com.example.onlinegame.model.matchmaking;

import com.example.onlinegame.model.dota2.Hero;
import com.example.onlinegame.model.dota2.Item;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "game_sessions")
@Data
public class GameSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String roomId;
    private Long matchId;

    @ManyToOne
    @JoinColumn(name = "hero_id")
    private Hero hero;

    @ManyToMany
    @JoinTable(
            name = "game_session_items",
            joinColumns = @JoinColumn(name = "game_session_id"),
            inverseJoinColumns = @JoinColumn(name = "item_id")
    )
    private List<Item> items;

    @ManyToOne
    @JoinColumn(name = "neutral_item_id")
    private Item neutralItem;

    @ElementCollection
    @CollectionTable(name = "game_session_votes", joinColumns = @JoinColumn(name = "game_session_id"))
    @MapKeyColumn(name = "user_id")
    @Column(name = "hero_id")
    private Map<String, Long> votes; // userId -> heroId

    private LocalDateTime startTime;
    private LocalDateTime endTime;

    // Геттеры и сеттеры
}