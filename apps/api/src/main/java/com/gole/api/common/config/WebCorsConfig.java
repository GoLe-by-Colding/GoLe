package com.gole.api.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 프론트엔드(Next.js) 개발 오리진에 대한 CORS 허용.
 * 허용 오리진은 {@code gole.web.allowed-origins}(GOLE_WEB_ALLOWED_ORIGINS)로 설정한다.
 *
 * <p>기본값에 3000과 3010을 모두 넣는 이유: 로컬 dev 서버가 3000 점유 시 3010으로 뜨는데
 * (`pnpm dev:web`의 GOLE_WEB_PORT 기본값), 오리진이 어긋나면 브라우저 요청만 조용히 403이 되어
 * "curl은 되는데 화면만 안 되는" 형태로 디버깅이 오래 걸린다.
 */
@Configuration
public class WebCorsConfig implements WebMvcConfigurer {

    private static final String DEFAULT_ORIGINS = "http://localhost:3000,http://localhost:3010";

    private final String[] allowedOrigins;

    public WebCorsConfig(@Value("${gole.web.allowed-origins:" + DEFAULT_ORIGINS + "}") String[] allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}
