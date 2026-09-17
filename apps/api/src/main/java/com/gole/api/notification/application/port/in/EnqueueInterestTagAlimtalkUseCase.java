package com.gole.api.notification.application.port.in;

/** 관심 테마가 지정된 새 매물의 FANOUT 잡을 적재한다. */
public interface EnqueueInterestTagAlimtalkUseCase {

    void enqueue(String sellerId, String listingId, String title, String tagKey, String tagLabel);
}
