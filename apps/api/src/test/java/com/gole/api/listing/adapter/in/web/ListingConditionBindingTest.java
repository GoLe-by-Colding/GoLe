package com.gole.api.listing.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.gole.api.common.web.GlobalExceptionHandler;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.format.support.FormattingConversionService;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * `?condition=` 파라미터 바인딩 계약.
 *
 * <p>{@link ListingWebConfig} 컨버터만이 아니라 <b>예외 핸들러까지 함께</b> 세운다. 둘을 떼어
 * 놓으면 의미가 없기 때문이다 — 컨버터는 모르는 키를 거부하지만, 그 거부가 몇 번대 응답이
 * 되는지는 {@link GlobalExceptionHandler}가 정한다. 실제로 catch-all
 * {@code @ExceptionHandler(Exception.class)}가 Spring 기본 400 매핑보다 먼저 잡는 탓에
 * 한동안 오타 하나가 500 + ERROR 등급 운영 이벤트로 나갔다.
 */
class ListingConditionBindingTest {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        FormattingConversionService conversionService = new FormattingConversionService();
        new ListingWebConfig().addFormatters(conversionService);

        // 검색 use case는 빈 목록만 돌려주면 된다. 여기서 보는 것은 바인딩 결과지 검색 결과가 아니다.
        ListingController controller = new ListingController(null, null, query -> List.of(), null, null, null);

        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler(event -> {}))
                .setConversionService(conversionService)
                .build();
    }

    private int statusFor(String condition) throws Exception {
        return mvc.perform(get("/api/v1/listings").param("condition", condition))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    @Test
    void acceptsLowercaseKeysAsDocumentedInTheOpenApiContract() throws Exception {
        // OpenAPI 설명이 소문자 키를 규약으로 못박고 있다. 기본 컨버터는 Enum.valueOf라 이게 안 된다.
        assertThat(statusFor("new_sealed")).isEqualTo(200);
        assertThat(statusFor("like_new")).isEqualTo(200);
        assertThat(statusFor("damaged")).isEqualTo(200);
    }

    @Test
    void acceptsEnumNamesSoExistingCallersKeepWorking() throws Exception {
        // 프론트는 값을 대문자로 올려 보낸다(listing-api.ts). 그 경로가 깨지면 안 된다.
        assertThat(statusFor("NEW_SEALED")).isEqualTo(200);
        assertThat(statusFor("USED_GOOD")).isEqualTo(200);
    }

    @Test
    void acceptsLegacyThreeGradeValuesSoOldLinksKeepWorking() throws Exception {
        assertThat(statusFor("used_complete")).isEqualTo(200);
        assertThat(statusFor("USED_INCOMPLETE")).isEqualTo(200);
    }

    @Test
    void treatsBlankAsNoFilterRatherThanAnError() throws Exception {
        assertThat(statusFor("")).isEqualTo(200);
    }

    @Test
    void rejectsUnknownValueWith400NotWith500() throws Exception {
        // 400이어야 하는 이유는 두 가지다. (1) 서버 잘못이 아니라 요청 잘못이다.
        // (2) 500이면 handleUnexpected가 ERROR 운영 이벤트를 발행해, 쿼리스트링을 훑는 봇
        //     하나로 운영 알림 채널이 막힌다.
        assertThat(statusFor("nonsense")).isEqualTo(400);
    }

    @Test
    void unknownValueErrorBodyNamesTheOffendingParameter() throws Exception {
        String body = mvc.perform(get("/api/v1/listings").param("condition", "nonsense"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(body).contains("INVALID_PARAMETER").contains("condition");
    }
}
