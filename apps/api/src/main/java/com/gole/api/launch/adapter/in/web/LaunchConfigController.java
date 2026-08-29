package com.gole.api.launch.adapter.in.web;

import com.gole.api.launch.adapter.in.web.LaunchDtos.LaunchConfigResponse;
import com.gole.api.launch.application.port.in.GetLaunchConfigUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공개 설정 조회. 로그인 없이 열려 있어야 한다 — 첫 화면이 이 값으로 무엇을 보여줄지 정한다.
 *
 * <p>이 엔드포인트는 <b>절대 실패하면 안 되는 축</b>에 가깝다. 실패하면 프론트가 Stage 0 으로
 * fail-closed 하므로 서비스가 통째로 공사중으로 보인다. 그래서 저장된 값이 손상돼도 예외를
 * 던지는 대신 가장 닫힌 해석으로 응답한다(영속 어댑터 참고).
 */
@Tag(name = "Config", description = "공개 설정")
@RestController
@RequestMapping("/api/v1/config")
public class LaunchConfigController {

    private final GetLaunchConfigUseCase launchConfig;

    public LaunchConfigController(GetLaunchConfigUseCase launchConfig) {
        this.launchConfig = launchConfig;
    }

    @Operation(
            summary = "공개 단계 조회",
            description = "서비스 공개 단계(0~3)와 기능별 개방 여부. 인증이 필요 없다. " + "조회에 실패하면 클라이언트는 Stage 0(공사중)으로 닫아야 한다.")
    @GetMapping("/launch")
    public LaunchConfigResponse launch() {
        return LaunchConfigResponse.from(launchConfig.current());
    }
}
