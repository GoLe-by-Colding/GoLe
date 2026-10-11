package com.gole.api.notification.adapter.in.web;

import com.gole.api.common.exception.BadRequestException;
import com.gole.api.common.web.auth.AuthenticatedUser;
import com.gole.api.notification.application.port.in.ManageNotificationPreferencesUseCase;
import com.gole.api.notification.domain.model.NotificationCategory;
import com.gole.api.notification.domain.model.NotificationPreferences;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inbound 어댑터(REST): 내 알림 수신 설정. (notification-preferences P2·P3)
 *
 * <p>경로의 {@code userId}는 무시하고 세션 계정을 쓴다 — 기존 알림 API와 같다. 남의 설정을 바꿀 길을
 * 만들지 않기 위해서다. {@code /api/v1/users/**}는 인터셉터가 이미 세션을 요구한다.
 */
@Tag(name = "Notification", description = "알림 목록·읽음 처리")
@RestController
@RequestMapping("/api/v1/users/{userId}/notification-preferences")
public class NotificationPreferenceController {

    private final ManageNotificationPreferencesUseCase preferences;

    public NotificationPreferenceController(ManageNotificationPreferencesUseCase preferences) {
        this.preferences = preferences;
    }

    @Operation(summary = "알림 수신 설정 조회", description = "분류별 켜짐 여부. 저장한 적이 없으면 전부 켜짐입니다.")
    @GetMapping
    public NotificationPreferencesResponse get(@PathVariable String userId, HttpServletRequest http) {
        return NotificationPreferencesResponse.from(preferences.get(AuthenticatedUser.id(http)));
    }

    @Operation(summary = "알림 수신 설정 변경", description = "보낸 분류만 바꾸고 빠진 분류는 그대로 둡니다. 거래 진행(trade)은 끌 수 없습니다.")
    @PutMapping
    public NotificationPreferencesResponse update(
            @PathVariable String userId, @RequestBody UpdateRequest request, HttpServletRequest http) {
        String accountId = AuthenticatedUser.id(http);
        return NotificationPreferencesResponse.from(preferences.update(accountId, parse(request)));
    }

    private static Map<NotificationCategory, Boolean> parse(UpdateRequest request) {
        if (request == null || request.enabled() == null) {
            throw new BadRequestException("VALIDATION_ERROR", "enabled 값이 필요합니다");
        }
        Map<NotificationCategory, Boolean> changes = new EnumMap<>(NotificationCategory.class);
        for (Map.Entry<String, Boolean> entry : request.enabled().entrySet()) {
            NotificationCategory category = categoryOf(entry.getKey());
            if (entry.getValue() == null) {
                throw new BadRequestException(
                        "VALIDATION_ERROR", "enabled." + category.key() + " 값은 true 또는 false여야 합니다");
            }
            changes.put(category, entry.getValue());
        }
        return changes;
    }

    /** 외부 키는 소문자 분류 이름이다({@link NotificationCategory#key()}). 다른 표기는 받지 않는다. */
    private static NotificationCategory categoryOf(String key) {
        return Arrays.stream(NotificationCategory.values())
                .filter(category -> category.key().equals(key))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("NOTIFICATION_CATEGORY_UNKNOWN", "알 수 없는 알림 분류입니다"));
    }

    /** @param enabled 분류 키 → 켜짐 여부. 예: {@code {"offer": false, "watch": true}} */
    public record UpdateRequest(Map<String, Boolean> enabled) {}

    public record NotificationPreferencesResponse(List<CategoryResponse> categories) {

        static NotificationPreferencesResponse from(NotificationPreferences preferences) {
            // 순서는 enum 순서로 고정한다 — 화면이 이 순서대로 그린다. (P2)
            return new NotificationPreferencesResponse(Arrays.stream(NotificationCategory.values())
                    .map(category -> new CategoryResponse(
                            category.key(), category.label(), preferences.isEnabled(category), category.mandatory()))
                    .toList());
        }
    }

    public record CategoryResponse(String key, String label, boolean enabled, boolean mandatory) {}
}
