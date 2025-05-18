package com.example.onlinegame.error;

public enum ErrorCode {
    ALREADY_IN_SESSION("player_already_in_session"),
    SESSION_NOT_FOUND("session_not_found"),
    INVALID_REQUEST("invalid_request"),
    MATCHMAKING_ERROR("matchmaking_error");


    private final String code;

    ErrorCode(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}