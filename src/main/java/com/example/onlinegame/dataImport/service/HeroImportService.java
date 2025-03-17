package com.example.onlinegame.dataImport.service;

import com.example.onlinegame.model.Hero;
import com.example.onlinegame.repo.HeroRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HeroImportService {

    private final HeroRepository heroRepository;

    public void importHeroesFromJsonFile() throws IOException {
        // Чтение файла из ресурсов
        ClassPathResource resource = new ClassPathResource("heroData_ID.json");
        File file = resource.getFile();

        // Парсинг JSON
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Hero> heroesMap = objectMapper.readValue(file, new TypeReference<Map<String, Hero>>() {});

        // Преобразуем Map в List<Hero>
        List<Hero> heroes = heroesMap.values().stream()
                .peek(hero -> {
                    // Устанавливаем heroId из JSON
                    hero.setHeroId(Math.toIntExact(hero.getId()));
                    // Сбрасываем id, чтобы оно генерировалось автоматически
                    hero.setId(null);
                })
                .collect(Collectors.toList());

        // Сохраняем всех героев в базу данных
        heroRepository.saveAll(heroes);
    }
}