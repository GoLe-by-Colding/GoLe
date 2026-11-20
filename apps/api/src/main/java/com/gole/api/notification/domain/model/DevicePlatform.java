package com.gole.api.notification.domain.model;

import java.util.Arrays;
import java.util.Optional;

/** 푸시 대상 플랫폼. 진단과 플랫폼별 페이로드 분기에 쓴다. */
public enum DevicePlatform {
    IOS,
    ANDROID;

    /** 앱이 보내는 이름(`IOS`·`ANDROID`)과 정확히 같을 때만 읽는다. 그 밖의 값은 비어 있다. */
    public static Optional<DevicePlatform> fromName(String raw) {
        return Arrays.stream(values())
                .filter(platform -> platform.name().equals(raw))
                .findFirst();
    }
}
