package com.gole.api.common.web;

import com.gole.api.common.exception.BadRequestException;
import com.gole.api.common.exception.ConflictException;
import com.gole.api.common.exception.DomainException;
import com.gole.api.common.exception.ForbiddenException;
import com.gole.api.common.exception.NotFoundException;
import com.gole.api.common.exception.UnauthorizedException;
import com.gole.api.common.operations.OperationalEvent;
import com.gole.api.common.operations.OperationalEvent.Category;
import com.gole.api.common.operations.OperationalEvent.Level;
import com.gole.api.common.operations.OperationalEventPublisher;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * inbound 웹 어댑터 공통 예외 변환. 도메인 예외를 HTTP 상태/표준 바디로 매핑한다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final OperationalEventPublisher operationalEventPublisher;

    public GlobalExceptionHandler(OperationalEventPublisher operationalEventPublisher) {
        this.operationalEventPublisher = operationalEventPublisher;
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(BadRequestException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(ex.getCode(), ex.getMessage()));
    }

    // 요구사항 13.7: 동시 갱신 충돌(낙관적 락) → 409
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(OptimisticLockingFailureException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("CONCURRENT_UPDATE_CONFLICT", "Resource was updated concurrently"));
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(UnauthorizedException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(ForbiddenException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErrorResponse(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ErrorResponse> handleDomain(DomainException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
                .body(new ErrorResponse(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .orElse("Validation failed");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse("VALIDATION_ERROR", message));
    }

    /** 예상하지 못한 500 오류는 사용자에게 내부 정보를 숨기고 운영 채널에는 추적 ID를 남긴다. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        String errorReference = UUID.randomUUID().toString();
        log.error("[{}] 처리되지 않은 오류 path={}", errorReference, request.getRequestURI(), ex);
        operationalEventPublisher.publish(new OperationalEvent(
                Category.APPLICATION,
                Level.ERROR,
                "처리되지 않은 API 오류",
                "서버에서 예상하지 못한 예외가 발생했습니다. 로그의 오류 참조값으로 추적하세요.",
                Map.of(
                        "오류 참조", errorReference,
                        "요청 경로", request.getRequestURI(),
                        "예외 종류", ex.getClass().getSimpleName()),
                Instant.now()));
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("INTERNAL_SERVER_ERROR", "일시적인 오류가 발생했습니다. 참조: " + errorReference));
    }
}
