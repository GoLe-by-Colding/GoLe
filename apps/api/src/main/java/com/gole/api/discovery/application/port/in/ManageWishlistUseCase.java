package com.gole.api.discovery.application.port.in;

import com.gole.api.discovery.domain.model.WishlistEntry;
import com.gole.api.discovery.domain.model.WishlistTargetType;
import java.util.List;

/**
 * Inbound port: 위시리스트 추가/삭제/조회. (요구사항 17.1~17.4)
 */
public interface ManageWishlistUseCase {

    void add(String userId, WishlistTargetType type, String targetId);

    void remove(String userId, WishlistTargetType type, String targetId);

    List<WishlistEntry> list(String userId);
}
