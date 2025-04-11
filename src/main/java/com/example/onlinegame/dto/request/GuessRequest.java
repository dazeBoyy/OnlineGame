package com.example.onlinegame.dto.request;

import lombok.Data;

@Data
public class GuessRequest {
    private String roomId;
    private Long userId;
    private Long heroId;
}