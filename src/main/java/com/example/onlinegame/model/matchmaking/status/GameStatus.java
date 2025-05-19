package com.example.onlinegame.model.matchmaking.status;

public enum GameStatus {
    SEARCHING,         // Ожидание игроков
    IN_PROGRESS,    // Игра в процессе
    FINISHED,       // Игра завершена
    WAITING;        // Ждем игру

    public boolean isActive() {
        return this == WAITING || this == IN_PROGRESS;
    }
}