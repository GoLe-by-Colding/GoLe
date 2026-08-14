package com.gole.api.common.web;

import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** {@link CurrentUser} 인자 리졸버 등록. */
@Configuration
public class CurrentUserWebConfig implements WebMvcConfigurer {

    private final CurrentUserArgumentResolver currentUserArgumentResolver;

    public CurrentUserWebConfig(CurrentUserArgumentResolver currentUserArgumentResolver) {
        this.currentUserArgumentResolver = currentUserArgumentResolver;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentUserArgumentResolver);
    }
}
