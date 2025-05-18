package com.example.onlinegame.exception;

public class GameEndException extends RuntimeException {
    public GameEndException(String message, Throwable cause) {
        super(message, cause);
    }
}