package ru.skillbox.search_engine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import ru.skillbox.search_engine.config.SitesList;
import ru.skillbox.search_engine.model.Page;
import ru.skillbox.search_engine.model.Site;
import ru.skillbox.search_engine.model.Status;
import ru.skillbox.search_engine.repositories.PageRepository;
import ru.skillbox.search_engine.repositories.SiteRepository;

import java.time.LocalDateTime;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;

@Slf4j
@Service
@RequiredArgsConstructor
public class IndexingService {
    private final SitesList sitesList;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaService lemmaService;

    private volatile boolean isIndexing = false;

    public void startIndexing() {
        if (isIndexing) {
            log.error("Indexing is already running");
            throw new IllegalStateException("Indexing is already running");
        }
        isIndexing = true;
        log.info("Starting indexing for {} sites", sitesList.getSites().size());

        for (SitesList.Site site : sitesList.getSites()) {
            new Thread(() -> indexSite(site)).start();
        }
    }

    public void stopIndexing() {
        isIndexing = false;
        log.info("Indexing stopped by user");
    }

    public void indexSinglePage(String url) {
        String baseUrl = url.substring(0, url.indexOf('/', 8) + 1);
        log.info("Indexing single page: {}", url);

        Site site = siteRepository.findByUrl(baseUrl)
                .orElseGet(() -> {
                    SitesList.Site configSite = sitesList.getSites().stream()
                            .filter(s -> s.getUrl().equals(baseUrl))
                            .findFirst()
                            .orElseThrow(() -> new IllegalArgumentException("Site not found in configuration for URL: " + url));
                    Site newSite = new Site();
                    newSite.setUrl(configSite.getUrl());
                    newSite.setName(configSite.getName());
                    newSite.setStatus(Status.INDEXING);
                    newSite.setStatusTime(LocalDateTime.now());
                    log.info("Created new site: {}", newSite.getUrl());
                    return siteRepository.save(newSite);
                });

        try {
            log.info("Crawling page: {}", url);
            Document doc = Jsoup.connect(url)
                    .userAgent("HeliontSearchBot")
                    .referrer("http://www.google.com")
                    .timeout(10000)
                    .get();

            String path = url.startsWith(site.getUrl()) ? url.replace(site.getUrl(), "/") : url;

            if (pageRepository.existsByPathAndSite(path, site)) {
                log.info("Page already exists, updating: {}", path);
                Page existingPage = pageRepository.findByPathAndSite(path, site);
                existingPage.setCode(doc.connection().response().statusCode());
                existingPage.setContent(doc.html());
                pageRepository.save(existingPage);
            } else {
                Page page = new Page();
                page.setSite(site);
                page.setPath(path);
                page.setCode(doc.connection().response().statusCode());
                page.setContent(doc.html());
                pageRepository.save(page);
                log.info("Saved new page: {}", page.getPath());
            }

            Page page = pageRepository.findByPathAndSite(path, site);
            log.info("Indexing lemmas for page: {}", page.getPath());
            lemmaService.indexPage(page);

            site.setStatus(Status.INDEXED);
            site.setStatusTime(LocalDateTime.now());
            siteRepository.save(site);
            log.info("Single page indexing completed for: {}", url);

        } catch (org.jsoup.HttpStatusException e) {
            log.error("HTTP error indexing page {}: Status={}, URL={}", url, e.getStatusCode(), e.getUrl());
            String path = url.startsWith(site.getUrl()) ? url.replace(site.getUrl(), "/") : url;
            Page errorPage = new Page();
            errorPage.setSite(site);
            errorPage.setPath(path);
            errorPage.setCode(e.getStatusCode());
            errorPage.setContent("HTTP Error: " + e.getStatusCode());
            pageRepository.save(errorPage);
            site.setStatus(Status.FAILED);
            site.setLastError("HTTP Error: " + e.getStatusCode());
            siteRepository.save(site);
        } catch (Exception e) {
            log.error("Error indexing page {}: {}", url, e.getMessage());
            site.setStatus(Status.FAILED);
            site.setLastError(e.getMessage());
            siteRepository.save(site);
        }
    }

