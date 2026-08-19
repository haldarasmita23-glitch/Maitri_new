package com.maitri.service;

import com.maitri.dto.category.CategoryRequest;
import com.maitri.dto.category.CategoryResponse;
import com.maitri.exception.CategoryNotFoundException;
import com.maitri.exception.DuplicateCategoryNameException;
import com.maitri.model.Category;
import com.maitri.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Category Service — business logic for the Category module (Phase 4).
 *
 * ─── RESPONSIBILITIES ─────────────────────────────────────────────────────
 *   getActiveCategories()   — List publicly visible categories (active only)
 *   createCategory()        — Create a category (duplicate name/slug → 409)
 *   updateCategory()        — Update a category (404 if unknown, dup checks)
 *   disableCategory()       - Hide a category from the public list
 *
 * ─── SLUG RULE ────────────────────────────────────────────────────────────
 *   If the request supplies a slug, it is used as-is (after format validation).
 *   If not, the slug is auto-generated from the category name
 *   (e.g. "Printing & Xerox" → "printing-xerox"). Slug collisions → 409.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CategoryService {

    private final CategoryRepository categoryRepository;

    /** Returns only categories that are active (publicly visible), sorted A→Z. */
    public List<CategoryResponse> getActiveCategories() {
        return categoryRepository.findByActiveTrueOrderByCategoryNameAsc()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /** Creates a new category. Duplicate name or slug → 409 Conflict. */
    public CategoryResponse createCategory(CategoryRequest request) {
        if (categoryRepository.existsByCategoryName(request.getCategoryName())) {
            throw new DuplicateCategoryNameException(
                    "A category with this name already exists."
            );
        }

        String slug = resolveSlug(request.getSlug(), request.getCategoryName());
        if (categoryRepository.existsBySlug(slug)) {
            throw new DuplicateCategoryNameException(
                    "A category with this slug already exists."
            );
        }

        Category category = Category.builder()
                .categoryName(request.getCategoryName())
                .categoryImage(request.getCategoryImage())
                .slug(slug)
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();

        Category saved = categoryRepository.save(category);
        log.info("[Category] Created: id={}, name={}, slug={}",
                saved.getId(), saved.getCategoryName(), saved.getSlug());
        return toResponse(saved);
    }

    /**
     * Updates an existing category. Unknown id → 404. A name/slug already
     * used by a DIFFERENT category → 409 Conflict.
     */
    public CategoryResponse updateCategory(String id, CategoryRequest request) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new CategoryNotFoundException("Category not found."));

        if (categoryRepository.existsByCategoryNameAndIdNot(request.getCategoryName(), id)) {
            throw new DuplicateCategoryNameException(
                    "A category with this name already exists."
            );
        }

        String slug = (request.getSlug() != null && !request.getSlug().isBlank())
                ? request.getSlug()
                : category.getSlug();

        if (categoryRepository.existsBySlugAndIdNot(slug, id)) {
            throw new DuplicateCategoryNameException(
                    "A category with this slug already exists."
            );
        }

        category.setCategoryName(request.getCategoryName());
        category.setCategoryImage(request.getCategoryImage());
        category.setSlug(slug);

        Category saved = categoryRepository.save(category);
        log.info("[Category] Updated: id={}, name={}", saved.getId(), saved.getCategoryName());
        return toResponse(saved);
    }

    /**
     * Hides a category from the public list by setting active = false.
     * Unknown id → 404. The category document is kept for historical data.
     */
    public CategoryResponse disableCategory(String id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new CategoryNotFoundException("Category not found."));

        category.setActive(false);
        Category saved = categoryRepository.save(category);
        log.info("[Category] Disabled: id={}, name={}", saved.getId(), saved.getCategoryName());
        return toResponse(saved);
    }

    /** Uses the provided slug if present, otherwise auto-generates one. */
    private String resolveSlug(String slug, String categoryName) {
        if (slug != null && !slug.isBlank()) {
            return slug;
        }
        return slugify(categoryName);
    }

    /** Converts a display name to a URL-safe slug: "Printing & Xerox" → "printing-xerox". */
    private String slugify(String name) {
        return name.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("(^-|-$)", "");
    }

    /** Maps a Category document to its API response shape. */
    private CategoryResponse toResponse(Category category) {
        return CategoryResponse.builder()
                .id(category.getId())
                .categoryName(category.getCategoryName())
                .categoryImage(category.getCategoryImage())
                .slug(category.getSlug())
                .active(category.isActive())
                .createdAt(category.getCreatedAt())
                .build();
    }
}