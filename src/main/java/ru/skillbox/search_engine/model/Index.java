package ru.skillbox.search_engine.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "index_table")
@Data
public class Index {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne
    @JoinColumn(name = "page_id", nullable = false)
    private Page page;

    @ManyToOne
    @JoinColumn(name = "lemma_id", nullable = false)
    private Lemma lemma;

    @Column(nullable = false)
    private float rank;
}