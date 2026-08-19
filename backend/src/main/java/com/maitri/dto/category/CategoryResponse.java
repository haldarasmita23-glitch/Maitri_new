package com.maitri.dto.category;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Category Response DTO — Phase 4.
 *
 * The public-facing shape of a category. Mirrors the Category model but
 * decouples the API contract from the database document, so internal model
 * changes never leak into the API.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryResponse {

    private String id;
    private String categoryName;
    private String categoryImage;
    private String slug;
    private boolean active;
    private LocalDateTime createdAt;
}