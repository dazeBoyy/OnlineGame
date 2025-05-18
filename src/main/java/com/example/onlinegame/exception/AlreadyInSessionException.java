package com.example.onlinegame.exception;

public class AlreadyInSessionException extends MatchmakingException {
    private final String roomId;

    public AlreadyInSessionException(String roomId) {
        super("Player is already in active session: " + roomId);
        this.roomId = roomId;
    }

    public String getRoomId() {
        return roomId;
    }
}