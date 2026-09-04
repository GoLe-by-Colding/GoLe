package com.gole.api.chat.application.port.out;

import com.gole.api.chat.domain.model.SupportCategory;
import java.util.List;
import java.util.Optional;

/** 문의 원문을 외부 모델에 직접 결합하지 않기 위한 내부 AI 분석 경계. */
public interface SupportAssistantPort {

    Optional<Analysis> analyze(Request request);

    record Request(String ticketId, SupportCategory declaredCategory, String title, String message, String locale) {}

    record Analysis(
            SupportCategory recommendedCategory,
            Priority priority,
            String summary,
            String draftReply,
            List<String> riskFlags,
            boolean humanReviewRequired,
            boolean externalModelUsed,
            String engineVersion) {

        public Analysis {
            riskFlags = List.copyOf(riskFlags);
        }
    }

    enum Priority {
        LOW,
        NORMAL,
        HIGH,
        URGENT
    }
}
