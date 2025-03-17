package com.example.onlinegame.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(name = "wins", columnDefinition = "INT DEFAULT 0")
    private int wins;

    @Column(name = "losses", columnDefinition = "INT DEFAULT 0")
    private int losses;
}