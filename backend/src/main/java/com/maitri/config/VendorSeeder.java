package com.maitri.config;

import com.maitri.model.Category;
import com.maitri.model.Role;
import com.maitri.model.User;
import com.maitri.model.Vendor;
import com.maitri.model.VendorStatus;
import com.maitri.repository.CategoryRepository;
import com.maitri.repository.UserRepository;
import com.maitri.repository.VendorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Vendor Seeder — Development/Testing Data Seeder.
 *
 * Seeds 12 vendor profiles (matching frontend mock data) along with their
 * corresponding VENDOR user accounts. Runs on application startup when the
 * 'local' profile is active.
 *
 * ─── DESIGN PRINCIPLES ─────────────────────────────────────────────────────
 *   - Idempotent: safe to run multiple times; skips existing users/vendors.
 *   - Creates exactly one VENDOR user per vendor profile (1:1 mapping).
 *   - Links vendor.userId to the VENDOR user's MongoDB _id (String).
 *   - Resolves category slugs to actual Category._id from the database.
 *   - All vendors are seeded with status = APPROVED for immediate visibility.
 *   - Runs AFTER CategorySeeder (via @Order) to ensure categories exist.
 *
 * ─── DATA SOURCE ───────────────────────────────────────────────────────────
 *   Vendor data mirrors frontend/js/mock-data.js MOCK_VENDORS array.
 *   This ensures frontend mock data and backend seed data stay in sync.
 *
 * ─── EXECUTION ORDER ──────────────────────────────────────────────────────
 *   @Order(2) ensures this runs AFTER CategorySeeder (@Order(1)).
 *   CategorySeeder must run first so categories exist for slug→ID resolution.
 *
 * ─── IDEMPOTENCY ───────────────────────────────────────────────────────────
 *   - Skips user creation if email already exists.
 *   - Skips vendor creation if user already has a vendor profile.
 *   - Safe to run on every application restart.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(2) // Runs after CategorySeeder (@Order(1) implied/default)
