package ru.skillbox.search_engine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.skillbox.search_engine.model.Page;
import ru.skillbox.search_engine.model.Site;

public interface PageRepository extends JpaRepository<Page, Integer> {
    boolean existsByPathAndSite(String path, Site site);
    Page findByPathAndSite(String path, Site site);
    long countBySite(Site site);
}
