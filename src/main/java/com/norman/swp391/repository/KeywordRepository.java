package com.norman.swp391.repository;

import com.norman.swp391.entity.Keyword;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KeywordRepository extends JpaRepository<Keyword, Long> {
    Optional<Keyword> findByTerm(String term);

    @org.springframework.data.jpa.repository.Query("""
        SELECT k FROM Keyword k
        WHERE LOWER(k.domain) IN :domains
        ORDER BY k.paperCount ASC
        """)
    List<Keyword> findResearchGapsInDomains(@org.springframework.data.repository.query.Param("domains") java.util.Collection<String> domains, org.springframework.data.domain.Pageable pageable);

    @org.springframework.data.jpa.repository.Query("SELECT k FROM Keyword k WHERE LOWER(k.term) IN :terms")
    java.util.List<Keyword> findByTermInIgnoreCase(@org.springframework.data.repository.query.Param("terms") java.util.Collection<String> terms);
}
