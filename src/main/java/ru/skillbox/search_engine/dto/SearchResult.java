package ru.skillbox.search_engine.dto;

import lombok.Data;

@Data
public class SearchResult {
    private String uri;
    private String title;
    private String snippet;
    private float relevance;

    public SearchResult(String uri, String title, String snippet, float relevance) {
        this.uri = uri;
        this.title = title;
        this.snippet = snippet;
        this.relevance = relevance;
    }
}