public class VendorSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final VendorRepository vendorRepository;
    private final CategoryRepository categoryRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Default password for all seeded vendor accounts.
     * Injected from property (gitignored) with a local-dev fallback.
     * Can be overridden via VENDOR_SEED_PASSWORD environment variable.
     */
    @Value("${vendor.seed.password:Vendor@LocalDev2026!}")
    private String defaultVendorPassword;

    /** Email domain for seeded vendor accounts. */
    private static final String VENDOR_EMAIL_DOMAIN = "@maitri.local";

    /** Vendor data matching frontend/js/mock-data.js MOCK_VENDORS array. */
    private static final List<SeedVendor> SEED_VENDORS = List.of(
            // ── Street Food (slug: street-food) ──────────────────────────────
            new SeedVendor(
                    "v001", "Shree Sagar Tiffin Centre", "Ramesh Kumar",
                    "street-food",
                    "Authentic South Indian breakfast and lunch since 1998. Known for our crispy dosas and filter coffee. A Peenya favourite for over two decades.",
                    "Near Gate 2, Peenya Industrial Area, Bengaluru",
                    "Peenya",
                    "+91 98450 12345",
                    "06:30", "14:00",
                    4.6, 87,
                    "🍛",
                    List.of("Dosa", "Idli", "Filter Coffee", "Veg")
            ),
            new SeedVendor(
                    "v002", "Annapoorna Mess", "Suresh Bhat",
                    "street-food",
                    "Home-style North Karnataka thali meals. Full meals available for lunch. Popular among factory workers and students.",
                    "Nagasandra Main Road, near Nagasandra Metro Station",
                    "Nagasandra",
                    "+91 97400 55678",
                    "11:00", "15:30",
                    4.3, 62,
                    "🥘",
                    List.of("Thali", "Lunch", "Veg & Non-Veg")
            ),
            new SeedVendor(
                    "v003", "Peenya Juice Corner", "Mohammed Salim",
                    "street-food",
                    "Fresh fruit juices, sugarcane juice, and shakes. Best sugarcane juice in the area — no added sugar.",
                    "Peenya 2nd Stage, near Bus Stand",
                    "Peenya",
                    "+91 96060 34567",
                    "08:00", "20:00",
                    4.8, 115,
                    "🥤",
                    List.of("Juices", "Shakes", "Fresh", "Cold Drinks")
            ),
            new SeedVendor(
                    "v004", "Meghana Fast Food", "Lakshmi Devi",
                    "street-food",
                    "Puri bhaji, gobi manchurian, and evening snacks. Very popular during evening hours.",
                    "3rd Cross, Nagasandra, Bengaluru",
                    "Nagasandra",
                    "+91 99720 11234",
                    "16:00", "21:30",
                    4.1, 43,
                    "🍱",
                    List.of("Snacks", "Evening", "Veg")
            ),

            // ── Tailors (slug: tailors) ──────────────────────────────────────
            new SeedVendor(
                    "v005", "New Style Tailors", "Gopal Naidu",
                    "tailors",
                    "Expert tailoring for men's and women's clothing. Specialise in salwar kameez, kurta, and formal shirts. 20+ years of experience.",
                    "Peenya 1st Stage, Main Road, Bengaluru",
                    "Peenya",
                    "+91 99001 78900",
                    "10:00", "19:30",
                    4.5, 54,
                    "🧵",
                    List.of("Stitching", "Alterations", "Ladies & Gents")
            ),
            new SeedVendor(
                    "v006", "Divya Fashion Boutique", "Divya Menon",
                    "tailors",
                    "Ladies boutique specialising in saree blouses, lehenga, and designer kurtas. Embroidery work available.",
                    "Nagasandra, near Metro Pillar 142",
                    "Nagasandra",
                    "+91 88004 67890",
                    "10:30", "20:00",
                    4.7, 38,
                    "👗",
                    List.of("Blouses", "Lehenga", "Embroidery", "Ladies")
            ),
            new SeedVendor(
                    "v007", "Raja Gents Tailor", "Rajendra Sharma",
                    "tailors",
                    "Formal shirts, trousers, and suits for men. Quick turnaround time. Uniforms for industries also accepted.",
                    "Peenya Industrial Area, Block C",
                    "Peenya",
                    "+91 98300 22100",
                    "09:00", "18:00",
                    4.2, 29,
                    "👔",
                    List.of("Formal", "Shirts", "Uniforms", "Gents")
            ),

            // ── Printing & Xerox (slug: printing) ────────────────────────────
            new SeedVendor(
                    "v008", "Peenya Xerox & Prints", "Vinod Kumar",
                    "printing",
                    "Photocopying, colour printing, lamination, and spiral binding. Open early for morning document needs.",
                    "Near Peenya Metro Station, Ground Floor",
                    "Peenya",
                    "+91 98860 90001",
                    "07:30", "21:00",
                    4.4, 72,
                    "🖨️",
                    List.of("Xerox", "Printing", "Lamination", "Binding")
            ),
            new SeedVendor(
                    "v009", "Digital Print House", "Praveen S",
                    "printing",
                    "High-quality digital colour printing, visiting cards, banners, and ID card printing. Bulk orders welcome.",
                    "Nagasandra Main Road, 2nd Floor",
                    "Nagasandra",
                    "+91 95380 45600",
                    "09:00", "19:30",
                    4.5, 41,
                    "🖼️",
                    List.of("Colour Print", "Visiting Cards", "Banners", "Digital")
            ),

            // ── Mobile/Laptop Repair (slug: repair) ──────────────────────────
            new SeedVendor(
                    "v010", "TechFix Solutions", "Arjun Reddy",
                    "repair",
                    "Certified technician for all mobile phones and laptops. Screen replacement, battery, charging port, water damage repair. 3-month warranty on parts.",
                    "Peenya 2nd Stage, Shop 7, Ground Floor",
                    "Peenya",
                    "+91 87940 33211",
                    "10:00", "20:00",
                    4.7, 96,
                    "📱",
                    List.of("Screen Repair", "Battery", "All Brands", "Warranty")
            ),
            new SeedVendor(
                    "v011", "Nagasandra Mobile Care", "Imran Sheikh",
                    "repair",
                    "Quick mobile repair and accessories shop. Software unlocking, data recovery, and protective screen fitting.",
                    "Nagasandra Circle, opp. SBI Bank",
                    "Nagasandra",
                    "+91 96560 78900",
                    "09:30", "21:00",
                    4.2, 58,
                    "🔧",
                    List.of("Software", "Data Recovery", "Accessories")
            ),
            new SeedVendor(
                    "v012", "Laptop Doctor", "Anand Prasad",
                    "repair",
                    "Laptop and desktop specialist. RAM upgrades, SSD installation, OS installation, virus removal. Home service available.",
                    "Peenya Industrial Area, 4th Cross",
                    "Peenya",
                    "+91 99870 12000",
                    "09:00", "19:00",
                    4.6, 67,
                    "💻",
                    List.of("Laptops", "Desktops", "Home Service", "SSD", "OS")
            )
    );

    @Override
    public void run(String... args) {
        log.info("[VendorSeeder] Starting vendor data seeding...");

        // Build category slug → ID map for quick lookup
        Map<String, String> categorySlugToId = categoryRepository.findAll().stream()
                .filter(Category::isActive)
                .collect(Collectors.toMap(Category::getSlug, Category::getId));

        if (categorySlugToId.isEmpty()) {
            log.warn("[VendorSeeder] No active categories found. Skipping vendor seeding.");
            return;
        }

        int skippedUsers = 0;
        int skippedVendors = 0;
        AtomicInteger createdUsers = new AtomicInteger(0);
        AtomicInteger createdVendors = new AtomicInteger(0);

        for (SeedVendor seed : SEED_VENDORS) {
            // 1. Create or find the VENDOR user
            String vendorEmail = seed.id().toLowerCase() + VENDOR_EMAIL_DOMAIN; // e.g., v001@maitri.local
            Optional<User> existingUser = userRepository.findByEmail(vendorEmail);
            User vendorUser = existingUser.orElseGet(() -> {
                User newUser = User.builder()
                        .name(seed.ownerName())
                        .email(vendorEmail)
                        .password(passwordEncoder.encode(defaultVendorPassword))
                        .role(Role.VENDOR)
                        .active(true)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build();
                User saved = userRepository.save(newUser);
                createdUsers.incrementAndGet();
                log.info("[VendorSeeder] Created VENDOR user: email={}, id={}", vendorEmail, saved.getId());
                return saved;
            });

            if (vendorUser == null) {
                skippedUsers++;
                continue;
            }

            // 2. Create or find the vendor profile (idempotent via existsByUserId)
            if (vendorRepository.existsByUserId(vendorUser.getId())) {
                log.debug("[VendorSeeder] Vendor profile already exists for userId={}, skipping", vendorUser.getId());
                skippedVendors++;
                continue;
            }

            // Resolve category ID from slug
            String categoryId = categorySlugToId.get(seed.categorySlug());
            if (categoryId == null) {
                log.warn("[VendorSeeder] Category slug '{}' not found or inactive, skipping vendor: {}",
                        seed.categorySlug(), seed.shopName());
                continue;
            }

            // Build vendor profile
            Vendor vendor = Vendor.builder()
                    .userId(vendorUser.getId()) // Link to the VENDOR user's MongoDB _id
                    .shopName(seed.shopName())
                    .ownerName(seed.ownerName())
                    .categoryId(categoryId) // Store actual MongoDB _id, not slug
                    .description(seed.description())
                    .address(seed.address())
                    .area(seed.area())
                    .phone(seed.phone())
                    .openingTime(seed.openingTime())
                    .closingTime(seed.closingTime())
                    .averageRating(seed.averageRating())
                    .status(VendorStatus.APPROVED) // Immediately visible
                    .createdAt(LocalDateTime.now())
                    .build();

            Vendor saved = vendorRepository.save(vendor);
            createdVendors.incrementAndGet();
            log.info("[VendorSeeder] Created vendor: id={}, shop={}, userId={}, categoryId={}",
                    saved.getId(), saved.getShopName(), saved.getUserId(), saved.getCategoryId());
        }

        log.info("[VendorSeeder] Seeding complete. Users created: {}, Vendors created: {}, Users skipped: {}, Vendors skipped: {}",
                createdUsers.get(), createdVendors.get(), skippedUsers, skippedVendors);
    }

    /**
     * Internal record for vendor seed data.
     * Mirrors the structure of frontend MOCK_VENDORS but with Java-friendly field names.
     */
    private record SeedVendor(
            String id,              // frontend ID (e.g., "v001") — used for email generation
            String shopName,
            String ownerName,
            String categorySlug,    // e.g., "street-food" — resolved to Category._id
            String description,
            String address,
            String area,
            String phone,
            String openingTime,
            String closingTime,
            double averageRating,
            int reviewCount,        // Not stored in Vendor model yet (Phase 7), kept for reference
            String emoji,           // Not stored in Vendor model (frontend only), kept for reference
            List<String> tags       // Not stored in Vendor model yet (Phase 7), kept for reference
    ) {}
}