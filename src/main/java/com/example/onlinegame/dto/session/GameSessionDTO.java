package com.example.onlinegame.dto.session;

import com.example.onlinegame.model.matchmaking.status.GameStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class GameSessionDTO {
    private String roomId; // ID комнаты
    private String matchId; // ID матча
    private GameStatus status; // Статус игры
    private List<PlayerDTO> players; // Игроки в сессии
    private List<ItemDTO> items; // Инвентарь
    private List<ItemDTO> backpacks; // Рюкзак
    private Integer currentRound; // Игровой раунд
    private ItemDTO neutralItem; // Нейтральный предмет
    private Integer timeLeft; // Время раунда
    private LocalDateTime finishTime;
}