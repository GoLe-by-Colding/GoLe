package com.gole.api.media.application.port.out;

import com.gole.api.media.application.port.out.ObjectStoragePort.StoredObject;
import java.util.Optional;

/** 배포 산출물에 포함된 기본 카탈로그·커뮤니티 이미지를 읽는 포트. */
public interface BundledMediaPort {

    Optional<StoredObject> get(String key);
}
