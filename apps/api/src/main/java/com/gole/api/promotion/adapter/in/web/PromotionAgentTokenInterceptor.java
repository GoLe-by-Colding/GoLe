package com.gole.api.promotion.adapter.in.web;

import com.gole.api.account.application.port.in.ManageAccountsUseCase;
import com.gole.api.account.application.port.in.ManageAccountsUseCase.AccountSummary;
import com.gole.api.account.domain.model.AccountStatus;
import com.gole.api.account.domain.model.Role;
import com.gole.api.common.exception.ForbiddenException;
import com.gole.api.common.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 홍보 납품 창구 가드(promotion-review D29). {@code /api/internal/promotion-agent/**} 에만 걸린다.
 *
 * <p>지인 서버가 보내는 {@code Authorization: Bearer <홍보 토큰>} 의 SHA-256 을 설정값과 비교한다. 백엔드는 토큰 원문을
 * 모른다. 맞으면 봇 ADMIN 계정({@code gole.admin.promotion-bot-email})으로 행세해 작성자·성찰 제안자·메이커-체커가
 * 러너 경로와 같다. 이 토큰은 세션이 아니라서 다른 API 의 가드는 통과하지 못한다 — 열리는 문은 납품 창구 하나다.
 *
 * <p>설정이 비었거나 봇 계정이 없거나 ADMIN 이 아니거나 정지됐으면 막는다(fail-closed).
 */
@Component
public class PromotionAgentTokenInterceptor implements HandlerInterceptor {

    public static final String ATTR_ACTOR_ID = "promotionAgentActorId";

    private static final Logger log = LoggerFactory.getLogger(PromotionAgentTokenInterceptor.class);
    private static final String BEARER = "Bearer ";

    private final byte[] expectedHash;
    private final String botEmail;
    private final ManageAccountsUseCase accounts;

    public PromotionAgentTokenInterceptor(
            @Value("${gole.promotion-agent.token-sha256:}") String tokenSha256,
            @Value("${gole.admin.promotion-bot-email:}") String botEmail,
            ManageAccountsUseCase accounts) {
        this.expectedHash = parseHash(tokenSha256);
        this.botEmail = botEmail == null ? "" : botEmail.trim();
        this.accounts = accounts;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (expectedHash == null || botEmail.isEmpty()) {
            log.warn("[Promotion agent denied] reason=not_configured path={}", request.getRequestURI());
            throw new ForbiddenException("PROMOTION_AGENT_DISABLED", "홍보 납품 창구가 설정되지 않았습니다");
        }
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        String token = header != null && header.startsWith(BEARER)
                ? header.substring(BEARER.length()).trim()
                : "";
        if (token.isEmpty() || !MessageDigest.isEqual(expectedHash, sha256(token))) {
            // 토큰·IP 는 남기지 않는다.
            log.warn("[Promotion agent denied] reason=invalid_token path={}", request.getRequestURI());
            throw new UnauthorizedException("INVALID_PROMOTION_AGENT_TOKEN", "홍보 토큰이 올바르지 않습니다");
        }
        AccountSummary bot = accounts.findByEmail(botEmail)
                .filter(account -> account.role() == Role.ADMIN && account.status() != AccountStatus.SUSPENDED)
                .orElseThrow(() -> {
                    log.warn("[Promotion agent denied] reason=bot_account_unusable path={}", request.getRequestURI());
                    return new ForbiddenException("PROMOTION_BOT_UNAVAILABLE", "홍보 봇 계정을 쓸 수 없습니다");
                });
        request.setAttribute(ATTR_ACTOR_ID, bot.id());
        return true;
    }

    /** 64자 16진수가 아니면 설정이 없는 것으로 본다. */
    static byte[] parseHash(String hex) {
        String trimmed = hex == null ? "" : hex.trim().toLowerCase(Locale.ROOT);
        if (!trimmed.matches("[0-9a-f]{64}")) {
            return null;
        }
        return HexFormat.of().parseHex(trimmed);
    }

    static byte[] sha256(String token) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
