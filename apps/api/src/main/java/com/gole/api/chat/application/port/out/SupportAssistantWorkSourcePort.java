package com.gole.api.chat.application.port.out;

import com.gole.api.chat.application.port.out.SupportAssistantPort.Request;
import java.util.List;
import java.util.Optional;

/** 문의 AI 작업을 방 ID만으로 다시 구성한다. 원문은 기존 문의 메시지를 단일 원본으로 사용한다. */
public interface SupportAssistantWorkSourcePort {

    Optional<Request> findRequest(String roomId);

    /** 작업 등록 순간의 저장소 장애를 복구하기 위한 최근 문의 ID 목록이다. */
    List<String> findRecentRoomIds(int limit);
}
