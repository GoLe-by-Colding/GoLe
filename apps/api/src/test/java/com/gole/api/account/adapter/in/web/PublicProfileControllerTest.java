package com.gole.api.account.adapter.in.web;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gole.api.account.application.port.in.GetPublicProfilesUseCase;
import com.gole.api.account.application.port.in.GetPublicProfilesUseCase.PublicProfile;
import com.gole.api.common.exception.BadRequestException;
import com.gole.api.common.operations.OperationalEventPublisher;
import com.gole.api.common.web.GlobalExceptionHandler;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PublicProfileControllerTest {

    private GetPublicProfilesUseCase publicProfiles;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        publicProfiles = mock(GetPublicProfilesUseCase.class);
        mvc = MockMvcBuilders.standaloneSetup(new PublicProfileController(publicProfiles))
                .setControllerAdvice(new GlobalExceptionHandler(mock(OperationalEventPublisher.class)))
                .build();
    }

    @Test
    @DisplayName("로그인 없이 쉼표로 이은 ID 의 닉네임만 돌려주고 1분 캐시를 허용한다")
    void publicProfiles_readsCommaSeparatedIdsWithoutSession() throws Exception {
        when(publicProfiles.publicProfiles(List.of("a", "b")))
                .thenReturn(List.of(new PublicProfile("a", "브릭고래"), new PublicProfile("b", null)));

        mvc.perform(get("/api/v1/accounts/public-profiles").param("ids", "a,b"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "max-age=60"))
                .andExpect(jsonPath("$[0].accountId").value("a"))
                .andExpect(jsonPath("$[0].nickname").value("브릭고래"))
                .andExpect(jsonPath("$[1].accountId").value("b"))
                .andExpect(jsonPath("$[1].nickname").doesNotExist())
                .andExpect(jsonPath("$[0].email").doesNotExist())
                .andExpect(jsonPath("$[0].role").doesNotExist());
    }

    @Test
    @DisplayName("반복한 ids 파라미터도 같은 목록으로 받는다")
    void publicProfiles_readsRepeatedIds() throws Exception {
        when(publicProfiles.publicProfiles(List.of("a", "b"))).thenReturn(List.of());

        mvc.perform(get("/api/v1/accounts/public-profiles").param("ids", "a").param("ids", "b"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @DisplayName("ID 가 너무 많으면 400 과 오류 코드를 돌려준다")
    void publicProfiles_mapsTooManyIdsToBadRequest() throws Exception {
        when(publicProfiles.publicProfiles(List.of("a")))
                .thenThrow(new BadRequestException("TOO_MANY_ACCOUNT_IDS", "한 번에 50명까지 조회할 수 있습니다"));

        mvc.perform(get("/api/v1/accounts/public-profiles").param("ids", "a"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TOO_MANY_ACCOUNT_IDS"));
    }
}
