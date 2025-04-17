package ru.skillbox.search_engine.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.skillbox.search_engine.dto.SearchResult;
import ru.skillbox.search_engine.dto.statistics.StatisticsResponse;
import ru.skillbox.search_engine.services.IndexingService;
import ru.skillbox.search_engine.services.SearchService;
import ru.skillbox.search_engine.services.StatisticsService;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ForkJoinPool;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ApiController {
    private final StatisticsService statisticsService;
    private final IndexingService indexingService;
    private final SearchService searchService;
    private volatile boolean isIndexing = false;

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    @GetMapping("/startIndexing")
    public ResponseEntity<Map<String, Object>> startIndexing() {
        if (isIndexing) {
            return ResponseEntity.badRequest().body(Map.of("result", false, "error", "Индексация уже запущена"));
        }
        isIndexing = true;
        ForkJoinPool.commonPool().submit(() -> {
            try {
                indexingService.startIndexing();
            } finally {
                isIndexing = false;
            }
        });
        return ResponseEntity.ok(Map.of("result", true));
    }

    @GetMapping("/stopIndexing")
    public ResponseEntity<Map<String, Object>> stopIndexing() {
        if (!isIndexing) {
            return ResponseEntity.badRequest().body(Map.of("result", false, "error", "Индексация не запущена"));
        }
        indexingService.stopIndexing();
        isIndexing = false;
        return ResponseEntity.ok(Map.of("result", true));
    }

    @PostMapping("/indexPage")
    public ResponseEntity<Map<String, Object>> indexPage(@RequestParam String url) {
        try {
            indexingService.indexSinglePage(url);
            return ResponseEntity.ok(Map.of("result", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("result", false, "error", e.getMessage()));
        }
    }

    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> search(
            @RequestParam String query,
            @RequestParam(required = false) String site,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit) {
        try {
            List<SearchResult> results = searchService.search(query, site, offset, limit);
            return ResponseEntity.ok(Map.of(
                    "result", true,
                    "count", results.size(),
                    "data", results
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "result", false,
                    "error", e.getMessage()
            ));
        }
    }
}