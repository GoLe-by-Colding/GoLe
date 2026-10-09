package com.gole.api.promotion.application.port.out;

/**
 * 홍보 게시물 검토 조치(승인/반려/발행) 건수 조회 출력 포트.
 *
 * <p>admin 컨텍스트의 감사 로그가 실제 데이터 소스이지만, promotion은 admin의 out-port나
 * 도큐먼트를 직접 참조하지 않는다. 구현 어댑터가 admin이 공개한 인바운드 유스케이스
 * (@code ListAdminActionsUseCase)로 위임한다 — order의
 * {@code adapter/out/listing/ListingReservationAdapter}와 같은 패턴(AGENTS.md 컨텍스트 간 연동).
 */
public interface PromotionAuditMetricsPort {

    long countApprove();

    long countReject();

    long countPublish();
}
