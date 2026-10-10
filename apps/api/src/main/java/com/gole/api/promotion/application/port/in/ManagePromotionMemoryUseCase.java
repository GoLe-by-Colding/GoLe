package com.gole.api.promotion.application.port.in;

import com.gole.api.promotion.domain.model.*;
import java.util.List;

/** 경험 검색, 모델의 지침 제안, 사람의 지침 관리를 담당한다. */
public interface ManagePromotionMemoryUseCase {
    WorkingMemory context(PromotionCategory category, List<String> routes);

    List<PromotionFeedback> listFeedback(String postId, int limit);

    PromotionFeedback getFeedback(String id);

    List<PromotionGuideline> reflect(String actorId, ReflectionCommand command);

    List<PromotionGuideline> listGuidelines(PromotionGuidelineStatus status, int limit);

    PromotionGuideline edit(
            String id, String content, List<PromotionMemoryTarget> targets, List<PromotionCategory> categories);

    PromotionGuideline activate(String id, String actorId);

    PromotionGuideline dismiss(String id);

    PromotionGuideline retire(String id);

    record WorkingMemory(
            List<PromotionFeedback> feedback,
            List<PromotionGuideline> guidelines,
            List<PromotionFeedback> unreflectedFeedback) {}

    record ReflectionCommand(List<String> feedbackIds, String runKey, List<Proposal> proposals) {}

    record Proposal(
            PromotionGuidelineKind kind,
            String content,
            List<PromotionMemoryTarget> targets,
            List<PromotionCategory> categories,
            List<String> sourceFeedbackIds) {}
}
