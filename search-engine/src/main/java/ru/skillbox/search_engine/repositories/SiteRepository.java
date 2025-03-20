package ru.skillbox.search_engine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.skillbox.search_engine.model.Site;

public interface SiteRepository extends JpaRepository<Site, Integer> {
}