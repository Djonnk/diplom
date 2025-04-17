package ru.skillbox.search_engine.config;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConfigurationProperties(prefix = "indexing-settings")
@Data
public class SitesList {
    private List<Site> sites;

    @Data
    public static class Site {
        private String url;
        private String name;
    }
}