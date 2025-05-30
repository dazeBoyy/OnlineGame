package com.example.onlinegame.controller.matchmaking;


import com.example.onlinegame.dto.session.GameSessionDTO;
import com.example.onlinegame.service.matchmaking.MatchmakingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/matchmaking")
@RequiredArgsConstructor
@Slf4j
public class MatchmakingController {
    private final MatchmakingService matchmakingService;

    @GetMapping("/session/{userId}")
    public ResponseEntity<GameSessionDTO> getCurrentSession(@PathVariable Long userId) {
        return matchmakingService.getCurrentSession(userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }
}