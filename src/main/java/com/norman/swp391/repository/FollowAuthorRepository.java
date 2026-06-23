package com.norman.swp391.repository;
 
import com.norman.swp391.entity.FollowAuthor;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
 
public interface FollowAuthorRepository extends JpaRepository<FollowAuthor, Long> {
 
    boolean existsByUserIdAndAuthorId(Long userId, Long authorId);
 
    long countByUserId(Long userId);
 
    Optional<FollowAuthor> findByUserIdAndAuthorId(Long userId, Long authorId);
 
    List<FollowAuthor> findByUserId(Long userId);
 
    List<FollowAuthor> findByAuthorId(Long authorId);
}
