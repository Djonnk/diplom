package ru.skillbox.search_engine.dto;

import lombok.Data;

@Data
public class SearchResult {
    private String uri;
    private String title;
    private String snippet;
    private float relevance;

    public SearchResult(String uri, String content, String snippet, float relevance) {
        this.uri = uri;
        this.title = content.contains("<title>") ? content.split("<title>")[1].split("</title>")[0] : "No title";
        this.snippet = snippet;
        this.relevance = relevance;
    }
}