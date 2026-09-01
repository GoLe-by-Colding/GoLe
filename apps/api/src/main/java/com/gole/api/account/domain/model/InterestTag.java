package com.gole.api.account.domain.model;

/**
 * 관심 태그 하나. (onboarding D8)
 *
 * <p>계정에 저장되는 것은 <b>{@code key}</b>다. 표시 문구({@code label})를 저장하면 "아이콘"을
 * "아이콘즈"로 고치는 순간 이미 저장된 사용자 선택을 전부 마이그레이션해야 하고, 다국어를
 * 붙일 때도 같은 문제가 반복된다.
 *
 * @param key 영속화·검증에 쓰는 안정 식별자
 * @param label 화면에 그대로 노출하는 한국어 표기
 */
public record InterestTag(String key, String label) {}
