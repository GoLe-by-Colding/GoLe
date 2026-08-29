package com.gole.api.launch.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gole.api.launch.domain.model.LaunchConfig;
import com.gole.api.launch.domain.model.LaunchStage;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;

class LaunchConfigSafetyClampTest {

    @Test
    @DisplayName("낙관적 잠금 충돌은 새 writer 호출로 한 번 재시도한다")
    void retriesOptimisticConflictThroughFreshWriterInvocation() {
        LaunchConfigSafetyClampWriter writer = mock(LaunchConfigSafetyClampWriter.class);
        LaunchConfig safe = new LaunchConfig(LaunchStage.BROWSE_ONLY, Map.of(), null, "system");
        when(writer.enforceOnce())
                .thenThrow(new OptimisticLockingFailureException("동시 변경"))
                .thenReturn(safe);

        LaunchConfig result = new LaunchConfigSafetyClamp(writer).enforce();

        assertThat(result).isSameAs(safe);
        verify(writer, org.mockito.Mockito.times(2)).enforceOnce();
    }
}
