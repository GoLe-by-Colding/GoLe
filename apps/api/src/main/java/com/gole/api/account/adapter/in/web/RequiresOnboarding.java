package com.gole.api.account.adapter.in.web;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 온보딩을 마친 계정만 호출할 수 있는 거래성 액션 표시. (onboarding D5, R9)
 *
 * <p>둘러보기(홈·매물조회·시세)는 온보딩과 무관하게 항상 열어 둔다. 이 애노테이션은
 * 매물 등록·주문 생성·채팅 대화 시작 세 곳에만 붙는다.
 *
 * <p>이 판정을 <b>서버가</b> 하는 이유 — 이 저장소는 클라이언트만 믿고 게이트를 걸었다가
 * 실제 콘솔 우회 사고를 낸 전례가 있다(2026-08-29 어드민 클라이언트 게이트 우회).
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresOnboarding {}
