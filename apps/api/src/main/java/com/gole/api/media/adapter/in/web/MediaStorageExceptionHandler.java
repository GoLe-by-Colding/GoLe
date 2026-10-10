package com.gole.api.media.adapter.in.web;

import com.gole.api.common.operations.OperationalEventPublisher;
import com.gole.api.common.web.DependencyFailures;
import com.gole.api.common.web.ErrorResponse;
import com.gole.api.media.domain.exception.ObjectStorageUnavailableException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 오브젝트 스토리지(S3/MinIO) 연결 장애를 503 으로 바꾼다. 오류 참조값을 운영 알림과 응답에 함께 남긴다.
 *
 * <p>공통 처리기(common)는 컨텍스트 예외를 모르게 두므로 media 가 자기 예외의 응답을 맡는다.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MediaStorageExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(MediaStorageExceptionHandler.class);

    private final OperationalEventPublisher operationalEventPublisher;

    public MediaStorageExceptionHandler(OperationalEventPublisher operationalEventPublisher) {
        this.operationalEventPublisher = operationalEventPublisher;
    }

    @ExceptionHandler(ObjectStorageUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleObjectStorageUnavailable(
            ObjectStorageUnavailableException ex, HttpServletRequest request) {
        String errorReference =
                DependencyFailures.publish(operationalEventPublisher, log, "미디어 저장소 연결 장애", ex, request);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErrorResponse(
                        "MEDIA_STORAGE_UNAVAILABLE", "이미지 저장소 연결이 지연되고 있습니다. 잠시 후 다시 시도해 주세요. 참조: " + errorReference));
    }
}
