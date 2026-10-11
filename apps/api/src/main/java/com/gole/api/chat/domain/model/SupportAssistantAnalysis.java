package com.gole.api.chat.domain.model;

import java.util.List;

public record SupportAssistantAnalysis(
        SupportCategory recommendedCategory,
        SupportAssistantPriority priority,
        String summary,
        String draftReply,
        List<String> riskFlags,
        boolean humanReviewRequired,
        boolean externalModelUsed,
        String engineVersion) {

    public SupportAssistantAnalysis {
        riskFlags = List.copyOf(riskFlags);
    }
}
