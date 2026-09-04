package com.gole.api.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gole.api.common.exception.ForbiddenException;
import com.gole.api.common.exception.ServiceUnavailableException;
import com.gole.api.common.exception.TooManyRequestsException;
import com.gole.api.common.operations.OperationalEvent;
import com.gole.api.common.operations.OperationalEventPublisher;
import com.gole.api.media.domain.exception.ObjectStorageUnavailableException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.MethodParameter;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

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

    @Test
    void forbiddenSseHandshakeKeepsOriginalStatusWithoutContentNegotiationFailure() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new ForbiddenStreamController())
                .setControllerAdvice(handler)
                .build();

        mvc.perform(get("/stream-test").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(content().string("event: error\ndata: CHAT_ROOM_ACCESS_DENIED\n\n"));
        verify(events, never()).publish(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rateLimitReturnsRetryAfterWithoutOperationalAlert() {
        var response = handler.handleTooManyRequests(new TooManyRequestsException(
                "MEDIA_UPLOAD_RATE_LIMITED", "잠시 후 다시 시도해 주세요", java.time.Duration.ofMillis(1_001)));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("2");
        assertThat(response.getBody().code()).isEqualTo("MEDIA_UPLOAD_RATE_LIMITED");
        verify(events, never()).publish(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void plannedSellerReadinessGateReturns503WithoutOperationalAlert() {
        var response = handler.handleServiceUnavailable(
                new ServiceUnavailableException("SELLER_IDENTITY_VERIFICATION_UNAVAILABLE", "판매자 신원확인 준비 중입니다"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().code()).isEqualTo("SELLER_IDENTITY_VERIFICATION_UNAVAILABLE");
        verify(events, never()).publish(org.mockito.ArgumentMatchers.any());
    }

    @RestController
    private static final class ForbiddenStreamController {

        @GetMapping(value = "/stream-test", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
        SseEmitter stream() {
            throw new ForbiddenException("CHAT_ROOM_ACCESS_DENIED", "참여 중인 채팅방만 볼 수 있습니다");
        }
    }
}