    private void indexSite(SitesList.Site site) {
        log.info("Indexing site: {}", site.getUrl());
        Site siteEntity = new Site();
        siteEntity.setUrl(site.getUrl());
        siteEntity.setName(site.getName());
        siteEntity.setStatus(Status.INDEXING);
        siteEntity.setStatusTime(LocalDateTime.now());
        siteRepository.save(siteEntity);

        boolean hasErrors = false;
        try {
            ForkJoinPool pool = new ForkJoinPool();
            pool.invoke(new SiteCrawler(siteEntity, siteEntity.getUrl(), this));
            if (isIndexing) {
                siteEntity.setStatus(Status.INDEXED);
                log.info("Site indexing completed: {}", siteEntity.getUrl());
            } else {
                siteEntity.setStatus(Status.FAILED);
                siteEntity.setLastError("Indexing stopped by user");
                log.warn("Site indexing interrupted: {}", siteEntity.getUrl());
                hasErrors = true;
            }
        } catch (Exception e) {
            siteEntity.setStatus(Status.FAILED);
            siteEntity.setLastError(e.getMessage());
            log.error("Error indexing site {}: {}", siteEntity.getUrl(), e.getMessage());
            hasErrors = true;
        }
        siteEntity.setStatusTime(LocalDateTime.now());
        siteRepository.save(siteEntity);

        if (hasErrors && pageRepository.countBySite(siteEntity) == 0) {
            siteEntity.setStatus(Status.FAILED);
            siteEntity.setLastError("No pages indexed due to errors");
            siteRepository.save(siteEntity);
        }
    }

    private class SiteCrawler extends RecursiveAction {
        private final Site site;
        private final String url;
        private final IndexingService service;

        SiteCrawler(Site site, String url, IndexingService service) {
            this.site = site;
            this.url = url;
            this.service = service;
        }

        @Override
        protected void compute() {
            if (!service.isIndexing) return;

            try {
                log.info("Crawling page: {}", url);
                Document doc = Jsoup.connect(url)
                        .userAgent("HeliontSearchBot")
                        .referrer("http://www.google.com")
                        .timeout(10000)
                        .get();

                Page page = new Page();
                page.setSite(site);
                page.setPath(url.replace(site.getUrl(), "/"));
                page.setCode(doc.connection().response().statusCode());
                page.setContent(doc.html());
                pageRepository.save(page);
                log.info("Saved page: {}", page.getPath());

                log.info("Indexing lemmas for page: {}", page.getPath());
                service.lemmaService.indexPage(page);

                doc.select("a[href]").stream()
                        .map(link -> link.attr("abs:href"))
                        .filter(link -> link.startsWith(site.getUrl()))
                        .filter(link -> !pageRepository.existsByPathAndSite(link.replace(site.getUrl(), "/"), site))
                        .forEach(link -> {
                            SiteCrawler task = new SiteCrawler(site, link, service);
                            task.fork();
                            task.join();
                        });

                site.setStatusTime(LocalDateTime.now());
                siteRepository.save(site);

                Thread.sleep((long) (Math.random() * 4500 + 500));
            } catch (org.jsoup.HttpStatusException e) {
                log.error("HTTP error crawling page {}: Status={}, URL={}", url, e.getStatusCode(), e.getUrl());
                Page errorPage = new Page();
                errorPage.setSite(site);
                errorPage.setPath(url.replace(site.getUrl(), "/"));
                errorPage.setCode(e.getStatusCode());
                errorPage.setContent("HTTP Error: " + e.getStatusCode());
                pageRepository.save(errorPage);
            } catch (Exception e) {
                log.error("Error crawling page {}: {}", url, e.getMessage());
            }
        }
    }
}