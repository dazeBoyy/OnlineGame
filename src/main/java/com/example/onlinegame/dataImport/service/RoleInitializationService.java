package com.example.onlinegame.dataImport.service;

import com.example.onlinegame.model.user.Role;
import com.example.onlinegame.repo.user.RoleRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.List;

@Service
public class RoleInitializationService {

    @Autowired
    private RoleRepository roleRepository;

    @PostConstruct
    public void initRoles() {
        try {
            // Загрузка файла roles.json из ресурсов
            ClassPathResource resource = new ClassPathResource("roles.json");
            File file = resource.getFile();

            // Парсинг JSON
            ObjectMapper objectMapper = new ObjectMapper();
            List<Role> roles = objectMapper.readValue(file, new TypeReference<List<Role>>() {});

            // Сохранение ролей в базе данных, если они еще не существуют
            for (Role role : roles) {
                if (roleRepository.findByName(role.getName()).isEmpty()) {
                    roleRepository.save(role);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}