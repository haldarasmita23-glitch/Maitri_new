package com.maitri.controller;

import com.maitri.dto.ApiResponse;
import com.maitri.dto.category.CategoryRequest;
import com.maitri.dto.category.CategoryResponse;
import com.maitri.service.CategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Category Controller — Phase 4.
 *
 * ─── ENDPOINTS & ACCESS ────────────────────────────────────────────────────
 *   GET  /api/categories                — PUBLIC: active categories only
 *   POST /api/categories                — ADMIN ONLY: create → 201 Created
 *   PUT  /api/categories/{id}           — ADMIN ONLY: update
 *   PATCH /api/categories/{id}/disable  — ADMIN ONLY: hide from public list
 *
 * All responses use the standard ApiResponse wrapper.
 */
@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    /** PUBLIC — lists all active (publicly visible) categories, A→Z. */
    @GetMapping
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> listCategories() {
        return ResponseEntity.ok(
                ApiResponse.success("Categories retrieved.", categoryService.getActiveCategories())
        );
    }

    /** ADMIN ONLY — creates a category. Duplicate name/slug → 409. */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CategoryResponse>> createCategory(
            @Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.success("Category created.", categoryService.createCategory(request))
        );
    }

    /** ADMIN ONLY — updates a category. Unknown id → 404. */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CategoryResponse>> updateCategory(
            @PathVariable String id,
            @Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success("Category updated.", categoryService.updateCategory(id, request))
        );
    }

    /** ADMIN ONLY — disables a category so it disappears from the public GET. */
    @PatchMapping("/{id}/disable")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CategoryResponse>> disableCategory(@PathVariable String id) {
        return ResponseEntity.ok(
                ApiResponse.success("Category disabled.", categoryService.disableCategory(id))
        );
    }
}