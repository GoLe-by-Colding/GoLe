package com.gole.api.discovery.application.port.out;

import com.gole.api.discovery.domain.model.Follow;
import java.util.List;

/**
 * Outbound port: 팔로우 관계 영속성. (요구사항 16.3~16.7)
 */
public interface FollowRepositoryPort {

    /** 사용자 → 셀러 팔로우 관계 존재 여부. */
    boolean exists(String userId, String sellerId);

    /** 팔로우 관계 저장. */
    void save(Follow follow);

    /** 팔로우 관계 삭제(없으면 무시, 멱등). */
    void delete(String userId, String sellerId);

    /** 사용자가 팔로우한 셀러 id 목록. */
    List<String> findSellerIdsByUser(String userId);
}
