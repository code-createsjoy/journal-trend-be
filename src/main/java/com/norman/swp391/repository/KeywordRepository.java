package com.norman.swp391.repository;

import com.norman.swp391.entity.Keyword;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KeywordRepository extends JpaRepository<Keyword, Long> {
    Optional<Keyword> findByTerm(String term);
}
