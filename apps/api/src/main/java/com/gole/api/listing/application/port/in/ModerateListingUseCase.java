package com.gole.api.listing.application.port.in;

/**
 * Inbound port: 운영자의 매물 강제 내림. (admin-console 요구사항 4.2, 4.3)
 *
 * <p>셀러 삭제({@link DeleteListingUseCase})는 진행 중 주문이 있으면 거부하지만,
 * 가품·도용 같은 모더레이션은 그 규칙보다 우선한다. 따라서 별도 포트로 분리해
 * "관리자는 상태와 무관하게 내릴 수 있다"는 규칙을 타입으로 드러낸다.
 */
public interface ModerateListingUseCase {

    /** 상태와 무관하게 매물을 내린다. 이미 내려간 매물이면 멱등하게 성공한다. */
    void takedown(String listingId, String reason);
}
