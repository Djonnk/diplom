package ru.skillbox.search_engine.services;


import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.skillbox.search_engine.dto.statistics.DetailedStatisticsItem;
import ru.skillbox.search_engine.dto.statistics.StatisticsData;
import ru.skillbox.search_engine.dto.statistics.StatisticsResponse;
import ru.skillbox.search_engine.dto.statistics.TotalStatistics;
import ru.skillbox.search_engine.model.Site;
import ru.skillbox.search_engine.repositories.LemmaRepository;
import ru.skillbox.search_engine.repositories.PageRepository;
import ru.skillbox.search_engine.repositories.SiteRepository;

import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;

    public StatisticsResponse getStatistics() {
        List<Site> sites = siteRepository.findAll();
        int totalSites = sites.size();
        long totalPages = pageRepository.count();
        long totalLemmas = lemmaRepository.count();

        TotalStatistics total = new TotalStatistics();
        total.setSites(totalSites);
        total.setPages(totalPages);
        total.setLemmas(totalLemmas);

        List<DetailedStatisticsItem> detailed = sites.stream()
                .map(site -> {
                    long pages = pageRepository.countBySite(site);
                    long lemmas = lemmaRepository.countBySite(site);
                    var detailedStatisticsItem = new DetailedStatisticsItem();
                    detailedStatisticsItem.setUrl(site.getUrl());
                    detailedStatisticsItem.setName(site.getName());
                    detailedStatisticsItem.setStatus(site.getStatus().name());
                    detailedStatisticsItem.setStatusTime(site.getStatusTime().toEpochSecond(ZoneOffset.UTC));
                    detailedStatisticsItem.setError(site.getLastError() != null ? site.getLastError() : "");
                    detailedStatisticsItem.setPages(pages);
                    detailedStatisticsItem.setLemmas(lemmas);
                    return detailedStatisticsItem;
                })
                .collect(Collectors.toList());

        StatisticsData statisticsData = new StatisticsData();
        statisticsData.setTotal(total);
        statisticsData.setDetailed(detailed);
        var response = new StatisticsResponse();
        response.setStatistics(statisticsData);
        response.setResult(true);
        return response;
    }
}