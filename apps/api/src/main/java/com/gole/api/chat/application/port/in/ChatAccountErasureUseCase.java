package com.gole.api.chat.application.port.in;

/**
 * Inbound port: 회원 탈퇴 때 채팅 컨텍스트가 맡는 일. (account-deletion-participants D1)
 *
 * <p>아직 파기하면 안 되는 기록이 남았는지 답하고, 파기할 때 자기 기록을 지우거나 익명 주체로 바꾼다. 호출자의
 * 트랜잭션 안에서 돌아 계정 파기와 함께 커밋되거나 함께 되돌려진다.
 */
public interface ChatAccountErasureUseCase {

    /** 운영팀 문의 기록이 있는가 — 문의 대화는 별도 파기 절차를 먼저 거친다. */
    boolean hasSupportRecords(String accountId);

    /** 열려 있는 그룹 대화를 소유하는가 — 소유권을 넘긴 뒤에 파기한다. */
    boolean ownsOpenGroup(String accountId);

    /**
     * 이 계정의 기록을 지우거나 {@code anonymousSubject}로 바꾼다.
     *
     * @return 처리한 기록 수
     */
    ChatErasure erase(String accountId, String anonymousSubject);

    /**
     * @param chatReadCursors 읽음 위치 삭제 수
     * @param chatBlocks 차단 관계 삭제 수
     * @param chatMessages 보낸 메시지 삭제 수
     * @param marketChatRooms 매물 대화방 당사자 가명화 수
     * @param socialChatRooms 1:1·그룹 대화방 구성원 정리 수
     * @param chatReportSnapshots 채팅 신고 스냅샷 가명화 수
     */
    record ChatErasure(
            long chatReadCursors,
            long chatBlocks,
            long chatMessages,
            long marketChatRooms,
            long socialChatRooms,
            long chatReportSnapshots) {}
}
