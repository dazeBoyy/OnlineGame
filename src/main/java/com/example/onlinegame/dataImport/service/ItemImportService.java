package com.example.onlinegame.dataImport.service;

import com.example.onlinegame.model.dota2.Item;
import com.example.onlinegame.repo.game.ItemRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ItemImportService {

    private final ItemRepository itemRepository;

    public void importItemsFromJsonFile() throws IOException {
        // Чтение файла из ресурсов
        Resource resource = new ClassPathResource("items.json");
        File file = resource.getFile();

        // Парсинг JSON
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Item> itemsMap = objectMapper.readValue(file, new TypeReference<Map<String, Item>>() {});

        // Преобразуем Map в List<Item>
        List<Item> items = itemsMap.values().stream()
                .peek(item -> {
                    // Устанавливаем itemId из JSON
                    item.setItemId(item.getId());
                    // Сбрасываем id, чтобы оно генерировалось автоматически
                    item.setId(null);
                })
                .collect(Collectors.toList());

        // Сохраняем все предметы в базу данных
        itemRepository.saveAll(items);
    }
}