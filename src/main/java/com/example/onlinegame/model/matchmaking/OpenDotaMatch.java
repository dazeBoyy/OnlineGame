package com.example.onlinegame.model.matchmaking;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true) // Игнорируем неизвестные поля
public class OpenDotaMatch {
    @JsonProperty("match_id")
    private Long matchId;
    private List<OpenDotaPlayer> players;
}