package com.example.onlinegame.exception;

public class RedisOperationException extends RuntimeException {
    public RedisOperationException(String sessionDeletionFailed, Exception e) {
        super(sessionDeletionFailed, e);
    }
}
