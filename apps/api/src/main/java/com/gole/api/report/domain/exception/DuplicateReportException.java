package com.gole.api.report.domain.exception;

import com.gole.api.common.exception.ConflictException;

public class DuplicateReportException extends ConflictException {

    public DuplicateReportException(String targetId) {
        super("DUPLICATE_REPORT", "이미 신고해 검토를 기다리는 대상입니다");
    }
}
