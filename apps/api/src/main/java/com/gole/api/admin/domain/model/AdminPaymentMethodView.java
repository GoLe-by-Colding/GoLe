package com.gole.api.admin.domain.model;

/** 읽기 모델의 결제수단. 도메인 열거형 이름을 그대로 옮긴다. */
public record AdminPaymentMethodView(String type, String provider) {}
