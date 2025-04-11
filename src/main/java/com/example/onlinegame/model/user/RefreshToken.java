package com.example.onlinegame.model.user;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Автоинкрементный ID
    private Long id;

    @Column(nullable = false)
    private String token; // Сам токен

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt; // Дата и время создания токена

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt; // Дата и время истечения токена

    @Column(name = "is_active", nullable = false)
    private Boolean isActive; // Статус активности токена
    
    @ManyToOne(fetch = FetchType.LAZY) // Связь с таблицей users
    @JoinColumn(name = "user_id", nullable = false) // Внешний ключ для связи с таблицей users
    private User user;

    // Автоматическая установка createdAt при создании токена
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.isActive = true; // По умолчанию токен активен
    }
}
