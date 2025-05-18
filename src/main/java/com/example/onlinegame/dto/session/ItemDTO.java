package com.example.onlinegame.dto.session;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ItemDTO {
    private Long id; // ID предмета
    private String name; // Название предмета
    private String img; // URL изображения предмета
    private Integer cocst; // Стоимость предмета
}