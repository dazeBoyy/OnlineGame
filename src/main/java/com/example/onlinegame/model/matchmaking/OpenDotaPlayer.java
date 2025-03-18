package com.example.onlinegame.model.matchmaking;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true) // Игнорируем неизвестные поля
public class OpenDotaPlayer {
    @JsonProperty("account_id")
    private Long accountId;

    @JsonProperty("hero_id")
    private Integer heroId;

    // Основные предметы
    @JsonProperty("item_0")
    private Integer item0;

    @JsonProperty("item_1")
    private Integer item1;

    @JsonProperty("item_2")
    private Integer item2;

    @JsonProperty("item_3")
    private Integer item3;

    @JsonProperty("item_4")
    private Integer item4;

    @JsonProperty("item_5")
    private Integer item5;

    // Предметы в рюкзаке
    @JsonProperty("backpack_0")
    private Integer backpack0;

    @JsonProperty("backpack_1")
    private Integer backpack1;

    @JsonProperty("backpack_2")
    private Integer backpack2;

    // Нейтральный предмет
    @JsonProperty("item_neutral")
    private Integer itemNeutral;

    // Списки для удобства
    private List<Integer> items;
    private List<Integer> backpack;
    private Integer neutralItem;
}