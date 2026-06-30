package com.norman.swp391.repository;

import com.norman.swp391.entity.PaperCitation;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

public interface PaperCitationRepository extends JpaRepository<PaperCitation, Long> {

    List<PaperCitation> findByPaperId(Long paperId);

    @Modifying
    @Transactional
    void deleteByPaperId(Long paperId);

    boolean existsByPaperIdAndFetchedAtAfter(Long paperId, LocalDateTime threshold);
}
