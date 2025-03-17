package com.example.onlinegame.controller;


import lombok.RequiredArgsConstructor;
import com.example.onlinegame.model.Hero;
import org.springframework.web.bind.annotation.*;
import com.example.onlinegame.repo.HeroRepository;

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
}