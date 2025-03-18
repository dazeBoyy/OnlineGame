package com.example.onlinegame.repo.game;

import com.example.onlinegame.model.dota2.Hero;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HeroRepository extends JpaRepository<Hero, Long> {
}
