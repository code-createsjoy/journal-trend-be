package com.norman.swp391.repository;

import com.norman.swp391.entity.ReferenceMetadata;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository cho ReferenceMetadata — cache metadata nhẹ của referenced works.
 */
public interface ReferenceMetadataRepository extends JpaRepository<ReferenceMetadata, Long> {

    Optional<ReferenceMetadata> findByOpenAlexId(String openAlexId);

    List<ReferenceMetadata> findByOpenAlexIdIn(List<String> openAlexIds);
}
