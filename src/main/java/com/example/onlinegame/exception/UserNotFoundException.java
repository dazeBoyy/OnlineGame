package com.example.onlinegame.exception;

public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(Long userId) {
        super("Пользователь с id: " + userId + " не был найден!");
    }
}
