package com.gole.api.discovery.application.port.out;

import com.gole.api.discovery.domain.model.WishlistEntry;
import com.gole.api.discovery.domain.model.WishlistTargetType;
import java.util.List;

/**
 * Outbound port: 위시리스트 영속성. (요구사항 17.1~17.4)
 */
public interface WishlistRepositoryPort {

    /** 사용자의 동일 대상 위시리스트 항목 존재 여부. */
    boolean exists(String userId, WishlistTargetType type, String targetId);

    /** 위시리스트 항목 저장. */
    void save(WishlistEntry entry);

    /** 위시리스트 항목 삭제(없으면 무시, 멱등). */
    void delete(String userId, WishlistTargetType type, String targetId);

    /** 사용자의 위시리스트 전체. */
    List<WishlistEntry> findByUser(String userId);
}
