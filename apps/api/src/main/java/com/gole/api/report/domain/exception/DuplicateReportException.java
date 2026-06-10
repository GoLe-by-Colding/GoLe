package com.gole.api.report.domain.exception;

import com.gole.api.common.exception.ConflictException;

public class DuplicateReportException extends ConflictException {

    public DuplicateReportException(String targetId) {
        super("DUPLICATE_REPORT", "Already reported and pending: " + targetId);
    }
}
