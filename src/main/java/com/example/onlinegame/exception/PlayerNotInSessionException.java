package com.example.onlinegame.exception;

public class PlayerNotInSessionException extends RuntimeException {
    public PlayerNotInSessionException(Long userId, String roomId) {
        super("Пользователь с Id:" + userId + " не был найден в комнате: " + roomId);
    }
}

