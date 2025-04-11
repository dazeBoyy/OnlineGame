package com.example.onlinegame.dto.session;

import com.example.onlinegame.model.matchmaking.GameStatus;
import lombok.Builder;
import lombok.Data;

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
    private ItemDTO neutralItem; // Нейтральный предмет
}