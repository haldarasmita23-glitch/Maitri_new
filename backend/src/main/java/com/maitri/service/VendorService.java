package com.maitri.service;

import com.maitri.dto.vendor.VendorApplyRequest;
import com.maitri.dto.vendor.VendorResponse;
import com.maitri.exception.CategoryNotFoundException;
import com.maitri.exception.DuplicateVendorProfileException;
import com.maitri.exception.InvalidCategoryException;
import com.maitri.exception.VendorNotFoundException;
import com.maitri.model.Category;
import com.maitri.model.NotificationType;
import com.maitri.model.Role;
import com.maitri.model.User;
import com.maitri.model.Vendor;
import com.maitri.model.VendorStatus;
import com.maitri.repository.CategoryRepository;
import com.maitri.repository.VendorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Vendor Service — business logic for the Vendor Module (Phase 5).
 *
 * ─── ROLES ──────────────────────────────────────────────────────────────────
 *   apply()              — VENDOR submits a listing → PENDING (409 if one exists)
 *   listApproved()       — PUBLIC: approved vendors, optional slug + search filter
 *   getApprovedById()    — PUBLIC: one approved vendor (others → 404)
 *   getMyProfile()       — VENDOR: own listing
 *   updateMyProfile()    — VENDOR: edit own listing (status/rating never touched)
 *   listPending()        — ADMIN: review queue
 *   approve() / reject() — ADMIN: change status
 *
 * ─── CATEGORY INTEGRATION ────────────────────────────────────────────────────
 *   The frontend uses stable category slugs. Every slug is resolved via
 *   CategoryRepository.findBySlug() to the real categories._id before storage.
 *   Unknown slug → 404. Disabled category → 400.
 *   Phase 4 slugs (street-food, tailors, printing, repair) are preserved.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VendorService {

    private final VendorRepository vendorRepository;
    private final CategoryRepository categoryRepository;
    private final NotificationService notificationService;

    // ─── Apply ────────────────────────────────────────────────────────────────

    /**
     * Creates a PENDING vendor profile for an authenticated VENDOR account.
     *
     * @param user    The authenticated vendor account (role=VENDOR, enforced by controller)
     * @param request Business listing details
     * @return The created profile
     * @throws DuplicateVendorProfileException if the account already has a profile (409)
     * @throws CategoryNotFoundException       if the slug is unknown (404)
     * @throws InvalidCategoryException        if the category is disabled (400)
     */
    public VendorResponse apply(User user, VendorApplyRequest request) {
        if (vendorRepository.existsByUserId(user.getId())) {
            throw new DuplicateVendorProfileException(
                    "A vendor profile already exists for this account."
            );
        }

        Category category = resolveCategory(request.getCategoryId());

        Vendor vendor = Vendor.builder()
                .userId(user.getId())
                .shopName(request.getShopName())
                .ownerName(request.getOwnerName())
                .categoryId(category.getId())
                .description(request.getDescription())
                .address(request.getAddress())
                .area(request.getArea())
                .phone(request.getPhone())
                .openingTime(request.getOpeningTime())
                .closingTime(request.getClosingTime())
                .images(request.safeImages())
                .averageRating(0.0)
                .status(VendorStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        Vendor saved = vendorRepository.save(vendor);
        log.info("[Vendor] Application submitted: id={}, userId={}, shop={}",
                saved.getId(), saved.getUserId(), saved.getShopName());

        // Notify admins about new application awaiting review
        notificationService.notifyAdmins(
                NotificationType.VERIFICATION,
                "New Vendor Application",
                "A new vendor application for '" + saved.getShopName() + "' is awaiting review."
        );

        return toResponse(saved, category);
    }

    // ─── Public browse ────────────────────────────────────────────────────────

    /**
     * Lists APPROVED vendors. Optional category slug filter + case-insensitive
     * search on shop name, description and area. Sorted by shop name A→Z
     * (the frontend re-sorts by rating/reviews/name, preserving existing UX).
     */
    public List<VendorResponse> listApproved(String categorySlug, String query) {
        String categoryId = null;
        if (categorySlug != null && !categorySlug.isBlank()) {
            Category category = categoryRepository.findBySlug(categorySlug).orElse(null);
            if (category == null || !category.isActive()) {
                return List.of();
            }
            categoryId = category.getId();
        }

        List<Vendor> vendors = (categoryId == null)
                ? vendorRepository.findByStatus(VendorStatus.APPROVED)
                : vendorRepository.findByStatusAndCategoryId(VendorStatus.APPROVED, categoryId);

        if (query != null && !query.isBlank()) {
            String q = query.toLowerCase();
            vendors = vendors.stream()
                    .filter(v -> v.getShopName().toLowerCase().contains(q)
                            || (v.getDescription() != null && v.getDescription().toLowerCase().contains(q))
                            || (v.getArea() != null && v.getArea().toLowerCase().contains(q)))
                    .collect(Collectors.toList());
        }

        vendors.sort(Comparator.comparing(Vendor::getShopName, String.CASE_INSENSITIVE_ORDER));
        Map<String, Category> categoryMap = loadCategoryMap();
        return vendors.stream()
                .map(v -> toResponse(v, categoryMap.get(v.getCategoryId())))
                .collect(Collectors.toList());
    }

    /**
     * Returns ONE vendor for public viewing. Only APPROVED vendors are
     * exposed — PENDING/REJECTED return 404 so they can never be sniffed.
     */
    public VendorResponse getApprovedById(String id) {
        Vendor vendor = vendorRepository.findById(id)
                .orElseThrow(() -> new VendorNotFoundException("Vendor not found."));
        if (vendor.getStatus() != VendorStatus.APPROVED) {
            throw new VendorNotFoundException("Vendor not found.");
        }
        Category category = categoryRepository.findById(vendor.getCategoryId()).orElse(null);
        return toResponse(vendor, category);
    }

    // ─── Vendor own profile ───────────────────────────────────────────────────

    /** Returns the authenticated vendor's own listing. */
    public VendorResponse getMyProfile(User user) {
        Vendor vendor = findMyVendor(user);
        Category category = categoryRepository.findById(vendor.getCategoryId()).orElse(null);
        return toResponse(vendor, category);
    }

    /**
     * Updates the authenticated vendor's listing. Approval status and
     * averageRating are deliberately NOT modified — only an ADMIN endpoint
     * can change status.
     */
    public VendorResponse updateMyProfile(User user, VendorApplyRequest request) {
        Vendor vendor = findMyVendor(user);
        Category category = resolveCategory(request.getCategoryId());

        vendor.setShopName(request.getShopName());
        vendor.setOwnerName(request.getOwnerName());
        vendor.setCategoryId(category.getId());
        vendor.setDescription(request.getDescription());
        vendor.setAddress(request.getAddress());
        vendor.setArea(request.getArea());
        vendor.setPhone(request.getPhone());
        vendor.setOpeningTime(request.getOpeningTime());
        vendor.setClosingTime(request.getClosingTime());
        vendor.setImages(request.safeImages());
        // status and averageRating intentionally untouched

        Vendor saved = vendorRepository.save(vendor);
        log.info("[Vendor] Profile updated: id={}, userId={}", saved.getId(), saved.getUserId());
        return toResponse(saved, category);
    }

    // ─── Admin ────────────────────────────────────────────────────────────────

    /** ADMIN: returns the pending review queue, oldest first. */
    public List<VendorResponse> listPending() {
        Map<String, Category> categoryMap = loadCategoryMap();
        return vendorRepository.findByStatusOrderByCreatedAtAsc(VendorStatus.PENDING)
                .stream()
                .map(v -> toResponse(v, categoryMap.get(v.getCategoryId())))
                .collect(Collectors.toList());
    }

    /** ADMIN: approves a vendor → becomes publicly visible. */
    public VendorResponse approve(String id) {
        Vendor vendor = findById(id);
        vendor.setStatus(VendorStatus.APPROVED);
        Vendor saved = vendorRepository.save(vendor);
        log.info("[Vendor] Approved: id={}, shop={}", saved.getId(), saved.getShopName());

        // Phase 10 trigger — notify the vendor's owner account (fail-safe).
        notificationService.notifyUser(
                saved.getUserId(),
                Role.VENDOR,
                NotificationType.VERIFICATION,
                "Vendor Approved",
                "Congratulations! Your business '" + saved.getShopName()
                        + "' has been approved and is now visible on Maitri."
        );

        Category category = categoryRepository.findById(saved.getCategoryId()).orElse(null);
        return toResponse(saved, category);
    }

    /** ADMIN: rejects a vendor → stays hidden. */
    public VendorResponse reject(String id) {
        Vendor vendor = findById(id);
        vendor.setStatus(VendorStatus.REJECTED);
        Vendor saved = vendorRepository.save(vendor);
        log.info("[Vendor] Rejected: id={}, shop={}", saved.getId(), saved.getShopName());

        // Phase 10 trigger — notify the vendor's owner account (fail-safe).
        notificationService.notifyUser(
                saved.getUserId(),
                Role.VENDOR,
                NotificationType.VERIFICATION,
                "Vendor Rejected",
                "Your business '" + saved.getShopName()
                        + "' was not approved at this time. Please contact support for details."
        );

        Category category = categoryRepository.findById(saved.getCategoryId()).orElse(null);
        return toResponse(saved, category);
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    /** Loads the authenticated user's profile or throws 404. */
    private Vendor findMyVendor(User user) {
        return vendorRepository.findByUserId(user.getId())
                .orElseThrow(() -> new VendorNotFoundException(
                        "No vendor profile found for this account."
                ));
    }

    /** Loads a vendor by id or throws 404. */
    private Vendor findById(String id) {
        return vendorRepository.findById(id)
                .orElseThrow(() -> new VendorNotFoundException("Vendor not found."));
    }

    /** Resolves a category slug to an active category document. */
    private Category resolveCategory(String slug) {
        Category category = categoryRepository.findBySlug(slug)
                .orElseThrow(() -> new CategoryNotFoundException("Category not found."));
        if (!category.isActive()) {
            throw new InvalidCategoryException(
                    "This category is disabled and cannot be used."
            );
        }
        return category;
    }

    /** Loads all categories into a map for efficient name/slug resolution. */
    private Map<String, Category> loadCategoryMap() {
        return categoryRepository.findAll().stream()
                .collect(Collectors.toMap(Category::getId, Function.identity()));
    }

    /** Maps a Vendor document + its Category to the API response shape. */
    private VendorResponse toResponse(Vendor vendor, Category category) {
        return VendorResponse.builder()
                .id(vendor.getId())
                .userId(vendor.getUserId())
                .shopName(vendor.getShopName())
                .ownerName(vendor.getOwnerName())
                .categoryId(vendor.getCategoryId())
                .categorySlug(category != null ? category.getSlug() : null)
                .categoryName(category != null ? category.getCategoryName() : null)
                .description(vendor.getDescription())
                .address(vendor.getAddress())
                .area(vendor.getArea())
                .phone(vendor.getPhone())
                .openingTime(vendor.getOpeningTime())
                .closingTime(vendor.getClosingTime())
                .images(vendor.getImages() == null ? new ArrayList<>() : vendor.getImages())
                .averageRating(vendor.getAverageRating())
                .status(vendor.getStatus())
                .createdAt(vendor.getCreatedAt())
                .build();
    }
}