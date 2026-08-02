package com.restaurant.ordering.table.api;

import java.time.Instant;
import java.util.UUID;

import com.restaurant.ordering.events.TableState;
import com.restaurant.ordering.table.domain.RestaurantTable;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Request and response shapes for the table API. */
public final class TableDtos {

    private TableDtos() {
    }

    /** Body of the QR scan. The value comes straight off the printed sticker. */
    public record ScanRequest(@NotBlank String qrCode) {
    }

    /**
     * What the customer's phone gets back after scanning.
     *
     * @param token      the session JWT; every later customer call carries it
     * @param expiresAt  so the UI can re-scan before the meal outlasts the token
     */
    public record SessionResponse(
            String token,
            Long tableId,
            String tableCode,
            UUID sessionId,
            Instant expiresAt) {
    }

    /** Table as shown on the staff dashboard. Never exposes {@code qrCode}. */
    public record TableView(
            Long id,
            String code,
            int seats,
            TableState state,
            boolean attentionFlagged,
            String attentionNote,
            Instant updatedAt) {

        public static TableView of(RestaurantTable table) {
            return new TableView(
                    table.getId(),
                    table.getCode(),
                    table.getSeats(),
                    table.getState(),
                    table.isAttentionFlagged(),
                    table.getAttentionNote(),
                    table.getUpdatedAt());
        }
    }

    public record AttentionRequest(
            boolean flagged,
            @Size(max = 255) String note) {
    }

    public record StateRequest(@NotNull TableState state) {
    }
}
