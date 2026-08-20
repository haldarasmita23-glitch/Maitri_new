package com.maitri.service;

import com.maitri.dto.favourite.FavouriteCheckResponse;
import com.maitri.dto.favourite.FavouriteRequest;
import com.maitri.dto.favourite.FavouriteResponse;
import com.maitri.dto.vendor.VendorResponse;
import com.maitri.exception.DuplicateFavouriteException;
import com.maitri.exception.FavouriteNotFoundException;
import com.maitri.exception.VendorNotFoundException;
import com.maitri.model.Category;
import com.maitri.model.Favourite;
import com.maitri.model.User;
import com.maitri.model.Vendor;
import com.maitri.model.VendorStatus;
import com.maitri.repository.CategoryRepository;
import com.maitri.repository.FavouriteRepository;
import com.maitri.repository.VendorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Favourite Service — business logic for the Favourites Module (Phase 8).
 *
 * ─── BUSINESS RULES ──────────────────────────────────────────────────────────
 *   1. Only authenticated USERs/ADMINs can manage favourites (enforced by controller)
 *   2. Only APPROVED vendors can be favourited
 *   3. One favourite per user per vendor (unique compound index + service check)
 *   4. All operations are scoped to the authenticated user's userId — a user can
 *      never see or remove another user's favourites
 *   5. Concurrent duplicate inserts are caught via the unique index and
 *      converted to a 409 conflict (DuplicateFavouriteException)
 *
 * ─── METHODS ─────────────────────────────────────────────────────────────────
 *   addFavourite()    — USER: save an APPROVED vendor to the user's favourites
 *   getFavourites()   — USER: list the authenticated user's favourites
 *   removeFavourite() — USER: remove a vendor from the user's favourites
 *   isFavourite()     — USER: check whether the user favourited a vendor
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FavouriteService {

    private final FavouriteRepository favouriteRepository;
    private final VendorRepository vendorRepository;
    private final CategoryRepository categoryRepository;

    /**
     * Adds a vendor to the authenticated user's favourites.
     *
     * @param user    The authenticated user (role enforced by controller)
     * @param request Favourite details (vendorId)
     * @return The created favourite
     * @throws VendorNotFoundException    if vendor doesn't exist or isn't APPROVED
     * @throws DuplicateFavouriteException if the user already favourited this vendor
     */
    public FavouriteResponse addFavourite(User user, FavouriteRequest request) {
        // Verify vendor exists and is approved
        Vendor vendor = vendorRepository.findById(request.getVendorId())
                .orElseThrow(() -> new VendorNotFoundException("Vendor not found."));

        if (vendor.getStatus() != VendorStatus.APPROVED) {
            throw new VendorNotFoundException("Favourites can only be added for approved vendors.");
        }

        // Check for a duplicate favourite
        if (favouriteRepository.existsByUserIdAndVendorId(user.getId(), request.getVendorId())) {
            throw new DuplicateFavouriteException("This vendor is already in your favourites.");
        }

        try {
            Favourite favourite = Favourite.builder()
                    .userId(user.getId())
                    .vendorId(request.getVendorId())
                    .createdAt(LocalDateTime.now())
                    .build();

            Favourite savedFavourite = favouriteRepository.save(favourite);

            log.info("[Favourite] Created: favouriteId={}, userId={}, vendorId={}",
                    savedFavourite.getId(), user.getId(), request.getVendorId());

            return toResponse(savedFavourite, vendor);
        } catch (DuplicateKeyException ex) {
            // Database race: another concurrent request inserted the same favourite first.
            throw new DuplicateFavouriteException("This vendor is already in your favourites.");
        }
    }

    /**
     * Lists the authenticated user's favourites, newest first.
     * Embeds the vendor details using the existing VendorResponse shape.
     *
     * @param user The authenticated user
     * @return List of the user's favourites with vendor details
     */
    public List<FavouriteResponse> getFavourites(User user) {
        List<Favourite> favourites = favouriteRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        Map<String, Category> categoryMap = categoryRepository.findAll().stream()
                .collect(Collectors.toMap(Category::getId, Function.identity()));

        return favourites.stream()
                .map(favourite -> {
                    Vendor vendor = vendorRepository.findById(favourite.getVendorId()).orElse(null);
                    return toResponse(favourite, vendor, categoryMap);
                })
                .collect(Collectors.toList());
    }

    /**
     * Removes a vendor from the authenticated user's favourites.
     * Scoped by userId + vendorId — can only ever remove the user's own favourite.
     *
     * @param user     The authenticated user
     * @param vendorId The vendor to remove from favourites
     * @throws FavouriteNotFoundException if the vendor is not in the user's favourites
     */
    public void removeFavourite(User user, String vendorId) {
        favouriteRepository.findByUserIdAndVendorId(user.getId(), vendorId)
                .orElseThrow(() -> new FavouriteNotFoundException("Vendor is not in your favourites."));

        favouriteRepository.deleteByUserIdAndVendorId(user.getId(), vendorId);

        log.info("[Favourite] Removed: userId={}, vendorId={}", user.getId(), vendorId);
    }

    /**
     * Checks whether the authenticated user has favourited a vendor.
     *
     * @param user     The authenticated user
     * @param vendorId The vendor to check
     * @return Check response containing the favourited boolean
     */
    public FavouriteCheckResponse isFavourite(User user, String vendorId) {
        return FavouriteCheckResponse.builder()
                .favourited(favouriteRepository.existsByUserIdAndVendorId(user.getId(), vendorId))
                .build();
    }

    /**
     * Maps a Favourite entity + Vendor to a FavouriteResponse DTO.
     *
     * @param favourite The favourite entity
     * @param vendor    The favourited vendor
     * @return Safe favourite data projection (never exposes credentials)
     */
    private FavouriteResponse toResponse(Favourite favourite, Vendor vendor) {
        return toResponse(favourite, vendor, categoryRepository.findAll().stream()
                .collect(Collectors.toMap(Category::getId, Function.identity())));
    }

    /**
     * Maps a Favourite entity + Vendor to a FavouriteResponse DTO using a
     * pre-loaded category map (avoids repeated lookups when listing).
     *
     * @param favourite   The favourite entity
     * @param vendor      The favourited vendor
     * @param categoryMap Pre-loaded categories keyed by id
     * @return Safe favourite data projection (never exposes credentials)
     */
    private FavouriteResponse toResponse(Favourite favourite, Vendor vendor, Map<String, Category> categoryMap) {
        VendorResponse vendorResponse = null;
        if (vendor != null) {
            Category category = categoryMap.get(vendor.getCategoryId());
            vendorResponse = VendorResponse.builder()
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

        return FavouriteResponse.builder()
                .id(favourite.getId())
                .userId(favourite.getUserId())
                .vendorId(favourite.getVendorId())
                .vendor(vendorResponse)
                .createdAt(favourite.getCreatedAt())
                .build();
    }
}