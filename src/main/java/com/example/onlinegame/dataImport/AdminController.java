package com.example.onlinegame.dataImport;

import com.example.onlinegame.dataImport.service.HeroImportService;
import com.example.onlinegame.dataImport.service.ItemImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final ItemImportService itemImportService;
    private final HeroImportService heroImportService;

    @PostMapping("/import-items")
    public ResponseEntity<String> importItems() {
        try {
            itemImportService.importItemsFromJsonFile();
            return ResponseEntity.ok("Items imported successfully");
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to import items: " + e.getMessage());
        }
    }

    @PostMapping("/import-heroes")
    public ResponseEntity<String> importHeroes() {
        try {
            heroImportService.importHeroesFromJsonFile();
            return ResponseEntity.ok("Heroes imported successfully");
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to import heroes: " + e.getMessage());
        }
    }
}