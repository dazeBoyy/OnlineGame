package com.example.onlinegame.dto.session;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PlayerDTO {
    private Long id; // ID игрока
    private String username; // Имя пользователя
}