package com.gole.api.discovery.application.port.in;

import java.util.List;

/** 다른 컨텍스트가 셀러의 팔로워에게 활동 소식을 전달할 때 사용하는 조회 포트. */
public interface ListSellerFollowersUseCase {

    List<String> followersOf(String sellerId);
}
