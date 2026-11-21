package com.gole.api.report.domain.exception;

import com.gole.api.common.exception.ConflictException;

public class ReportAlreadyHandledException extends ConflictException {

    public ReportAlreadyHandledException(String reportId) {
        super("REPORT_ALREADY_HANDLED", "이미 처리된 신고입니다");
    }
}
