package ru.skillbox.search_engine.dto.statistics;

import lombok.Data;

@Data
public class DetailedStatisticsItem {
    private String url;
    private String name;
    private String status;
    private long statusTime;
    private String error;
    private long pages;
    private long lemmas;
}
