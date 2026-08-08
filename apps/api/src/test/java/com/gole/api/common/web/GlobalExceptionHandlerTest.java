package com.gole.api.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.gole.api.common.operations.OperationalEventPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
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
}
