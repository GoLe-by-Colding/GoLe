package com.gole.api.media.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gole.api.common.operations.OperationalEvent;
import com.gole.api.common.operations.OperationalEventPublisher;
import com.gole.api.media.domain.exception.ObjectStorageUnavailableException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;

class MediaStorageExceptionHandlerTest {

    private final OperationalEventPublisher events = mock(OperationalEventPublisher.class);
    private final MediaStorageExceptionHandler handler = new MediaStorageExceptionHandler(events);

    @Test
    @DisplayName("오브젝트 스토리지 장애는 503 이고 오류 참조를 운영 알림과 응답에 남긴다")
    void unavailableObjectStorageIsAServiceDependencyFailure() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/v1/media/images/example.png");

        var response = handler.handleObjectStorageUnavailable(
                new ObjectStorageUnavailableException(new IllegalStateException("offline")), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().code()).isEqualTo("MEDIA_STORAGE_UNAVAILABLE");
        assertThat(response.getBody().message()).contains("참조:");
        ArgumentCaptor<OperationalEvent> event = ArgumentCaptor.forClass(OperationalEvent.class);
        verify(events).publish(event.capture());
        assertThat(event.getValue().fields()).containsKeys("오류 참조", "요청 경로", "예외 종류");
    }

    @Test
    @DisplayName("공통 처리기의 catch-all 보다 먼저 고르도록 우선순위가 가장 높다")
    void handlerRunsBeforeTheGlobalCatchAll() {
        assertThat(MediaStorageExceptionHandler.class.getAnnotation(Order.class).value())
                .isEqualTo(Ordered.HIGHEST_PRECEDENCE);
    }
}
