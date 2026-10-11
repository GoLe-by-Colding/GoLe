package com.gole.api.chat.application.service;

import com.gole.api.chat.application.port.in.GetChatReportSnapshotUseCase;
import com.gole.api.chat.application.port.out.ChatReportSnapshotPort;
import com.gole.api.chat.domain.model.ChatReportSnapshot;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** 신고 시점에 고정된 대화 사본 조회. */
@Service
public class ChatReportSnapshotService implements GetChatReportSnapshotUseCase {

    private final ChatReportSnapshotPort snapshots;

    public ChatReportSnapshotService(ChatReportSnapshotPort snapshots) {
        this.snapshots = snapshots;
    }

    @Override
    public Optional<ChatReportSnapshot> findByReportId(String reportId) {
        return snapshots.findByReportId(reportId);
    }
}
