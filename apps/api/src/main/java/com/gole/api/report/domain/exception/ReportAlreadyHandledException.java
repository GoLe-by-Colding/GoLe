package com.gole.api.report.domain.exception;

import com.gole.api.common.exception.ConflictException;

public class ReportAlreadyHandledException extends ConflictException {

    public ReportAlreadyHandledException(String reportId) {
        super("REPORT_ALREADY_HANDLED", "Report already handled: " + reportId);
    }
}
