package com.example.onlinegame.controller;

import lombok.RequiredArgsConstructor;
import com.example.onlinegame.model.Item;
import org.springframework.web.bind.annotation.*;
import com.example.onlinegame.repo.ItemRepository;

import java.util.List;

@RestController
@RequestMapping("/api/items")
@RequiredArgsConstructor
@CrossOrigin
public class ItemController {
    private final ItemRepository itemRepository;

    @GetMapping
    public List<Item> getItems() {
        return itemRepository.findAll();
    }
}