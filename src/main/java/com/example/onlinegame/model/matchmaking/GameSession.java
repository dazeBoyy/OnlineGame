package com.example.onlinegame.model.matchmaking;

import com.example.onlinegame.model.matchmaking.status.GameStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.NaturalId;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Entity
@Table(name = "game_sessions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NaturalId
    @Column(unique = true, nullable = false)
    @Builder.Default
    private String roomId = UUID.randomUUID().toString();

    // Основные игровые данные
    private Long matchId;
    private Long targetHeroId; // ID героя для угадывания
    private Integer totalRounds;
    private Long winnerId;

    // Состояние игры
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private GameStatus status = GameStatus.WAITING;

    @Builder.Default
    private int timeLeftInRound = 30;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    // Игроки
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "session_players", joinColumns = @JoinColumn(name = "session_id"))
    @Column(name = "user_id")
    @Builder.Default
    private Set<Long> playerIds = new HashSet<>();

    // Предметы игрока
    @Convert(converter = ItemListConverter.class)
    @Builder.Default
    private List<Long> itemIds = new ArrayList<>();

    @Convert(converter = ItemListConverter.class)
    @Builder.Default
    private List<Long> backpackIds = new ArrayList<>();

    private Long neutralItemId;

    @PrePersist
    protected void onCreate() {
        if (startedAt == null) {
            startedAt = LocalDateTime.now();
        }
    }

    public void finishGame() {
        this.finishedAt = LocalDateTime.now();
        this.status = GameStatus.FINISHED;
    }

    @Converter
    public static class ItemListConverter implements AttributeConverter<List<Long>, String> {
        @Override
        public String convertToDatabaseColumn(List<Long> attribute) {
            if (attribute == null || attribute.isEmpty()) {
                return "";
            }
            return attribute.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(","));
        }

        @Override
        public List<Long> convertToEntityAttribute(String dbData) {
            if (dbData == null || dbData.isEmpty()) {
                return new ArrayList<>();
            }
            return Arrays.stream(dbData.split(","))
                    .map(Long::valueOf)
                    .collect(Collectors.toList());
        }
    }
}