package com.example.onlinegame.dataImport.service;

import com.example.onlinegame.model.dota2.Hero;
import com.example.onlinegame.repo.game.HeroRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class HeroImportService {

    private static final Logger logger = LoggerFactory.getLogger(HeroImportService.class);
    private final HeroRepository heroRepository;

    public void importHeroesFromJsonFile() throws IOException {
        logger.info("Starting hero import from JSON file...");

        ClassPathResource resource = new ClassPathResource("heroData_ID.json");
        File file = resource.getFile();

        ObjectMapper objectMapper = new ObjectMapper();
        List<Hero> heroes = objectMapper.readValue(file, new TypeReference<List<Hero>>() {});

        heroes.forEach(hero -> {
            hero.setHeroId(Math.toIntExact(hero.getId()));
            hero.setId(null);
        });

        heroRepository.saveAll(heroes);
        logger.info("Successfully imported {} heroes.", heroes.size());
    }
}