package com.example.onlinegame.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.List;

@Entity
@Table(name = "items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Item {
    @Id
    private Long id;

    @Column(name = "item_id", unique = true, nullable = false)
    private Integer itemId; // Это ID предмета из JSON

    private String dname;
    private String img;
    private String lore;
    private int cost;
    private String behavior;
    @Column(length = 1000) // Увеличьте длину столбца
    private String notes;
    private boolean mc;
    private boolean hc;
    private int cd;
    private boolean created;
    private boolean charges;

    @ElementCollection
    private List<ItemAttribute> attrib;

    @ElementCollection
    private List<ItemAbility> abilities;
}