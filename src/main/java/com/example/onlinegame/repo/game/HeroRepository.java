package com.example.onlinegame.repo.game;

import com.example.onlinegame.model.dota2.Hero;
import com.example.onlinegame.model.dota2.Item;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface HeroRepository extends JpaRepository<Hero, Long> {
    Optional<Hero> findByHeroId(Long heroId);
}
