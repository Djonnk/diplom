package ru.skillbox.search_engine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import ru.skillbox.search_engine.config.SitesList;
import ru.skillbox.search_engine.config.SitesList.ConfigSite;
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

        for (ConfigSite configSite : sitesList.getSites()) {
            new Thread(() -> indexSite(configSite)).start();
        }
    }

    public void stopIndexing() {
        isIndexing = false;
        log.info("Indexing stopped by user");
    }

    private void indexSite(ConfigSite configSite) {
        log.info("Indexing site: {}", configSite.getUrl());
        Site site = new Site();
        site.setUrl(configSite.getUrl());
        site.setName(configSite.getName());
        site.setStatus(Status.INDEXING);
        site.setStatusTime(LocalDateTime.now());
        siteRepository.save(site);

        boolean hasErrors = false;
        try {
            ForkJoinPool pool = new ForkJoinPool();
            pool.invoke(new SiteCrawler(site, site.getUrl(), this));
            if (isIndexing) {
                site.setStatus(Status.INDEXED);
                log.info("Site indexing completed: {}", site.getUrl());
            } else {
                site.setStatus(Status.FAILED);
                site.setLastError("Indexing stopped by user");
                log.warn("Site indexing interrupted: {}", site.getUrl());
                hasErrors = true;
            }
        } catch (Exception e) {
            site.setStatus(Status.FAILED);
            site.setLastError(e.getMessage());
            log.error("Error indexing site {}: {}", site.getUrl(), e.getMessage());
            hasErrors = true;
        }
        site.setStatusTime(LocalDateTime.now());
        siteRepository.save(site);

        if (hasErrors && pageRepository.countBySite(site) == 0) {
            site.setStatus(Status.FAILED);
            site.setLastError("No pages indexed due to errors");
            siteRepository.save(site);
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