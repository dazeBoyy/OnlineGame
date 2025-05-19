package com.example.onlinegame.model.dota2;

import jakarta.persistence.*;
import lombok.Data;

import java.util.List;

@Entity
@Data
@Table(name = "heroes")
public class Hero {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;  //  ID базы данных

    @Column(name = "hero_id", unique = true, nullable = false)
    private Integer heroId; // ID героя из JSON

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

    private Integer complexity;
    private Integer releaseYear;
    private String image;
    private String heroImageName;
    private String gender;

    @ElementCollection
    @CollectionTable(name = "hero_races", joinColumns = @JoinColumn(name = "hero_id"))
    @Column(name = "races")
    private List<String> race;

    private Integer legs;
}