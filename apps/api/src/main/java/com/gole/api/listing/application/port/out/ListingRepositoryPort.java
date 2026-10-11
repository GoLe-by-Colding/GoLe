package com.gole.api.listing.application.port.out;

import com.gole.api.listing.application.query.ListingSearchQuery;
import com.gole.api.listing.domain.model.Listing;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 리스팅 영속성 outbound port. 도메인/애플리케이션은 저장 기술(MongoDB 등)에 의존하지 않는다.
 * 검색은 {@link ListingSearchQuery}의 필터/정렬을 그대로 위임받아 활성(ACTIVE) 리스팅만 반환한다.
 */
public interface ListingRepositoryPort {

    /** 리스팅을 저장(신규/갱신)하고 영속된 결과를 반환한다. */
    Listing save(Listing listing);

    /** id로 단건 조회. 없으면 비어있음. */
    Optional<Listing> findById(String listingId);

    /** 검색 조건/정렬에 따라 활성 리스팅을 조회한다. (요구사항 14) */
    List<Listing> search(ListingSearchQuery query);

    /**
     * 활성(ACTIVE) 리스팅을 원자적으로 RESERVED로 전이시키고 그 결과를 반환한다.
     * 활성 상태가 아니면 비어있음(선점 실패).
     */
    Optional<Listing> reserveIfActive(String listingId);

    /** ACTIVE → SOLD 원자 전이. 직거래와 주문 예약이 같은 매물을 동시에 선점하지 못하게 한다. */
    boolean markSoldIfActive(String listingId);

    /**
     * 판매자 수정 결과를 {@code {_id, status: ACTIVE}} 조건으로 원자적으로 반영한다. (E4)
     *
     * <p>수정 가능한 필드(제목·설명·가격·상태·고지·사진·관심 테마)와 가격 이력(직전가·변경 시각)만
     * 쓴다. 상태·판매자·세트·카테고리·정렬 키는 건드리지 않는다. {@link #save}는 문서를 통째로
     * 덮어써서 {@link #reserveIfActive}와 경합하면 RESERVED를 ACTIVE로 되돌릴 수 있다 — 그래서 따로 둔다.
     *
     * @return 반영했으면 true. 그 사이 판매 중이 아니게 됐으면(주문 예약 등) false.
     */
    boolean updateIfActive(Listing listing);

    /**
     * 끌올: {@code {_id, status: ACTIVE}} 조건으로 정렬 키와 끌올 시각만 원자적으로 바꾼다. (B1, B3)
     *
     * <p>{@link #updateIfActive}와 나눈 이유 — 끌올이 읽어 둔 낡은 제목·가격을 함께 쓰면, 그 사이에
     * 들어온 수정을 되돌린다. 끌올은 자기 필드만 쓴다.
     *
     * @return 반영했으면 true. 판매 중이 아니면 false.
     */
    boolean bumpIfActive(String listingId, Instant bumpedAt);

    /** 특정 셀러의 활성 리스팅 목록(최신순 — {@code listedAt} 내림차순). (요구사항 16, B5) */
    List<Listing> findActiveBySeller(String sellerId);

    /** 셀러의 리스팅(최신순 — {@code listedAt} 내림차순, 삭제 제외). 본인 "내 매물" 조회용. (B5) */
    List<Listing> findBySeller(String sellerId);

    /** 여러 셀러의 활성 리스팅 목록(최신순 — {@code listedAt} 내림차순, 피드 구성 등). (요구사항 17, B5) */
    List<Listing> findActiveBySellers(List<String> sellerIds, int limit);

    /** id 목록으로 리스팅을 조회한다(위시리스트 등). */
    List<Listing> findByIds(List<String> ids);
}
