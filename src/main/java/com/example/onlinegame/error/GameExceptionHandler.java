package com.example.onlinegame.error;


import com.example.onlinegame.exception.AlreadyInSessionException;
import com.example.onlinegame.exception.MatchmakingException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

import java.time.Instant;
import java.util.Map;

@ControllerAdvice
public class GameExceptionHandler {

    @ExceptionHandler(AlreadyInSessionException.class)
    public ResponseEntity<ErrorResponse> handleAlreadyInSession(
            AlreadyInSessionException ex
    ) {
        ErrorResponse error = ErrorResponse.create(
                ex,
                HttpStatus.CONFLICT,
                "Player is already in an active game session"
        );


        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }


    @ExceptionHandler(MatchmakingException.class)
    public ResponseEntity<ErrorResponse> handleMatchmakingError(
            MatchmakingException ex,
            WebRequest request) {

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.CONFLICT.value())
                .error(HttpStatus.CONFLICT.getReasonPhrase())
                .code(ErrorCode.MATCHMAKING_ERROR.name())
                .message("Ошибка при подборе матча")
                .path(request.getDescription(false).replace("uri=", ""))
                .details(Map.of(
                        "errorDetails", ex.getMessage(),
                        "errorType", ex.getClass().getSimpleName(),
                        "suggestedAction", "Попробуйте позже или создайте новую сессию"
                ))
                .build();

        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
    }
}
