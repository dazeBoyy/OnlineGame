package com.example.onlinegame.controller.matchmaking;

import com.example.onlinegame.security.UserPrincipal;
import com.example.onlinegame.service.matchmaking.MatchmakingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
@Slf4j
public class MatchmakingWebSocketController {
    private final MatchmakingService matchmakingService;

    @MessageMapping("/matchmaking/find")
    public void joinMatchmaking(@AuthenticationPrincipal UserPrincipal principal) {
        log.info("Пользователь с именем: {}", principal.getUsername() + " начал поиск!");

        matchmakingService.findMatch(principal.getUserId());
    }

    @MessageMapping("/matchmaking/cancel")
    public void cancelMatchmaking(@AuthenticationPrincipal UserPrincipal principal) {
        log.info("Пользователь с именем: {}", principal.getUsername() + " отменил поиск!");
        matchmakingService.cancelMatchmaking(principal.getUserId());

    }
}