package ru.skillbox.search_engine.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
public class Site {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "status_time", nullable = false)
    private java.time.LocalDateTime statusTime;

    @Column(name = "last_error")
    private String lastError;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String url;

    @Column(nullable = false)
    private String name;
}