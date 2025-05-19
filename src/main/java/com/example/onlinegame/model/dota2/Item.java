package com.example.onlinegame.model.dota2;

import com.example.onlinegame.dataImport.StringOrArrayDeserializer;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
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
@JsonIgnoreProperties(ignoreUnknown = true)
public class Item {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "item_id", unique = true, nullable = false)
    private Long itemId; // ID предмета из JSON

    private String dname;
    private String img;
    private String lore;
    private int cost;
    @JsonDeserialize(using = StringOrArrayDeserializer.class) // Применяем кастомный десериализатор
    private String behavior;
    @Column(length = 1000)
    private String notes;
    private boolean mc;
    private boolean hc;
    private boolean cd;
    private boolean created;
    private boolean charges;

    @ElementCollection
    private List<ItemAttribute> attrib;

    @ElementCollection
    private List<ItemAbility> abilities;

    @Override
    public String toString() {
        return String.format("Item{name='%s', cost=%d}", dname, cost);
    }
}