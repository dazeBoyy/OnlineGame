package com.example.onlinegame.exception;

public class PlayerAlreadyVotedException extends RuntimeException {
    public PlayerAlreadyVotedException(Long userId, String roomId) {
        super("User " + userId + " has already voted in room " + roomId);
    }
}
