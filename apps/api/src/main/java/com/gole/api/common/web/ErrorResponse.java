package com.gole.api.common.web;

/**
 * 프론트엔드 ApiErrorBody(code, message)와 1:1 대응하는 표준 에러 응답.
 */
public record ErrorResponse(String code, String message) {
}
