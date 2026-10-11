package com.gole.api.parts.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gole.api.account.adapter.in.web.UserAuthInterceptor;
import com.gole.api.common.exception.ConflictException;
import com.gole.api.common.exception.ForbiddenException;
import com.gole.api.common.exception.NotFoundException;
import com.gole.api.common.web.GlobalExceptionHandler;
import com.gole.api.common.web.auth.RequiresOnboarding;
import com.gole.api.parts.application.port.in.ClosePartRequestUseCase;
import com.gole.api.parts.application.port.in.CreatePartRequestUseCase;
import com.gole.api.parts.application.port.in.CreatePartRequestUseCase.CreatePartRequestCommand;
import com.gole.api.parts.application.port.in.CreatePartRequestUseCase.ItemCommand;
import com.gole.api.parts.application.port.in.DeletePartRequestUseCase;
import com.gole.api.parts.application.port.in.GetPartRequestUseCase;
import com.gole.api.parts.application.port.in.ListPartRequestsUseCase;
import com.gole.api.parts.application.port.in.ListPartRequestsUseCase.ListPartRequestsQuery;
import com.gole.api.parts.application.port.in.ListPartRequestsUseCase.StatusFilter;
import com.gole.api.parts.domain.exception.InvalidPartRequestException;
import com.gole.api.parts.domain.exception.PartRequestNotFoundException;
import com.gole.api.parts.domain.exception.PartRequestNotOpenException;
import com.gole.api.parts.domain.model.PartRequest;
import com.gole.api.parts.domain.model.PartRequestStatus;
import com.gole.api.parts.domain.model.WantedPart;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PartRequestControllerTest {

    private static final Instant CREATED = Instant.parse("2026-10-06T01:02:03Z");
    private static final Instant CLOSED = Instant.parse("2026-10-07T01:02:03Z");
    private static final PartRequest OPEN = PartRequest.restore(
            "pr-1",
            "user-1",
            "10305",
            List.of(new WantedPart("3062b", "Black", 4)),
            "급해요",
            PartRequestStatus.OPEN,
            CREATED,
            null);

    private CreatePartRequestUseCase create;
    private ListPartRequestsUseCase list;
    private GetPartRequestUseCase getOne;
    private ClosePartRequestUseCase close;
    private DeletePartRequestUseCase remove;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        create = mock(CreatePartRequestUseCase.class);
        list = mock(ListPartRequestsUseCase.class);
        getOne = mock(GetPartRequestUseCase.class);
        close = mock(ClosePartRequestUseCase.class);
        remove = mock(DeletePartRequestUseCase.class);
        mvc = MockMvcBuilders.standaloneSetup(new PartRequestController(create, list, getOne, close, remove))
                .setControllerAdvice(new GlobalExceptionHandler(event -> {}))
                .build();
    }

    @Test
    @DisplayName("게시는 세션 계정으로 만들고 201과 응답 계약을 돌려준다")
    void create_returns201WithResponseContract() throws Exception {
        when(create.create(any())).thenReturn(OPEN);

        mvc.perform(post("/api/v1/part-requests")
                        .requestAttr(UserAuthInterceptor.ATTR_ACCOUNT_ID, "user-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"setNumber":"10305","note":"급해요",
                                 "items":[{"partNumber":"3062b","colorName":"Black","quantity":4}]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("pr-1"))
                .andExpect(jsonPath("$.requesterId").value("user-1"))
                .andExpect(jsonPath("$.setNumber").value("10305"))
                .andExpect(jsonPath("$.items[0].partNumber").value("3062b"))
                .andExpect(jsonPath("$.items[0].colorName").value("Black"))
                .andExpect(jsonPath("$.items[0].quantity").value(4))
                .andExpect(jsonPath("$.note").value("급해요"))
                .andExpect(jsonPath("$.status").value("open"))
                .andExpect(jsonPath("$.createdAt").value("2026-10-06T01:02:03Z"))
                .andExpect(jsonPath("$.closedAt").isEmpty());

        ArgumentCaptor<CreatePartRequestCommand> command = ArgumentCaptor.forClass(CreatePartRequestCommand.class);
        verify(create).create(command.capture());
        assertThat(command.getValue())
                .isEqualTo(new CreatePartRequestCommand(
                        "user-1", "10305", List.of(new ItemCommand("3062b", "Black", 4)), "급해요"));
    }

    @Test
    @DisplayName("게시는 온보딩을 마친 계정만 — @RequiresOnboarding")
    void create_requiresOnboarding() throws Exception {
        assertThat(PartRequestController.class
                        .getMethod(
                                "create",
                                PartRequestDtos.CreatePartRequestRequest.class,
                                jakarta.servlet.http.HttpServletRequest.class)
                        .isAnnotationPresent(RequiresOnboarding.class))
                .isTrue();
    }

    @Test
    @DisplayName("게시 오류 코드: 400 INVALID, 404 SET_NOT_FOUND, 409 LIMIT_EXCEEDED")
    void create_mapsErrorCodes() throws Exception {
        when(create.create(any()))
                .thenThrow(new InvalidPartRequestException("bad"))
                .thenThrow(new NotFoundException("PART_REQUEST_SET_NOT_FOUND", "no set"))
                .thenThrow(new ConflictException("PART_REQUEST_LIMIT_EXCEEDED", "limit"));

        String body = """
                {"items":[{"partNumber":"3062b","colorName":"Black","quantity":4}]}
                """;
        mvc.perform(post("/api/v1/part-requests")
                        .requestAttr(UserAuthInterceptor.ATTR_ACCOUNT_ID, "user-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PART_REQUEST_INVALID"));
        mvc.perform(post("/api/v1/part-requests")
                        .requestAttr(UserAuthInterceptor.ATTR_ACCOUNT_ID, "user-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PART_REQUEST_SET_NOT_FOUND"));
        mvc.perform(post("/api/v1/part-requests")
                        .requestAttr(UserAuthInterceptor.ATTR_ACCOUNT_ID, "user-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PART_REQUEST_LIMIT_EXCEEDED"));
    }

    @Test
    @DisplayName("공개 게시판 기본값은 status=open, limit=20")
    void list_defaultsToOpenAnd20() throws Exception {
        when(list.list(any())).thenReturn(List.of(OPEN));

        mvc.perform(get("/api/v1/part-requests"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("pr-1"))
                .andExpect(jsonPath("$[0].status").value("open"));

        verify(list).list(new ListPartRequestsQuery(null, StatusFilter.OPEN, 20));
    }

    @Test
    @DisplayName("게시판 필터를 넘기고 limit은 1~50으로 당긴다")
    void list_passesFiltersAndClampsLimit() throws Exception {
        when(list.list(any())).thenReturn(List.of());

        mvc.perform(get("/api/v1/part-requests")
                        .param("setNumber", " 10305 ")
                        .param("status", "closed")
                        .param("limit", "500"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/part-requests").param("status", "ALL").param("limit", "0"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/part-requests").param("limit", "-3")).andExpect(status().isOk());

        verify(list).list(new ListPartRequestsQuery("10305", StatusFilter.CLOSED, 50));
        verify(list).list(new ListPartRequestsQuery(null, StatusFilter.ALL, 1));
        verify(list).list(new ListPartRequestsQuery(null, StatusFilter.OPEN, 1));
    }

    @Test
    @DisplayName("모르는 status나 숫자가 아닌 limit은 500이 아니라 400")
    void list_rejectsUnknownStatusAndMalformedLimit() throws Exception {
        mvc.perform(get("/api/v1/part-requests").param("status", "pending"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PART_REQUEST_INVALID"));
        mvc.perform(get("/api/v1/part-requests").param("limit", "abc")).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("내 요청은 세션 계정으로 조회한다")
    void mine_usesSessionAccount() throws Exception {
        when(list.listMine("user-1")).thenReturn(List.of(OPEN));

        mvc.perform(get("/api/v1/part-requests/mine").requestAttr(UserAuthInterceptor.ATTR_ACCOUNT_ID, "user-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("pr-1"));
    }

    @Test
    @DisplayName("상세는 공개, 없으면 404 PART_REQUEST_NOT_FOUND")
    void get_isPublicAndMapsNotFound() throws Exception {
        when(getOne.get("pr-1")).thenReturn(OPEN);
        when(getOne.get("nope")).thenThrow(new PartRequestNotFoundException("nope"));

        mvc.perform(get("/api/v1/part-requests/pr-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.setNumber").value("10305"));
        mvc.perform(get("/api/v1/part-requests/nope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PART_REQUEST_NOT_FOUND"));
    }

    @Test
    @DisplayName("세트 없는 요청은 setNumber가 null로 나간다")
    void get_withoutSetSerializesNullSetNumber() throws Exception {
        when(getOne.get("pr-2"))
                .thenReturn(PartRequest.restore(
                        "pr-2",
                        "user-1",
                        null,
                        List.of(new WantedPart("3001", "Red", 1)),
                        "",
                        PartRequestStatus.OPEN,
                        CREATED,
                        null));

        mvc.perform(get("/api/v1/part-requests/pr-2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.setNumber").isEmpty())
                .andExpect(jsonPath("$.note").value(""));
    }

    @Test
    @DisplayName("마감은 200과 closed 응답, 남이면 403, 이미 마감이면 409")
    void close_mapsResultAndErrors() throws Exception {
        when(close.close("pr-1", "user-1")).thenReturn(OPEN.close(CLOSED));
        when(close.close("pr-1", "user-2")).thenThrow(new ForbiddenException("PART_REQUEST_ACCESS_DENIED", "denied"));
        when(close.close("pr-9", "user-1")).thenThrow(new PartRequestNotOpenException("pr-9"));

        mvc.perform(post("/api/v1/part-requests/pr-1/close").requestAttr(UserAuthInterceptor.ATTR_ACCOUNT_ID, "user-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("closed"))
                .andExpect(jsonPath("$.closedAt").value("2026-10-07T01:02:03Z"));
        mvc.perform(post("/api/v1/part-requests/pr-1/close").requestAttr(UserAuthInterceptor.ATTR_ACCOUNT_ID, "user-2"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PART_REQUEST_ACCESS_DENIED"));
        mvc.perform(post("/api/v1/part-requests/pr-9/close").requestAttr(UserAuthInterceptor.ATTR_ACCOUNT_ID, "user-1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PART_REQUEST_NOT_OPEN"));
    }

    @Test
    @DisplayName("삭제는 204, 남이면 403")
    void delete_returns204OrForbidden() throws Exception {
        doThrow(new ForbiddenException("PART_REQUEST_ACCESS_DENIED", "denied"))
                .when(remove)
                .delete("pr-1", "user-2");

        mvc.perform(delete("/api/v1/part-requests/pr-1").requestAttr(UserAuthInterceptor.ATTR_ACCOUNT_ID, "user-1"))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/part-requests/pr-1").requestAttr(UserAuthInterceptor.ATTR_ACCOUNT_ID, "user-2"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PART_REQUEST_ACCESS_DENIED"));

        verify(remove).delete(eq("pr-1"), eq("user-1"));
    }
}
