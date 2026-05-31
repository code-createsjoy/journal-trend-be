package com.norman.swp391.repository;

import com.norman.swp391.entity.PaperAuthor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Kho truy cập liên kết bài báo–tác giả.
 */
public interface PaperAuthorRepository extends JpaRepository<PaperAuthor, Long> {

/**
 * Tìm kiếm: findByPaperId.
 */
    List<PaperAuthor> findByPaperId(Long paperId);

/**
 * Tìm kiếm: findByAuthorId.
 */
    List<PaperAuthor> findByAuthorId(Long authorId);

    @Query("SELECT pa FROM PaperAuthor pa JOIN FETCH pa.paper WHERE pa.author.id = :authorId")
    List<PaperAuthor> findByAuthorIdWithPaper(@Param("authorId") Long authorId);

    @Query("SELECT pa FROM PaperAuthor pa JOIN FETCH pa.author JOIN FETCH pa.paper WHERE pa.paper.id IN :paperIds")
    List<PaperAuthor> findByPaperIdInWithAuthor(@Param("paperIds") List<Long> paperIds);

/**
 * Xử lý nghiệp vụ: countByAuthorId.
 */
    long countByAuthorId(Long authorId);
}
