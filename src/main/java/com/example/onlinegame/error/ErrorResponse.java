package com.example.onlinegame.error;

import lombok.Data;
import lombok.Builder;
import org.springframework.http.HttpStatusCode;
import org.springframework.lang.NonNull;
import java.time.Instant;
import java.util.Map;
import java.util.HashMap;

@Data
@Builder
public class ErrorResponse {
    private Instant timestamp;
    private int status;
    private String error;
    private String code;
    private String message;
    private String path;
    private Map<String, Object> details;

    public static ErrorResponse create(
            @NonNull Throwable ex,
            @NonNull HttpStatusCode statusCode,
            String detail
    ) {
        return ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(statusCode.value())
                .error(statusCode.toString())
                .code(ex.getClass().getSimpleName())
                .message(detail != null ? detail : ex.getMessage())
                .details(new HashMap<>())
                .build();
    }

    public static ErrorResponse create(
            @NonNull Throwable ex,
            @NonNull HttpStatusCode statusCode
    ) {
        return create(ex, statusCode, ex.getMessage());
    }
}