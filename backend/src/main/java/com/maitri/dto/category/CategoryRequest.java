package com.maitri.dto.category;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Category Create/Update Request DTO — Phase 4.
 *
 * Used by the ADMIN endpoints:
 *   POST /api/categories        — create a category
 *   PUT  /api/categories/{id}   — update a category
 *
 * ─── VALIDATION ────────────────────────────────────────────────────────────
 *   categoryName — required, max 50 chars
 *   categoryImage — optional, max 500 chars (image URL)
 *   slug         — optional. If omitted, the service auto-generates it from
 *                  the category name. If provided, it must be lowercase
 *                  alphanumeric words separated by hyphens.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryRequest {

    @NotBlank(message = "Category name is required.")
    @Size(max = 50, message = "Category name must be at most 50 characters.")
    private String categoryName;

    @Size(max = 500, message = "Category image URL must be at most 500 characters.")
    private String categoryImage;

    @Pattern(
            regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$",
            message = "Slug must be lowercase letters/numbers separated by hyphens (e.g. street-food)."
    )
    @Size(max = 100, message = "Slug must be at most 100 characters.")
    private String slug;
}