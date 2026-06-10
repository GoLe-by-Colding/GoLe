package com.gole.api.admin.adapter.in.web;

import com.gole.api.catalog.domain.model.LegoSet;
import com.gole.api.catalog.domain.model.RetirementStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import org.bson.Document;

public final class AdminDtos {

    private AdminDtos() {}

    public record OverviewResponse(
            Map<String, Long> counts, long gmv, Map<String, Long> ordersByStatus, long activeListings) {}

    public record OrderRow(
            String id,
            String status,
            long amount,
            String buyerId,
            String sellerId,
            String catalogSetNumber,
            Instant createdAt) {

        public static OrderRow from(Document d) {
            return new OrderRow(
                    d.getString("_id"),
                    d.getString("status"),
                    num(d.get("amount")),
                    d.getString("buyerId"),
                    d.getString("sellerId"),
                    d.getString("catalogSetNumber"),
                    instant(d.get("createdAt")));
        }
    }

    public record ListingRow(
            String id, String title, String sellerId, long price, String status, String category, Instant createdAt) {

        public static ListingRow from(Document d) {
            return new ListingRow(
                    d.getString("_id"),
                    d.getString("title"),
                    d.getString("sellerId"),
                    num(d.get("priceAmount")),
                    d.getString("status"),
                    d.getString("category"),
                    instant(d.get("createdAt")));
        }
    }

    private static long num(Object v) {
        return v instanceof Number n ? n.longValue() : 0L;
    }

    private static Instant instant(Object v) {
        return v instanceof Date date ? date.toInstant() : null;
    }

    public record PostRow(String id, String authorId, String content, String type, String status, Instant createdAt) {

        public static PostRow from(Document d) {
            String raw = d.getString("content");
            return new PostRow(
                    d.getString("_id"),
                    d.getString("authorId"),
                    raw != null && raw.length() > 80 ? raw.substring(0, 80) + "…" : raw,
                    d.getString("type"),
                    d.getString("status"),
                    instant(d.get("createdAt")));
        }
    }

    public record AccountRow(String id, String email, String role, String status, Instant lockedUntil) {

        public static AccountRow from(Document d) {
            Object emailObj = d.get("email");
            // email 필드는 {address, ...} 임베디드 문서로 저장된다.
            String emailStr = emailObj instanceof Document emailDoc ? emailDoc.getString("address") : "";
            return new AccountRow(
                    d.getString("_id"),
                    emailStr,
                    d.getString("role"),
                    d.getString("status"),
                    instant(d.get("lockedUntil")));
        }
    }

    public record CreateSetRequest(
            @NotBlank String setNumber,
            @NotBlank String name,
            @NotBlank String theme,
            @Min(0) int pieceCount,
            int releaseYear,
            @NotNull RetirementStatus retirementStatus,
            String imageUrl,
            boolean featured) {}

    public record LegoSetResponse(
            String setNumber,
            String name,
            String theme,
            int pieceCount,
            int releaseYear,
            String retirementStatus,
            String imageUrl) {

        public static LegoSetResponse from(LegoSet s) {
            return new LegoSetResponse(
                    s.getSetNumber(),
                    s.getName(),
                    s.getTheme(),
                    s.getPieceCount(),
                    s.getReleaseYear(),
                    s.getRetirementStatus().name(),
                    s.getImageUrl());
        }
    }
}
