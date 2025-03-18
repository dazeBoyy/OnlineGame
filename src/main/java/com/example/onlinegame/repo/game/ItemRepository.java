package com.example.onlinegame.repo.game;

import com.example.onlinegame.model.dota2.Item;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemRepository  extends JpaRepository<Item, Long> {
}
