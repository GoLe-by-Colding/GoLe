package com.gole.api.report.adapter.in.web;

import com.gole.api.report.adapter.in.web.ReportDtos.SubmitReportRequest;
import com.gole.api.report.application.port.in.SubmitReportUseCase;
import com.gole.api.report.application.port.in.SubmitReportUseCase.SubmitReportCommand;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inbound 어댑터(REST): 신고 접수 — 가품·IP 도용·사기 매물/게시글 notice & takedown 입구.
 */
@Tag(name = "Report", description = "매물·게시글 신고 접수")
@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final SubmitReportUseCase submitReportUseCase;

    public ReportController(SubmitReportUseCase submitReportUseCase) {
        this.submitReportUseCase = submitReportUseCase;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> submit(@Valid @RequestBody SubmitReportRequest request) {
        String id = submitReportUseCase.submit(new SubmitReportCommand(
                request.reporterId(), request.targetType(), request.targetId(), request.reason(), request.detail()));
        return Map.of("id", id);
    }
}
