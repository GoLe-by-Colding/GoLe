package com.gole.api.listing.adapter.in.web;

import com.gole.api.listing.domain.model.ItemCondition;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * listing 웹 어댑터의 요청 바인딩 설정.
 *
 * <p>기본 enum 컨버터는 {@code Enum.valueOf}라 대소문자를 가린다. 그래서 API 규약대로
 * 소문자 키(`?condition=new_sealed`)를 보내면 변환에 실패했다. 프론트가 보내는 값이
 * 정확히 그 형태여서 상태 필터 검색이 통째로 500이었다.
 *
 * <p>{@link ItemCondition#fromKey}로 바인딩하면 소문자 키는 물론 3단계 시절 값
 * (`used_complete`)까지 흡수한다. 북마크·외부 링크에 남은 옛 URL이 깨지지 않는다.
 */
@Configuration
public class ListingWebConfig implements WebMvcConfigurer {

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(new StringToItemConditionConverter());
    }

    static final class StringToItemConditionConverter implements Converter<String, ItemCondition> {

        /**
         * 빈 값은 {@code null}(필터 없음), 아는 키는 해당 등급, 모르는 키는 거부한다.
         *
         * <p>모르는 키를 임의의 등급으로 흡수하지 않는 이유 — 오타가 나도 200이 떨어지면
         * 사용자는 필터가 걸린 줄 알고 엉뚱한 목록을 신뢰하게 된다.
         */
        @Override
        public ItemCondition convert(String source) {
            if (source.isBlank()) {
                return null;
            }
            return ItemCondition.parseKey(source)
                    .orElseThrow(() -> new IllegalArgumentException("알 수 없는 상태 등급: " + source));
        }
    }
}
