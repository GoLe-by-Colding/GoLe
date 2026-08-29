package com.gole.api.order.application.port.in;

import com.gole.api.order.application.port.in.ManageSettlementsUseCase.SettlementStatus;
import java.time.Instant;
import java.util.List;

/**
 * 판매자가 자기 정산 원장을 조회한다.
 *
 * <p>{@link ManageSettlementsUseCase}와 분리한 이유: 그쪽은 전체 원장 조회와 지급 확인이라
 * 관리자 전용이다. 판매자에게는 <b>자기 것만</b> 보여야 하고 지급 표시 권한도 없다.
 * 같은 인터페이스에 두면 호출부에서 권한 경계가 흐려진다.
 */
public interface GetSellerSettlementsUseCase {

    List<SellerSettlementSummary> listBySeller(String sellerId, int limit);

    /**
     * 판매자 공개용 원장. 지급사 응답·운영자 메모·내부 시도 횟수·증빙 번호는 고의로 포함하지
     * 않는다. 관리 DTO를 재사용하면 운영 재조정 사유와 외부 오류가 계정 경계 밖으로 샌다.
     */
    record SellerSettlementSummary(
            String orderId,
            long grossAmount,
            long fee,
            long payout,
            double feeRate,
            SettlementStatus status,
            Instant createdAt,
            Instant payableAt,
            Instant paidAt,
            Instant payoutNextAttemptAt) {}
}
