package com.gole.api.parts.application.port.out;

/** 카탈로그 세트 존재 확인 outbound port. catalog 컨텍스트의 인바운드 포트로 구현한다. (W2) */
public interface PartsCatalogPort {

    boolean setExists(String setNumber);
}
