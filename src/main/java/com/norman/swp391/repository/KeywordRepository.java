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

    /** Tra cứu nhiều keyword theo term (khớp chính xác) trong 1 lần — dùng để tránh N+1 khi lookup theo batch. */
    @org.springframework.data.jpa.repository.Query("SELECT k FROM Keyword k WHERE k.term IN :terms")
    java.util.List<Keyword> findByTermIn(@org.springframework.data.repository.query.Param("terms") java.util.Collection<String> terms);

    /** Danh sách domain duy nhất (không rỗng) để đổ vào dropdown filter category ở FE. */
    @org.springframework.data.jpa.repository.Query(
            value = "SELECT DISTINCT domain FROM keywords WHERE domain IS NOT NULL AND TRIM(domain) <> ''",
            nativeQuery = true)
    java.util.List<String> findDistinctDomains();

    /** BR-35: gợi ý autocomplete theo term khớp gần đúng, ưu tiên keyword nhiều bài hơn. */
    @org.springframework.data.jpa.repository.Query("""
        SELECT k FROM Keyword k
        WHERE LOWER(k.term) LIKE LOWER(CONCAT('%', :q, '%'))
        ORDER BY k.paperCount DESC
        """)
    List<Keyword> suggestByTerm(@org.springframework.data.repository.query.Param("q") String q, org.springframework.data.domain.Pageable pageable);
}
