package com.example.onlinegame.controller.matchmaking;

import com.example.onlinegame.model.matchmaking.GameSession;

import com.example.onlinegame.service.matchmaking.GameService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/game")
@AllArgsConstructor
public class GameController {


    private GameService gameService;

    @PostMapping("/create")
    public String createGame() {
        GameSession session = gameService.createGameSession();
        return session.getRoomId();
    }

    @PostMapping("/{roomId}/vote")
    public void vote(@PathVariable String roomId, @RequestParam String userId, @RequestParam Integer heroId) {
        gameService.vote(roomId, userId, heroId);
    }

    @GetMapping("/{roomId}")
    public GameSession getSession(@PathVariable String roomId) {
        return gameService.getSession(roomId);
    }
}