package ru.skillbox.search_engine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.skillbox.search_engine.model.Site;

import java.util.Optional;

public interface SiteRepository extends JpaRepository<Site, Integer> {
    Optional<Site> findByUrl(String url);
}