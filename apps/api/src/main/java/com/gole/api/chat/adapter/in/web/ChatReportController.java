package com.gole.api.chat.adapter.in.web;

import com.gole.api.account.adapter.in.web.AuthenticatedUser;
import com.gole.api.chat.application.ChatReportService;
import com.gole.api.report.domain.model.ReportReason;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chat/messages")
public class ChatReportController {

    private final ChatReportService reports;

    public ChatReportController(ChatReportService reports) {
        this.reports = reports;
    }

    @PostMapping("/{messageId}/report")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> report(
            @PathVariable String messageId, @Valid @RequestBody ReportMessageRequest request, HttpServletRequest http) {
        String reportId = reports.report(AuthenticatedUser.id(http), messageId, request.reason(), request.detail());
        return Map.of("id", reportId);
    }

    public record ReportMessageRequest(@NotNull ReportReason reason, @Size(max = 1000) String detail) {}
}
