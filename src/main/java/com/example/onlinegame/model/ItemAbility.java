package com.example.onlinegame.model;

import jakarta.persistence.*;
import lombok.*;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class ItemAbility {
    private String type;
    private String title;
    @Column(length = 1000) // Увеличьте длину столбца
    private String description;
}