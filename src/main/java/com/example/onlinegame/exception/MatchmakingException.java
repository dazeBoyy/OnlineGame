package com.example.onlinegame.exception;

public class MatchmakingException extends RuntimeException {
    public MatchmakingException(String message) {
        super("MatchmakingException" + message);
    }
}