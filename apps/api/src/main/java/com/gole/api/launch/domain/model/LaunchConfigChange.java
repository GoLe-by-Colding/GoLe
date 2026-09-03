package com.gole.api.launch.domain.model;

import java.time.Instant;

/**
 * 공개 설정 변경 이력 1건. 추가만 되고 수정·삭제하지 않는다.
 *
 * <p>관리자 감사 로그({@code admin_actions})에도 같은 조치가 남지만, 그쪽은 사유가 자유 문자열
 * 하나뿐이라 "무엇이 무엇으로" 바뀌었는지 구조적으로 남지 않는다. 결제를 열고 닫은 기록은
 * 사후에 기계적으로 되짚을 수 있어야 하므로 전/후 값을 별도 이력으로 보존한다.
 *
 * @param before 변경 전 표기(단계 또는 기능 상태). 최초 설정이면 이전 값이 없을 수 있다.
 * @param reason 변경 사유. 비어 있을 수 없다 — 왜 열고 닫았는지가 이 이력의 존재 이유다.
 */
public record LaunchConfigChange(
        String id,
        Type type,
        String target,
        String before,
        String after,
        String reason,
        String actorId,
        String actorEmail,
        Instant occurredAt) {

    public enum Type {
        STAGE,
        FEATURE_OVERRIDE,
        READINESS
    }
}
