package com.example.onlinegame.repo;

import com.example.onlinegame.model.Item;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemRepository  extends JpaRepository<Item, Long> {
}
