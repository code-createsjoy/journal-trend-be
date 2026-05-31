package com.norman.swp391.repository;

import com.norman.swp391.entity.FollowTopic;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

/**
 * Kho truy cập theo dõi chủ đề.
 */
public interface FollowTopicRepository extends JpaRepository<FollowTopic, Long> {
    /**
     * Tìm kiếm: findByUserId.
     */
    List<FollowTopic> findByUserId(Long userId);

    /**
     * Tìm bản ghi theo dõi user–chủ đề.
     */
    Optional<FollowTopic> findByUserIdAndTopicId(Long userId, Long topicId);

    /**
     * Kiểm tra user đã theo dõi chủ đề chưa.
     */
    boolean existsByUserIdAndTopicId(Long userId, Long topicId);

    long countByUserId(Long userId);

    /**
     * Lấy mọi người theo dõi một chủ đề.
     */
    List<FollowTopic> findByTopicId(Long topicId);

    /**
     * Đếm lượt theo dõi theo chủ đề (topicId, count).
     */
    @Query("""
        SELECT ft.topic.id, COUNT(ft) FROM FollowTopic ft
        GROUP BY ft.topic.id
        ORDER BY COUNT(ft) DESC
        """)
    /**
     * Xử lý nghiệp vụ: countFollowsByTopic.
     */
    List<Object[]> countFollowsByTopic(Pageable pageable);
}
