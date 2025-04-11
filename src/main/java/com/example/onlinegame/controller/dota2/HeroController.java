package com.example.onlinegame.controller.dota2;


import lombok.RequiredArgsConstructor;
import com.example.onlinegame.model.dota2.Hero;
import org.springframework.web.bind.annotation.*;
import com.example.onlinegame.repo.game.HeroRepository;

import java.util.List;

@RestController
@RequestMapping("/api/heroes")
@RequiredArgsConstructor
@CrossOrigin
public class HeroController {
    private final HeroRepository heroRepository;

    @GetMapping
    public List<Hero> getHeroes() {
        return heroRepository.findAll();
    }

    @GetMapping("/{heroId}")
    public Hero getHero(@PathVariable Long heroId) {
        return heroRepository.findById(heroId).orElseThrow(() -> new RuntimeException("Hero not found"));
    }
}