package ru.skillbox.search_engine.services;

import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;
import ru.skillbox.search_engine.dto.SearchResult;
import ru.skillbox.search_engine.model.Index;
import ru.skillbox.search_engine.model.Lemma;
import ru.skillbox.search_engine.model.Page;
import ru.skillbox.search_engine.repositories.IndexRepository;
import ru.skillbox.search_engine.repositories.LemmaRepository;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SearchService {
    private final LemmaService lemmaService;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;

    /**
     * Выполняет поиск по запросу для всех сайтов или конкретного сайта.
     *
     * @param query   Поисковый запрос
     * @param siteUrl URL сайта (опционально, если null — поиск по всем сайтам)
     * @param offset  Смещение для пагинации
     * @param limit   Лимит результатов на страницу
     * @return Список результатов поиска
     */
    public List<SearchResult> search(String query, String siteUrl, int offset, int limit) {
        Map<String, Integer> queryLemmas = lemmaService.getLemmasFromText(query);
        if (queryLemmas.isEmpty()) {
            return Collections.emptyList();
        }

        List<Lemma> lemmas = findLemmas(queryLemmas.keySet(), siteUrl);
        if (lemmas.isEmpty()) {
            return Collections.emptyList();
        }

        lemmas.sort(Comparator.comparingInt(Lemma::getFrequency));

        List<Page> relevantPages = findRelevantPages(lemmas);
        if (relevantPages.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Page, Float> relevanceMap = calculateRelevance(relevantPages, lemmas);
        float maxRelevance = Collections.max(relevanceMap.values());

        return relevantPages.stream()
                .map(page -> {
                    float relativeRelevance = relevanceMap.get(page) / maxRelevance;
                    String snippet = generateSnippet(page.getContent(), queryLemmas.keySet());
                    String title = extractTitle(page.getContent());
                    return new SearchResult(page.getSite().getUrl() + page.getPath(), title, snippet, relativeRelevance);
                })
                .sorted(Comparator.comparingDouble(SearchResult::getRelevance).reversed())
                .skip(offset)
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * Находит леммы в базе данных по запросу и, опционально, по сайту.
     */
    private List<Lemma> findLemmas(Set<String> lemmaTexts, String siteUrl) {
        if (siteUrl == null) {
            return lemmaTexts.stream()
                    .flatMap(text -> lemmaRepository.findAll().stream()
                            .filter(lemma -> lemma.getLemma().equals(text)))
                    .collect(Collectors.toList());
        } else {
            return lemmaTexts.stream()
                    .flatMap(text -> lemmaRepository.findAll().stream()
                            .filter(lemma -> lemma.getLemma().equals(text) && lemma.getSite().getUrl().equals(siteUrl)))
                    .collect(Collectors.toList());
        }
    }

    /**
     * Находит страницы, содержащие все леммы из списка.
     */
    private List<Page> findRelevantPages(List<Lemma> lemmas) {
        List<Page> pages = indexRepository.findByLemma(lemmas.get(0)).stream()
                .map(Index::getPage)
                .collect(Collectors.toList());

        for (int i = 1; i < lemmas.size() && !pages.isEmpty(); i++) {
            Lemma lemma = lemmas.get(i);
            List<Page> nextPages = indexRepository.findByLemma(lemma).stream()
                    .map(Index::getPage)
                    .collect(Collectors.toList());
            pages.retainAll(nextPages); // Оставляем только пересечение
        }

        return pages;
    }

    /**
     * Вычисляет абсолютную релевантность страниц.
     */
    private Map<Page, Float> calculateRelevance(List<Page> pages, List<Lemma> lemmas) {
        Map<Page, Float> relevanceMap = new HashMap<>();
        for (Page page : pages) {
            float absRelevance = 0;
            for (Lemma lemma : lemmas) {
                Optional<Index> index = indexRepository.findByPageAndLemma(page, lemma);
                absRelevance += index.map(Index::getRank).orElse(0f);
            }
            relevanceMap.put(page, absRelevance);
        }
        return relevanceMap;
    }

    /**
     * Генерирует сниппет с выделением слов из запроса.
     */
    private String generateSnippet(String content, Set<String> queryLemmas) {
        String text = Jsoup.parse(content).text();
        int snippetLength = 200;

        for (String lemma : queryLemmas) {
            int index = text.toLowerCase().indexOf(lemma);
            if (index != -1) {
                int start = Math.max(0, index - snippetLength / 2);
                int end = Math.min(text.length(), start + snippetLength);
                String snippet = text.substring(start, end);

                for (String q : queryLemmas) {
                    snippet = snippet.replaceAll("(?i)" + q, "<b>" + q + "</b>");
                }
                return snippet;
            }
        }

        return text.length() > snippetLength ? text.substring(0, snippetLength) : text;
    }

    /**
     * Извлекает заголовок страницы из HTML-контента.
     */
    private String extractTitle(String content) {
        String text = Jsoup.parse(content).text();
        if (content.contains("<title>")) {
            return content.split("<title>")[1].split("</title>")[0];
        }

        return text.length() > 50 ? text.substring(0, 50) + "..." : text;
    }
}