package ru.skillbox.search_engine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.skillbox.search_engine.model.Lemma;
import ru.skillbox.search_engine.model.Site;

import java.util.Optional;

public interface LemmaRepository extends JpaRepository<Lemma, Integer> {

    Optional<Lemma> findByLemmaAndSite(String lemma, Site site);
    boolean existsByLemmaAndSite(String lemma, Site site);
}