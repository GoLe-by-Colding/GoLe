package com.gole.api.promotion.application.service;

import com.gole.api.common.exception.ConflictException;
import com.gole.api.common.exception.NotFoundException;
import com.gole.api.promotion.application.port.in.ManagePromotionMemoryUseCase;
import com.gole.api.promotion.application.port.out.PromotionFeedbackRepositoryPort;
import com.gole.api.promotion.application.port.out.PromotionGuidelineRepositoryPort;
import com.gole.api.promotion.application.port.out.PromotionPostIdGeneratorPort;
import com.gole.api.promotion.domain.model.*;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PromotionMemoryService implements ManagePromotionMemoryUseCase {
    private static final Pattern RUN_KEY = Pattern.compile("[a-z0-9][a-z0-9-]{0,63}");
    private final PromotionFeedbackRepositoryPort feedback;
    private final PromotionGuidelineRepositoryPort guidelines;
    private final PromotionPostIdGeneratorPort ids;
    private final Clock clock;

    public PromotionMemoryService(
            PromotionFeedbackRepositoryPort feedback,
            PromotionGuidelineRepositoryPort guidelines,
            PromotionPostIdGeneratorPort ids,
            Clock clock) {
        this.feedback = feedback;
        this.guidelines = guidelines;
        this.ids = ids;
        this.clock = clock;
    }

    @Override
    public WorkingMemory context(PromotionCategory category, List<String> routes) {
        Objects.requireNonNull(category, "category");
        List<String> resolved = routes == null ? List.of() : List.copyOf(routes);
        if (resolved.size() > 30
                || resolved.stream().anyMatch(route -> !route.startsWith("/") || route.length() > 300)) {
            throw new IllegalArgumentException("invalid memory routes");
        }
        return new WorkingMemory(
                feedback.findRelevant(category, resolved, 3),
                guidelines.findActive(category, 8),
                feedback.findUnreflected(3));
    }

    @Override
    public List<PromotionFeedback> listFeedback(String postId, int limit) {
        return feedback.findRecent(postId, Math.clamp(limit, 1, 100));
    }

    @Override
    @Transactional
    public List<PromotionGuideline> reflect(String actorId, ReflectionCommand command) {
        List<String> feedbackIds = List.copyOf(command.feedbackIds());
        List<Proposal> proposals = List.copyOf(command.proposals());
        if (feedbackIds.isEmpty()
                || feedbackIds.size() > 3
                || proposals.size() > 3
                || new HashSet<>(feedbackIds).size() != feedbackIds.size()
                || feedbackIds.stream().anyMatch(id -> id.isBlank() || id.length() > 80)
                || command.runKey() == null
                || !RUN_KEY.matcher(command.runKey()).matches()) {
            throw new IllegalArgumentException("invalid reflection batch");
        }
        List<PromotionFeedback> sources =
                feedbackIds.stream().map(this::getFeedback).toList();
        List<PromotionFeedback> processed = feedback.findByReflectedRunKey(command.runKey());
        if (sources.stream().allMatch(source -> command.runKey().equals(source.reflectedRunKey()))) {
            if (!new HashSet<>(processed.stream().map(PromotionFeedback::id).toList())
                    .equals(new HashSet<>(feedbackIds))) {
                throw new ConflictException("PROMOTION_REFLECTION_BATCH_MISMATCH", "같은 실행에는 같은 반려 기록 묶음을 보내야 합니다.");
            }
            return guidelines.findByReflectionRunKey(command.runKey());
        }
        if (sources.stream().anyMatch(source -> source.reflectedAt() != null) || !processed.isEmpty()) {
            throw new ConflictException("PROMOTION_FEEDBACK_ALREADY_REFLECTED", "이미 처리한 반려 기록입니다.");
        }
        Instant now = Instant.now(clock);
        // 모든 제안을 검증한 뒤 쓰기 시작한다. 빈 제안도 처리 완료로 남긴다.
        List<PromotionGuideline> created = proposals.stream()
                .map(proposal -> {
                    if (proposal.sourceFeedbackIds() == null
                            || !feedbackIds.containsAll(proposal.sourceFeedbackIds())) {
                        throw new IllegalArgumentException("proposal sources must belong to this reflection batch");
                    }
                    return new PromotionGuideline(
                            ids.newId(),
                            proposal.kind(),
                            proposal.content(),
                            proposal.targets(),
                            proposal.categories(),
                            proposal.sourceFeedbackIds(),
                            PromotionGuidelineStatus.PROPOSED,
                            actorId,
                            now,
                            now,
                            null,
                            null,
                            command.runKey(),
                            0);
                })
                .toList();
        created.forEach(guidelines::insert);
        String ownerId = feedbackIds.stream().sorted().findFirst().orElseThrow();
        sources.forEach(source -> feedback.markReflected(
                source.id(), command.runKey(), now, source.id().equals(ownerId)));
        return created.stream()
                .sorted(java.util.Comparator.comparing(PromotionGuideline::id))
                .toList();
    }

    @Override
    public List<PromotionGuideline> listGuidelines(PromotionGuidelineStatus status, int limit) {
        return guidelines.findRecent(status, Math.clamp(limit, 1, 100));
    }

    @Override
    public PromotionGuideline edit(
            String id,
            String content,
            List<PromotionMemoryTarget> targets,
            List<PromotionCategory> categories,
            long expectedVersion) {
        PromotionGuideline current = getGuideline(id);
        requireVersion(current, expectedVersion);
        return guidelines.saveIfVersion(
                current.edit(content, targets, categories, Instant.now(clock)), current.version());
    }

    @Override
    public PromotionGuideline activate(String id, String actorId, long expectedVersion) {
        PromotionGuideline current = getGuideline(id);
        // 확정 응답만 유실된 같은 확정자의 재시도는 검토한 내용 그대로 반환한다.
        if (expectedVersion >= 0
                && current.status() == PromotionGuidelineStatus.ACTIVE
                && actorId.equals(current.confirmedBy())
                && expectedVersion == current.version() - 1) {
            return current.activate(actorId, Instant.now(clock));
        }
        requireVersion(current, expectedVersion);
        return saveChange(current, current.activate(actorId, Instant.now(clock)));
    }

    @Override
    public PromotionGuideline dismiss(String id) {
        PromotionGuideline current = getGuideline(id);
        return saveChange(current, current.dismiss(Instant.now(clock)));
    }

    @Override
    public PromotionGuideline retire(String id) {
        PromotionGuideline current = getGuideline(id);
        return saveChange(current, current.retire(Instant.now(clock)));
    }

    private PromotionGuideline saveChange(PromotionGuideline current, PromotionGuideline next) {
        return current == next ? current : guidelines.saveIfVersion(next, current.version());
    }

    private static void requireVersion(PromotionGuideline current, long expectedVersion) {
        if (expectedVersion < 0) throw new IllegalArgumentException("expectedVersion must be nonnegative");
        if (current.version() != expectedVersion) {
            throw new ConflictException("PROMOTION_GUIDELINE_VERSION_CONFLICT", "다른 관리자가 변경했습니다. 최신 내용을 확인해주세요.");
        }
    }

    @Override
    public PromotionFeedback getFeedback(String id) {
        return feedback.findById(id)
                .orElseThrow(() -> new NotFoundException("PROMOTION_FEEDBACK_NOT_FOUND", "반려 기록을 찾을 수 없습니다."));
    }

    private PromotionGuideline getGuideline(String id) {
        return guidelines
                .findById(id)
                .orElseThrow(() -> new NotFoundException("PROMOTION_GUIDELINE_NOT_FOUND", "지침을 찾을 수 없습니다."));
    }
}
