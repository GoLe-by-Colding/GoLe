package com.gole.api.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 3.0 전역 설정.
 * UI: /swagger-ui.html  |  JSON 명세: /v3/api-docs
 *
 * <p>인증: 브라우저는 HttpOnly 쿠키, 외부 API 클라이언트는 Bearer 토큰(Authorization: Bearer {sessionToken}).
 * 관리자 전용 엔드포인트(/api/admin/**)는 ADMIN 권한 토큰이 필요하다.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("GoLe LEGO Marketplace API")
                        .version("1.0.0")
                        .description(
                                """
                                GoLe — 레고 중고거래 마켓플레이스 백엔드 API.

                                ## 인증
                                브라우저는 **HttpOnly 세션 쿠키**, 외부 API 클라이언트는 **Bearer 토큰**으로 인증합니다.
                                `POST /api/v1/accounts/sessions`로 로그인하면 `sessionToken`을 발급받습니다.
                                브라우저에는 같은 응답에서 HttpOnly·SameSite 쿠키가 설정됩니다. API 클라이언트는
                                토큰을 `Authorization: Bearer {sessionToken}` 헤더에 포함하세요.

                                관리자(`/api/admin/**`) 엔드포인트는 **ADMIN 권한 계정의 토큰**이 필요합니다.

                                ## 에러 형식
                                ```json
                                { "code": "BUSINESS_ERROR_CODE", "message": "설명" }
                                ```
                                """)
                        .contact(new Contact().name("GoLe Team").url("https://gole.co.kr"))
                        .license(new License().name("Private")))
                .servers(List.of(
                        new Server().url("https://gole.co.kr").description("Production"),
                        new Server().url("http://localhost:8080").description("Local")))
                .components(new Components()
                        .addSecuritySchemes(
                                BEARER_SCHEME,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("OpaqueToken")
                                        .description("로그인 후 발급된 불투명 세션 토큰")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
