package com.gole.api.chat.domain.model;

import java.util.Objects;

/** 문의 콘솔에서 조치하는 운영자. 감사 기록에 남길 이메일을 조치와 함께 넘긴다. */
public record SupportOperator(String id, String email) {

    public SupportOperator {
        Objects.requireNonNull(id, "id");
    }
}
