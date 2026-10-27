package com.gole.api.operations.application.port.out;

public interface OperationsDiagnostics {
    /** 제한된 비민감 진단 코드만 반환하며 알림은 전송하지 않는다. */
    String inspect(String jobId);
}
