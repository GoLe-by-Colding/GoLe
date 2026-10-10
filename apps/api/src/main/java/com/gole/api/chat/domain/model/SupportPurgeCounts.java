package com.gole.api.chat.domain.model;

public record SupportPurgeCounts(
        long messages,
        long supportTickets,
        long socialRooms,
        long assistantAnalyses,
        long internalNotes,
        long readCursors,
        long retentionHolds,
        long auditReferencesAnonymized) {}
