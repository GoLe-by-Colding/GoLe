package com.gole.api.chat.application.port.out;

import com.gole.api.chat.domain.model.SupportStatus;
import com.gole.api.chat.domain.model.SupportTicket;
import java.util.List;
import java.util.Optional;

/** Outbound port: 운영팀 문의 처리 상태 저장소. 사용자에게 나가는 방 정보와 분리해 둔다. */
public interface SupportTicketRepositoryPort {

    Optional<SupportTicket> findByRoomId(String roomId);

    /** 사용자 방 목록에 붙일 문의 상태를 한 번에 읽어 N+1 조회를 막는다. */
    List<SupportTicket> findByRoomIds(List<String> roomIds);

    SupportTicket save(SupportTicket ticket);

    /** 관리자 인박스. {@code status} 가 null 이면 전체. */
    List<SupportTicket> findByStatus(SupportStatus status, int limit);
}
