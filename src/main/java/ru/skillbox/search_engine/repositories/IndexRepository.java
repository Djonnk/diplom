package ru.skillbox.search_engine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.skillbox.search_engine.model.Index;
import ru.skillbox.search_engine.model.Lemma;
import ru.skillbox.search_engine.model.Page;

import java.util.List;
import java.util.Optional;

public interface IndexRepository extends JpaRepository<Index, Integer> {

    List<Index> findByLemma(Lemma lemma);
    Optional<Index> findByPageAndLemma(Page page, Lemma lemma);
    boolean existsByPageAndLemma(Page page, Lemma lemma);
}
