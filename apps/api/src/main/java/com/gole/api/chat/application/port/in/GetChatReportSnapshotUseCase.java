package com.gole.api.chat.application.port.in;

import com.gole.api.chat.domain.model.ChatReportSnapshot;
import java.util.Optional;

/** Inbound port: 신고 시점에 고정된 대화 사본. 관리자는 살아 있는 방 대신 이것만 본다. */
public interface GetChatReportSnapshotUseCase {

    Optional<ChatReportSnapshot> findByReportId(String reportId);
}
