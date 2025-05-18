package com.example.onlinegame.controller.dota2;

import lombok.RequiredArgsConstructor;
import com.example.onlinegame.model.dota2.Item;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.bind.annotation.*;
import com.example.onlinegame.repo.game.ItemRepository;

import java.util.List;

@RestController
@RequestMapping("/api/items")
@RequiredArgsConstructor
@CrossOrigin
public class ItemController {
    private final ItemRepository itemRepository;

    @Cacheable("items")
    @GetMapping
    public List<Item> getItems() {
        return itemRepository.findAll();
    }
}