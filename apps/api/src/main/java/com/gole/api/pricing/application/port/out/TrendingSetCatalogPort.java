package com.gole.api.pricing.application.port.out;

/** Outbound port: 인기 세트 목록을 보강할 카탈로그 표시값(세트명·이미지). */
public interface TrendingSetCatalogPort {

    /** 카탈로그에 없거나 조회에 실패하면 예외를 던진다 — 호출자는 세트 번호로 대체한다. */
    SetLabel labelOf(String setNumber);

    record SetLabel(String name, String imageUrl) {}
}
