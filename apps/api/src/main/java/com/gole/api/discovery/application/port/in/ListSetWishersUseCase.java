package com.gole.api.discovery.application.port.in;

import java.util.List;

/** 다른 컨텍스트가 카탈로그 세트를 위시리스트에 담은 사용자에게 소식을 전달할 때 쓰는 조회 포트. */
public interface ListSetWishersUseCase {

    List<String> wishersOf(String setNumber);
}
