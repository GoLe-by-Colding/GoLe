package com.gole.api.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.gole.api.common.operations.OperationalEvent;
import com.gole.api.common.operations.OperationalEventPublisher;
import com.gole.api.media.domain.exception.ObjectStorageUnavailableException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.MethodParameter;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

class GlobalExceptionHandlerTest {

    private final OperationalEventPublisher events = mock(OperationalEventPublisher.class);
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(events);

    @Test
    void malformedJsonIsAClientErrorWithoutOperationalAlert() {
        var response = handler.handleUnreadableBody(
                new HttpMessageNotReadableException("broken", new MockHttpInputMessage(new byte[0])));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("MALFORMED_JSON");
        verify(events, never()).publish(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void invalidEnumQueryIsAClientErrorWithoutOperationalAlert() {
        var exception = new MethodArgumentTypeMismatchException(
                "NOT_A_STATUS", Enum.class, "status", mock(MethodParameter.class), new IllegalArgumentException());

        var response = handler.handleTypeMismatch(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("INVALID_PARAMETER");
        verify(events, never()).publish(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void unavailableObjectStorageIsAServiceDependencyFailure() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        org.mockito.Mockito.when(request.getRequestURI()).thenReturn("/api/v1/media/images/example.png");

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
    void unavailableRedisIsAServiceDependencyFailure() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        org.mockito.Mockito.when(request.getRequestURI()).thenReturn("/api/v1/chat/rooms");

        var response =
                handler.handleRedisUnavailable(new RedisConnectionFailureException("connection refused"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().code()).isEqualTo("CACHE_UNAVAILABLE");
        assertThat(response.getBody().message()).contains("참조:");
        ArgumentCaptor<OperationalEvent> event = ArgumentCaptor.forClass(OperationalEvent.class);
        verify(events).publish(event.capture());
        assertThat(event.getValue().title()).isEqualTo("캐시 서버 연결 장애");
        assertThat(event.getValue().fields()).containsKeys("오류 참조", "요청 경로", "예외 종류");
    }
}
