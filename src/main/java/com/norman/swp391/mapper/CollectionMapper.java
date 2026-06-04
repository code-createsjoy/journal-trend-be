package com.norman.swp391.mapper;

import com.norman.swp391.dto.response.collection.CollectionResponse;
import com.norman.swp391.entity.PaperCollection;
import lombok.experimental.UtilityClass;

import java.util.List;

/**
 * Mapper CollectionMapper.
 */
@UtilityClass
public class CollectionMapper {

/**
 * Ánh xạ sang DTO/phản hồi: toResponse.
 */
    public static CollectionResponse toResponse(PaperCollection collection) {
        return toResponse(collection, 0);
    }

/**
 * Ánh xạ sang DTO/phản hồi: toResponse.
 */
    public static CollectionResponse toResponse(PaperCollection collection, int paperCount) {
        if (collection == null) {
            return null;
        }
        return CollectionResponse.builder()
                .id(collection.getId())
                .name(collection.getName())
                .description(collection.getDescription())
                .paperCount(paperCount)
                .createdAt(collection.getCreatedAt())
                .build();
    }

    /**
     * Thực hiện toResponseList.
     */
    public static List<CollectionResponse> toResponseList(
            List<PaperCollection> collections, List<Integer> paperCounts) {
        if (collections == null) {
            return List.of();
        }
        if (paperCounts == null || paperCounts.size() != collections.size()) {
            return collections.stream().map(CollectionMapper::toResponse).toList();
        }
        return java.util.stream.IntStream.range(0, collections.size())
                .mapToObj(i -> toResponse(collections.get(i), paperCounts.get(i)))
                .toList();
    }
}


