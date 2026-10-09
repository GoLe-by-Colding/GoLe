package com.gole.api.promotion.adapter.out.admin;

import com.gole.api.admin.application.port.in.ListAdminActionsUseCase;
import com.gole.api.admin.domain.model.AdminActionType;
import com.gole.api.promotion.application.port.out.PromotionAuditMetricsPort;
import org.springframework.stereotype.Component;

/**
 * {@link PromotionAuditMetricsPort}를 admin의 인바운드 유스케이스({@link ListAdminActionsUseCase})로
 * 위임하는 어댑터. admin의 out-port·영속성 도큐먼트를 직접 참조하지 않는다(AGENTS.md 컨텍스트 간
 * 연동 — order의 {@code ListingReservationAdapter}와 같은 패턴).
 */
@Component
public class PromotionAuditMetricsAdapter implements PromotionAuditMetricsPort {

    private final ListAdminActionsUseCase adminActions;

    public PromotionAuditMetricsAdapter(ListAdminActionsUseCase adminActions) {
        this.adminActions = adminActions;
    }

    @Override
    public long countApprove() {
        return adminActions.countByType(AdminActionType.PROMOTION_POST_APPROVE);
    }

    @Override
    public long countReject() {
        return adminActions.countByType(AdminActionType.PROMOTION_POST_REJECT);
    }

    @Override
    public long countPublish() {
        return adminActions.countByType(AdminActionType.PROMOTION_POST_PUBLISH);
    }
}
