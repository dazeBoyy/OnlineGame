package com.example.onlinegame.service.matchmaking;

import com.example.onlinegame.dto.session.ItemDTO;
import com.example.onlinegame.repo.game.ItemRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ItemCache {
    private final ItemRepository itemRepo;
    private final RedisTemplate<String, ItemDTO> itemRedisTemplate;

    @PostConstruct
    public void init() {
        itemRepo.findAll().forEach(item ->
                itemRedisTemplate.opsForValue().set(
                        "items:item:" + item.getId(),
                        new ItemDTO(
                                item.getId(),
                                item.getDname(),
                                item.getImg(),
                                item.getCost())));
    }

    public ItemDTO getItem(Long id) {
        return itemRedisTemplate.opsForValue().get("items:item:" + id);
    }
}