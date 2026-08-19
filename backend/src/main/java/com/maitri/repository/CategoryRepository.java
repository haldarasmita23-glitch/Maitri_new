package com.maitri.repository;

import com.maitri.model.Category;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Category Repository — data access layer for the Category model (Phase 4).
 *
 * Extends MongoRepository, so it inherits findAll(), findById(), save(),
 * and deleteAll() for free. The custom methods below support the
 * CategoryService business rules (duplicate detection, active filtering).
 *
 * Spring Data derives the query implementation from the method name:
 *   existsByCategoryName(...)  → "is there a category with this name?"
 *   existsByCategoryNameAndIdNot(...) → "...with this name, excluding this id?"
 */
@Repository
public interface CategoryRepository extends MongoRepository<Category, String> {

    /** Finds a category by its stable URL slug. */
    Optional<Category> findBySlug(String slug);

    /** Returns only active (publicly visible) categories, sorted alphabetically. */
    List<Category> findByActiveTrueOrderByCategoryNameAsc();

    /** True when a category with this exact name already exists (for create). */
    boolean existsByCategoryName(String categoryName);

    /** True when a category with this name exists, excluding the given id (for update). */
    boolean existsByCategoryNameAndIdNot(String categoryName, String id);

    /** True when a category with this slug already exists (for create). */
    boolean existsBySlug(String slug);

    /** True when a category with this slug exists, excluding the given id (for update). */
    boolean existsBySlugAndIdNot(String slug, String id);
}