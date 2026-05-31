package com.norman.swp391.repository;

import com.norman.swp391.entity.Topic;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Kho truy cập chủ đề.
 */
public interface TopicRepository extends JpaRepository<Topic, Long> {

/**
 * Tìm kiếm: findByNameIgnoreCase.
 */
    Optional<Topic> findByNameIgnoreCase(String name);
}
