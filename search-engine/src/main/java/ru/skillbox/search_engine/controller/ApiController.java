package ru.skillbox.search_engine.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.skillbox.search_engine.dto.SearchResult;
import ru.skillbox.search_engine.services.IndexingService;
import ru.skillbox.search_engine.services.SearchService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ApiController {
    private final IndexingService indexingService;
    private final SearchService searchService;

    @GetMapping("/startIndexing")
    public ResponseEntity<String> startIndexing() {
        indexingService.startIndexing();
        return ResponseEntity.ok("Indexing started");
    }

    @GetMapping("/stopIndexing")
    public ResponseEntity<String> stopIndexing() {
        indexingService.stopIndexing();
        return ResponseEntity.ok("Indexing stopped");
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