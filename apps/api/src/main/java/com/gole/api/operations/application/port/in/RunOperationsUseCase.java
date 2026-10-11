package com.gole.api.operations.application.port.in;

import com.gole.api.operations.domain.OperationRun;
import java.util.List;

/** Inbound port: 운영 점검 작업(예외 큐 재집계·결제 준비·알림 연동 점검)의 목록·이력·실행. 상태를 바꾸는 작업은 없다. */
public interface RunOperationsUseCase {

    List<Job> jobs();

    List<OperationRun> history();

    OperationRun execute(String jobId, String actorId, String reasonCode, String retryOf);

    record Job(String id, String title, String description) {}
}
