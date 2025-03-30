package ru.skillbox.search_engine.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
public class Page {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "page_seq")
    @SequenceGenerator(name = "page_seq", sequenceName = "page_sequence", allocationSize = 1)
    private int id;

    @ManyToOne
    @JoinColumn(name = "site_id", nullable = false)
    private Site site;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String path;

    @Column(nullable = false)
    private int code;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;
}