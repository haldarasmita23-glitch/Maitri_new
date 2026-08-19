package com.maitri.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Category model for the Maitri Directory — Phase 4.
 *
 * Stores the directory's business categories (Street Food, Tailors, etc.).
 *
 * ─── FIELDS ────────────────────────────────────────────────────────────────
 *   id            — MongoDB auto-generated identifier
 *   categoryName  — Display name (e.g. "Street Food"), unique
 *   categoryImage — Optional image URL shown on the home page grid
 *   slug          — URL-friendly stable key (e.g. "street-food").
 *                   The frontend uses this in vendor URLs and filters,
 *                   so it never changes after creation.
 *   active        — true = visible to the public, false = hidden (disabled)
 *   createdAt     — When this category was created
 *
 * UNIQUE INDEXES:
 *   categoryName — two categories can never share the same display name
 *   slug         — two categories can never share the same URL slug
 *
 * @Document(collection = "categories") — maps to the "categories" MongoDB collection
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "categories")
public class Category {

    @Id
    private String id;

    @Indexed(unique = true)
    private String categoryName;

    private String categoryImage;

    @Indexed(unique = true)
    private String slug;

    @Builder.Default
    private boolean active = true;

    private LocalDateTime createdAt;
}