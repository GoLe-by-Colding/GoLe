package com.gole.api.report.domain.exception;

import com.gole.api.common.exception.NotFoundException;

public class ReportNotFoundException extends NotFoundException {

    public ReportNotFoundException(String reportId) {
        super("REPORT_NOT_FOUND", "Report not found: " + reportId);
    }
}
