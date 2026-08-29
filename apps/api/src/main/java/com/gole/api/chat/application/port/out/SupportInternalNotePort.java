package com.gole.api.chat.application.port.out;

import java.time.Instant;
import java.util.List;

/**
 * Outbound port: 운영자 내부 메모.
 *
 * <p>메시지와 <b>별도 저장소</b>인 것이 이 포트의 존재 이유다. 같은 컬렉션에 플래그로 구분하면
 * 언젠가 조건을 빠뜨린 쿼리 하나가 사용자에게 내부 메모를 노출시킨다. 구조를 나누면 그 실수가
 * 불가능해진다 — 사용자 메시지 조회 경로는 이 포트를 아예 모른다.
 */
public interface SupportInternalNotePort {

    void append(String roomId, String authorId, String note, Instant at);

    List<InternalNote> findByRoom(String roomId, int limit);

    record InternalNote(String id, String roomId, String authorId, String note, Instant createdAt) {}
}
