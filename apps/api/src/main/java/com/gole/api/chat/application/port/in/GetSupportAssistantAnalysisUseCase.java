package com.gole.api.chat.application.port.in;

import com.gole.api.chat.domain.model.SupportAssistantAnalysis;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Inbound port: 완료된 문의 AI 분석(검토용 초안). 사람이 검토하기 전에는 사용자에게 나가지 않는다. */
public interface GetSupportAssistantAnalysisUseCase {

    Optional<SupportAssistantAnalysis> findCompleted(String roomId);

    Map<String, SupportAssistantAnalysis> findCompleted(List<String> roomIds);
}
