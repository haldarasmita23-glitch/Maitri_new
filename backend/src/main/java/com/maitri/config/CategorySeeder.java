package com.maitri.config;

import com.maitri.model.Category;
import com.maitri.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Category Seeder — Phase 4.
 *
 * Idempotently seeds the 4 initial directory categories when the backend
 * starts. The slugs are hardcoded because the frontend links to them
 * (vendors.html?category=street-food, etc.) and they must never change.
 *
 * ─── IDEMPOTENT ───────────────────────────────────────────────────────────
 *   Before inserting, it checks whether a category with the same slug already
 *   exists. If yes, it skips that category. Re-running is always safe.
 *
 * ─── THE 4 INITIAL CATEGORIES ─────────────────────────────────────────────
 *   Street Food         → street-food
 *   Tailors             → tailors
 *   Printing & Xerox    → printing
 *   Mobile/Laptop Repair→ repair
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(1)
public class CategorySeeder implements CommandLineRunner {

    private final CategoryRepository categoryRepository;

    private static final List<SeedCategory> INITIAL_CATEGORIES = List.of(
            new SeedCategory("Street Food", "street-food"),
            new SeedCategory("Tailors", "tailors"),
            new SeedCategory("Printing & Xerox", "printing"),
            new SeedCategory("Mobile/Laptop Repair", "repair")
    );

    @Override
    public void run(String... args) {
        for (SeedCategory seed : INITIAL_CATEGORIES) {
            if (categoryRepository.existsBySlug(seed.slug())) {
                log.debug("[CategorySeeder] Already exists, skipping: slug={}", seed.slug());
                continue;
            }

            Category category = Category.builder()
                    .categoryName(seed.name())
                    .slug(seed.slug())
                    .active(true)
                    .createdAt(LocalDateTime.now())
                    .build();

            categoryRepository.save(category);
            log.info("[CategorySeeder] Seeded category: name={}, slug={}", seed.name(), seed.slug());
        }
    }

    private record SeedCategory(String name, String slug) {}
}