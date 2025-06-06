package com.example.onlinegame.model.dota2;


import jakarta.persistence.*;
import lombok.*;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class ItemAttribute {
    private String key;
    private String display;
    private String value;
}