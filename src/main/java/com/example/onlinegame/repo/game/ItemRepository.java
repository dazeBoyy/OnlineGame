package com.example.onlinegame.repo.game;

import com.example.onlinegame.model.dota2.Item;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ItemRepository  extends JpaRepository<Item, Long> {


    Optional<Item> findByItemId(Long itemId);
}
