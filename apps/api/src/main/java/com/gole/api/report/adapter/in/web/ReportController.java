package com.gole.api.report.adapter.in.web;

import com.gole.api.account.adapter.in.web.AuthenticatedUser;
import com.gole.api.common.exception.BadRequestException;
import com.gole.api.report.adapter.in.web.ReportDtos.SubmitReportRequest;
import com.gole.api.report.application.port.in.SubmitReportUseCase;
import com.gole.api.report.application.port.in.SubmitReportUseCase.SubmitReportCommand;
import com.gole.api.report.domain.model.ReportTargetType;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inbound 어댑터(REST): 신고 접수 — 가품·IP 도용·사기 매물/게시글 및 부적절한 후기 조치 입구.
 */
@Tag(name = "Report", description = "매물·게시글·후기 신고 접수")
@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final SubmitReportUseCase submitReportUseCase;

    public ReportController(SubmitReportUseCase submitReportUseCase) {
        this.submitReportUseCase = submitReportUseCase;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> submit(@Valid @RequestBody SubmitReportRequest request, HttpServletRequest http) {
        if (request.targetType() == ReportTargetType.CHAT_MESSAGE) {
            throw new BadRequestException("CHAT_REPORT_ROUTE_REQUIRED", "채팅 메시지는 대화 화면의 전용 신고 기능을 이용해 주세요");
        }
        if (request.targetType() == ReportTargetType.COMMENT) {
            throw new BadRequestException("COMMENT_REPORT_ROUTE_REQUIRED", "댓글은 게시글 화면의 전용 신고 기능을 이용해 주세요");
        }
        String id = submitReportUseCase.submit(new SubmitReportCommand(
                AuthenticatedUser.id(http),
                request.targetType(),
                request.targetId(),
                request.reason(),
                request.detail()));
        return Map.of("id", id);
    }
}
