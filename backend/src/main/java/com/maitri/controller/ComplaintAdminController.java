package com.maitri.controller;

import com.maitri.dto.ApiResponse;
import com.maitri.dto.complaint.ComplaintNoteRequest;
import com.maitri.dto.complaint.ComplaintResponse;
import com.maitri.model.ComplaintStatus;
import com.maitri.service.ComplaintService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Complaint Admin Controller — Phase 9 (Complaints Module) — ADMIN routes.
 *
 * ─── ENDPOINTS & ACCESS ──────────────────────────────────────────────────────
 *   GET    /api/complaints/admin             — ADMIN: all complaints (optional status filter)
 *   PATCH  /api/complaints/{id}/status       — ADMIN: any status transition
 *   PATCH  /api/complaints/{id}/note         — ADMIN: set/update internal adminNote
 *
 * ─── AUTHORIZATION MATRIX ────────────────────────────────────────────────────
 *   | Endpoint                         | Anonymous | USER | VENDOR | ADMIN |
 *   |----------------------------------|-----------|------|--------|-------|
 *   | GET /api/complaints/admin        | 401       | 403  | 403    | ✅    |
 *   | PATCH /api/complaints/{id}/status | 401      | 403  | 403    | ✅    |
 *   | PATCH /api/complaints/{id}/note  | 401       | 403  | 403    | ✅    |
 *
 * Spring MVC resolves the literal "admin" path ahead of the /{id} path variable
 * (same mechanism as /api/reviews/my), so this controller does not conflict
 * with ComplaintController's user routes.
 *
 * The adminNote is an INTERNAL admin comment: it is only returned in responses
 * built for ADMIN callers (ComplaintService passes includeAdminNote=true for
 * every admin-facing read/update).
 */
@RestController
@RequestMapping("/api/complaints")
@RequiredArgsConstructor
public class ComplaintAdminController {

    private final ComplaintService complaintService;

    /** ADMIN only — lists all complaints, optionally filtered by status. */
    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<ComplaintResponse>>> getAll(
            @RequestParam(required = false) String status) {
        ComplaintStatus filter = null;
        if (status != null && !status.isBlank()) {
            filter = ComplaintStatus.valueOf(status.toUpperCase());
        }
        return ResponseEntity.ok(
                ApiResponse.success("All complaints retrieved.",
                        complaintService.adminGetAll(filter))
        );
    }

    /**
     * ADMIN only — sets/updates the internal adminNote on a complaint.
     *
     * NOTE: The shared PATCH /api/complaints/{id}/status endpoint lives in
     * ComplaintController, where the service branches by the authenticated
     * role (VENDOR receives vendor-scoped transitions, ADMIN receives an
     * administrative override). This controller only hosts admin-specific
     * routes that VENDORs/USERs must never reach.
     */
    @PatchMapping("/{id}/note")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ComplaintResponse>> updateNote(
            @PathVariable String id,
            @Valid @RequestBody ComplaintNoteRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success("Complaint note updated.",
                        complaintService.adminUpdateNote(id, request))
        );
    }
}