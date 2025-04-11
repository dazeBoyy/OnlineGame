package com.example.onlinegame.dto.session;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ItemDTO {
    private Long id; // ID предмета
    private String name; // Название предмета
    private String img; // URL изображения предмета
    private Integer cost; // Стоимость предмета
}