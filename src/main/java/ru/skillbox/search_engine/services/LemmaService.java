package ru.skillbox.search_engine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.morphology.LuceneMorphology;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;
import ru.skillbox.search_engine.model.Lemma;
import ru.skillbox.search_engine.model.Page;
import ru.skillbox.search_engine.model.Site;
import ru.skillbox.search_engine.repositories.IndexRepository;
import ru.skillbox.search_engine.repositories.LemmaRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class LemmaService {

    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final LuceneMorphology russianLuceneMorphology;
    private final LuceneMorphology englishLuceneMorphology;

    public Map<String, Integer> getLemmasFromText(String html) {
        String text = Jsoup.parse(html).text();
        Map<String, Integer> lemmas = new HashMap<>();
        String[] words = text.toLowerCase().split("\\P{L}+");

        for (String word : words) {
            if (word.isEmpty()) continue;

            if (isCyrillic(word)) {
                processWord(word, russianLuceneMorphology, lemmas);
            } else if (isLatin(word)) {
                processWord(word, englishLuceneMorphology, lemmas);
            }
        }
        return lemmas;
    }

    private void processWord(String word, LuceneMorphology morphology, Map<String, Integer> lemmas) {
        try {
            if (isServiceWord(word, morphology)) return;
            List<String> normalForms = morphology.getNormalForms(word);
            if (!normalForms.isEmpty()) {
                String lemma = normalForms.get(0);
                lemmas.put(lemma, lemmas.getOrDefault(lemma, 0) + 1);
            }
        } catch (Exception e) {
            log.warn("Skipping word '{}': {}", word, e.getMessage());
        }
    }

    private boolean isServiceWord(String word, LuceneMorphology morphology) {
        try {
            List<String> morphInfo = morphology.getMorphInfo(word);
            return morphInfo.stream().anyMatch(info -> info.contains("CONJ") || info.contains("PREP") || info.contains("INTJ") || info.contains("PART"));
        } catch (Exception e) {
            log.warn("Error checking service word '{}': {}", word, e.getMessage());
            return true;
        }
    }

    private boolean isCyrillic(String word) {
        return word.matches("^[а-яё]+$");
    }

    private boolean isLatin(String word) {
        return word.matches("^[a-z]+$");
    }

    public void indexPage(Page page) {
        log.info("Starting lemma indexing for page: {}", page.getPath());
        Map<String, Integer> lemmas = getLemmasFromText(page.getContent());
        log.info("Found {} lemmas for page: {}", lemmas.size(), page.getPath());

        Site site = page.getSite();
        for (Map.Entry<String, Integer> entry : lemmas.entrySet()) {
            String lemmaText = entry.getKey();
            int frequency = entry.getValue();

            Lemma lemma = lemmaRepository.findByLemmaAndSite(lemmaText, site)
                    .orElseGet(() -> {
                        Lemma newLemma = new Lemma();
                        newLemma.setSite(site);
                        newLemma.setLemma(lemmaText);
                        newLemma.setFrequency(0);
                        return newLemma;
                    });
            lemma.setFrequency(lemma.getFrequency() + 1);
            lemmaRepository.save(lemma);
            log.debug("Saved lemma: {} for site: {}", lemmaText, site.getUrl());

            ru.skillbox.search_engine.model.Index index = indexRepository.findByPageAndLemma(page, lemma)
                    .orElseGet(() -> {
                        ru.skillbox.search_engine.model.Index newIndex = new ru.skillbox.search_engine.model.Index();
                        newIndex.setPage(page);
                        newIndex.setLemma(lemma);
                        return newIndex;
                    });
            index.setRank(frequency);
            indexRepository.save(index);
            log.debug("Saved index for lemma: {} on page: {}", lemmaText, page.getPath());
        }
        log.info("Finished lemma indexing for page: {}", page.getPath());
    }
}
