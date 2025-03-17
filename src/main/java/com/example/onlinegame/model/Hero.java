package com.example.onlinegame.model;

import jakarta.persistence.*;
import lombok.Data;

import java.util.List;

@Entity
@Data
@Table(name = "heroes")
public class Hero {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;  // Это внутренний ID базы данных

    @Column(name = "hero_id", unique = true, nullable = false)
    private Integer heroId; // Это ID героя из JSON

    private String name;
    private String primaryAttribute;
    private String attackType;

    @ElementCollection
    @CollectionTable(name = "hero_roles", joinColumns = @JoinColumn(name = "hero_id"))
    @Column(name = "roles")
    private List<String> roles;

    @ElementCollection
    @CollectionTable(name = "hero_lanes", joinColumns = @JoinColumn(name = "hero_id"))
    @Column(name = "lanes")
    private List<String> lanes;

}
