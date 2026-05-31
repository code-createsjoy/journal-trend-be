package com.norman.swp391.repository;

import com.norman.swp391.entity.Author;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Kho truy cập thực thể tác giả.
 */
public interface AuthorRepository extends JpaRepository<Author, Long> {
    /**
     * Tìm kiếm: findByOpenAlexIdIgnoreCase.
     */
    Optional<Author> findByOpenAlexIdIgnoreCase(String openAlexId);

    /**
     * Tìm tác giả đầu tiên theo tên và đơn vị công tác.
     */
    Optional<Author> findFirstByNameIgnoreCaseAndAffiliationIgnoreCaseOrderByIdAsc(String name, String affiliation);

    /**
     * Tìm kiếm tác giả theo từ khóa trong tên.
     */
    @Query("SELECT a FROM Author a WHERE :q IS NULL OR LOWER(a.name) LIKE LOWER(CONCAT('%', :q, '%'))")
    Page<Author> search(@Param("q") String q, Pageable pageable);

    /**
     * Lấy tác giả nổi bật theo lượt trích dẫn giảm dần.
     */
    @Query("SELECT a FROM Author a ORDER BY a.citationCount DESC")
    Page<Author> findFeatured(Pageable pageable);
}
