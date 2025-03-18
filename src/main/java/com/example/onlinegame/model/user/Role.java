package com.example.onlinegame.model.user;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
public class Role {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String name;
}