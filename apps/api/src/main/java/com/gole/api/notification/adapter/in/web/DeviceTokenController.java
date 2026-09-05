package com.gole.api.notification.adapter.in.web;

import com.gole.api.account.adapter.in.web.AuthenticatedUser;
import com.gole.api.notification.application.port.in.RegisterDeviceTokenUseCase;
import com.gole.api.notification.application.port.in.RegisterDeviceTokenUseCase.RegisterDeviceTokenCommand;
import com.gole.api.notification.domain.model.DevicePlatform;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inbound 어댑터(REST): 단말 푸시 토큰 등록·해제. (R8.1)
 *
 * <p>대상 계정은 <b>세션에서 정한다</b>. 본문으로 받으면 남의 계정에 자기 단말을 등록해
 * 그 사람의 알림을 가로챌 수 있다.
 */
@Tag(name = "Notification", description = "알림 목록·읽음 처리")
@RestController
@RequestMapping("/api/v1/notifications/devices")
public class DeviceTokenController {

    private final RegisterDeviceTokenUseCase registerDeviceToken;

    public DeviceTokenController(RegisterDeviceTokenUseCase registerDeviceToken) {
        this.registerDeviceToken = registerDeviceToken;
    }

    @Operation(summary = "단말 푸시 토큰 등록", description = "멱등합니다. 같은 토큰을 다시 보내면 소유 계정과 시각만 갱신됩니다.")
    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void register(@RequestBody RegisterRequest request, HttpServletRequest http) {
        registerDeviceToken.register(new RegisterDeviceTokenCommand(
                AuthenticatedUser.id(http), request.token(), DevicePlatform.valueOf(request.platform())));
    }

    @Operation(summary = "단말 푸시 토큰 해제", description = "로그아웃 시 호출합니다.")
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unregister(@RequestParam("token") String token) {
        registerDeviceToken.unregister(token);
    }

    public record RegisterRequest(@NotBlank String token, @NotBlank String platform) {}
}
