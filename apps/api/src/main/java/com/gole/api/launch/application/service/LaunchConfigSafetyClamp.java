package com.gole.api.launch.application.service;

import com.gole.api.launch.domain.model.LaunchConfig;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;

/** 결제·정산 실행 조건이 깨졌을 때 높은 공개 단계를 DB에서 영구적으로 낮추는 안전 래치. */
@Component
public class LaunchConfigSafetyClamp {

    private final LaunchConfigSafetyClampWriter writer;

    public LaunchConfigSafetyClamp(LaunchConfigSafetyClampWriter writer) {
        this.writer = writer;
    }

    /**
     * 낙관적 잠금 충돌은 새 트랜잭션에서 한 번 다시 판정한다.
     *
     * <p>MongoDB에서 write conflict가 난 트랜잭션은 그대로 재사용할 수 없다. 실제 저장을 별도
     * 컴포넌트의 REQUIRES_NEW 메서드에 두어 재시도마다 새 스냅샷과 트랜잭션을 사용한다.
     */
    public LaunchConfig enforce() {
        try {
            return writer.enforceOnce();
        } catch (OptimisticLockingFailureException concurrentChange) {
            return writer.enforceOnce();
        }
    }
}
