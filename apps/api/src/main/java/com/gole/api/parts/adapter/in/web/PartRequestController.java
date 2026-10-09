package com.gole.api.parts.adapter.in.web;

import com.gole.api.account.adapter.in.web.AuthenticatedUser;
import com.gole.api.account.adapter.in.web.RequiresOnboarding;
import com.gole.api.parts.adapter.in.web.PartRequestDtos.CreatePartRequestRequest;
import com.gole.api.parts.adapter.in.web.PartRequestDtos.PartRequestResponse;
import com.gole.api.parts.application.port.in.ClosePartRequestUseCase;
import com.gole.api.parts.application.port.in.CreatePartRequestUseCase;
import com.gole.api.parts.application.port.in.CreatePartRequestUseCase.CreatePartRequestCommand;
import com.gole.api.parts.application.port.in.CreatePartRequestUseCase.ItemCommand;
import com.gole.api.parts.application.port.in.DeletePartRequestUseCase;
import com.gole.api.parts.application.port.in.GetPartRequestUseCase;
import com.gole.api.parts.application.port.in.ListPartRequestsUseCase;
import com.gole.api.parts.application.port.in.ListPartRequestsUseCase.ListPartRequestsQuery;
import com.gole.api.parts.application.port.in.ListPartRequestsUseCase.StatusFilter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inbound 어댑터(REST): 부족 부품 요청. use case 인터페이스에만 의존한다. (wanted-parts W2~W7)
 *
 * <p>게시판·단건 조회는 공개다. 세션 계정은 쓰기와 {@code /mine}에서만 읽는다 — 그 둘만
 * {@code UserAuthInterceptor}가 세션을 강제하므로, 공개 조회에서 {@link AuthenticatedUser#id}를 부르면
 * 비로그인 요청이 500으로 터진다.
 */
@Tag(name = "PartRequest", description = "부족 부품 요청 게시·조회·마감·삭제")
@RestController
@RequestMapping("/api/v1/part-requests")
public class PartRequestController {

    private final CreatePartRequestUseCase createPartRequest;
    private final ListPartRequestsUseCase listPartRequests;
    private final GetPartRequestUseCase getPartRequest;
    private final ClosePartRequestUseCase closePartRequest;
    private final DeletePartRequestUseCase deletePartRequest;

    public PartRequestController(
            CreatePartRequestUseCase createPartRequest,
            ListPartRequestsUseCase listPartRequests,
            GetPartRequestUseCase getPartRequest,
            ClosePartRequestUseCase closePartRequest,
            DeletePartRequestUseCase deletePartRequest) {
        this.createPartRequest = createPartRequest;
        this.listPartRequests = listPartRequests;
        this.getPartRequest = getPartRequest;
        this.closePartRequest = closePartRequest;
        this.deletePartRequest = deletePartRequest;
    }

    @Operation(
            summary = "부품 요청 게시",
            description = "찾는 부품 1~20개와 세트 번호(선택)를 올린다. 세트가 있으면 그 세트 보유자에게 알림이 간다.\n\n"
                    + "- 400 `PART_REQUEST_INVALID`: 부품 번호·색·수량·메모 규칙 위반\n"
                    + "- 404 `PART_REQUEST_SET_NOT_FOUND`: 카탈로그에 없는 세트\n"
                    + "- 409 `PART_REQUEST_LIMIT_EXCEEDED`: 열린 요청이 이미 10건")
    @PostMapping
    @RequiresOnboarding
    @ResponseStatus(HttpStatus.CREATED)
    public PartRequestResponse create(@RequestBody CreatePartRequestRequest request, HttpServletRequest http) {
        List<ItemCommand> items = request.items() == null
                ? null
                : request.items().stream()
                        .map(item -> item == null
                                ? null
                                : new ItemCommand(item.partNumber(), item.colorName(), item.quantity()))
                        .toList();
        return PartRequestResponse.from(createPartRequest.create(
                new CreatePartRequestCommand(AuthenticatedUser.id(http), request.setNumber(), items, request.note())));
    }

    @Operation(
            summary = "부품 요청 게시판",
            description = "공개. 최신순.\n\n"
                    + "- `setNumber`: 세트 번호 필터(선택)\n"
                    + "- `status`: open(기본) | closed | all\n"
                    + "- `limit`: 1~50, 기본 20. 범위를 벗어나면 가까운 끝값으로 당긴다")
    @GetMapping
    public List<PartRequestResponse> list(
            @RequestParam(value = "setNumber", required = false) String setNumber,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "limit", required = false) Integer limit) {
        ListPartRequestsQuery query = ListPartRequestsQuery.of(setNumber, StatusFilter.fromKey(status), limit);
        return listPartRequests.list(query).stream()
                .map(PartRequestResponse::from)
                .toList();
    }

    @Operation(summary = "내 부품 요청", description = "세션 필요. 상태 무관 최신순 최대 50건.")
    @GetMapping("/mine")
    public List<PartRequestResponse> mine(HttpServletRequest http) {
        return listPartRequests.listMine(AuthenticatedUser.id(http)).stream()
                .map(PartRequestResponse::from)
                .toList();
    }

    @Operation(summary = "부품 요청 상세", description = "공개. 없으면 404 `PART_REQUEST_NOT_FOUND`.")
    @GetMapping("/{requestId}")
    public PartRequestResponse get(@PathVariable String requestId) {
        return PartRequestResponse.from(getPartRequest.get(requestId));
    }

    @Operation(
            summary = "부품 요청 마감",
            description = "작성자만. 403 `PART_REQUEST_ACCESS_DENIED`, 이미 마감됐으면 409 `PART_REQUEST_NOT_OPEN`.")
    @PostMapping("/{requestId}/close")
    public PartRequestResponse close(@PathVariable String requestId, HttpServletRequest http) {
        return PartRequestResponse.from(closePartRequest.close(requestId, AuthenticatedUser.id(http)));
    }

    @Operation(summary = "부품 요청 삭제", description = "작성자만. 403 `PART_REQUEST_ACCESS_DENIED`.")
    @DeleteMapping("/{requestId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String requestId, HttpServletRequest http) {
        deletePartRequest.delete(requestId, AuthenticatedUser.id(http));
    }
}
