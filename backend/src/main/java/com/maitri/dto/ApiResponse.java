package com.maitri.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Standard API Response Wrapper — used by ALL endpoints in Maitri.
 *
 * WHY THIS EXISTS:
 *   Without a wrapper, Spring Boot returns different shapes for success vs error:
 *     Success: { "id": "...", "name": "..." }
 *     Error:   { "timestamp": ..., "status": 400, "error": "..." }
 *
 *   With ApiResponse<T>, EVERY response follows the same structure:
 *     {
 *       "success": true,
 *       "message": "Vendor found.",
 *       "data": { "id": "...", "shopName": "..." },
 *       "timestamp": "2026-08-13T14:30:00"
 *     }
 *
 *   This makes frontend JavaScript much simpler — it always does the same check:
 *     if (response.success) { ... use response.data ... }
 *     else { showError(response.message); }
 *
 * HOW TO USE (in a controller):
 *   return ResponseEntity.ok(ApiResponse.success("User found.", user));
 *   return ResponseEntity.badRequest().body(ApiResponse.error("Invalid input."));
 *
 * @param <T> The type of data being returned (e.g., UserDto, List<VendorDto>)
 *
 * Lombok annotations used:
 *   @Data         — generates getters, setters, toString, equals, hashCode
 *   @Builder      — allows: ApiResponse.builder().success(true).build()
 *   @NoArgsConstructor — generates empty constructor (required for JSON deserialization)
 *   @AllArgsConstructor — generates constructor with all fields
 *
 * @JsonInclude(NON_NULL):
 *   Fields that are null will NOT appear in the JSON output.
 *   Example: if there are no errors, "errors" won't show in the response at all.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    /** true = the operation succeeded. false = it failed. */
    private boolean success;

    /** Human-readable message describing what happened. */
    private String message;

    /** Optional localization message key (e.g. "auth.invalidCredentials"). */
    private String messageKey;

    /** The actual response data (null on errors). */
    private T data;

    /** List of validation error messages (null on success). */
    private List<String> errors;

    /** ISO 8601 timestamp of when this response was generated. */
    private String timestamp;

    // ─── Static Factory Methods ───────────────────────────────────────────────
    // These make it easy to create responses without the verbose builder syntax.

    /**
     * Creates a successful response with data.
     * Example: ApiResponse.success("Vendor found.", vendorDto)
     */
    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .timestamp(currentTimestamp())
                .build();
    }

    /**
     * Creates a successful response with messageKey and data.
     */
    public static <T> ApiResponse<T> success(String message, String messageKey, T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .messageKey(messageKey)
                .data(data)
                .timestamp(currentTimestamp())
                .build();
    }

    /**
     * Creates a successful response with no data (e.g., for DELETE or logout).
     * Example: ApiResponse.success("Logged out successfully.")
     */
    public static <T> ApiResponse<T> success(String message) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .timestamp(currentTimestamp())
                .build();
    }

    /**
     * Creates an error response with a single message.
     * Example: ApiResponse.error("Vendor not found.")
     */
    public static <T> ApiResponse<T> error(String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .timestamp(currentTimestamp())
                .build();
    }

    /**
     * Creates an error response with message and messageKey.
     */
    public static <T> ApiResponse<T> error(String message, String messageKey) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .messageKey(messageKey)
                .timestamp(currentTimestamp())
                .build();
    }

    /**
     * Creates an error response with multiple validation error details.
     * Example: ApiResponse.error("Validation failed.", List.of("Email is required", "Name too short"))
     */
    public static <T> ApiResponse<T> error(String message, List<String> errors) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .errors(errors)
                .timestamp(currentTimestamp())
                .build();
    }

    /**
     * Creates an error response with messageKey and multiple validation error details.
     */
    public static <T> ApiResponse<T> error(String message, String messageKey, List<String> errors) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .messageKey(messageKey)
                .errors(errors)
                .timestamp(currentTimestamp())
                .build();
    }

    /** Returns the current date/time as a formatted string. */
    private static String currentTimestamp() {
        return LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }
}
