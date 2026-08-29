package com.gole.api.chat.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * 사용자 차단. 차단한 사람 기준으로 한 행이다.
 *
 * <p>판정은 <b>양방향</b>이다. A가 B를 차단하면 A→B 도 B→A 도 막힌다. 한 방향만 막으면
 * 차단한 사람이 상대에게 계속 말을 걸 수 있어, 차단이 "안 보기"가 아니라 일방적 확성기가 된다.
 *
 * <p>차단은 기존 방을 지우지 않는다. 과거 대화는 신고·분쟁의 증거라서 보존하고 전송만 막는다.
 */
public record ChatBlock(String blockerId, String blockedId, String reason, Instant blockedAt) {

    public ChatBlock {
        Objects.requireNonNull(blockerId, "blockerId");
        Objects.requireNonNull(blockedId, "blockedId");
        Objects.requireNonNull(blockedAt, "blockedAt");
        if (blockerId.equals(blockedId)) {
            throw new IllegalArgumentException("자기 자신은 차단할 수 없다");
        }
    }
}
