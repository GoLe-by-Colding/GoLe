
package com.gole.api.pricing.adapter.in.web;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.gole.api.pricing.application.port.in.GetTrendingSetsUseCase;
import com.gole.api.pricing.application.port.in.GetTrendingSetsUseCase.TrendingSet;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inbound 어댑터(REST): 인기(트렌딩) 세트 랭킹. (백로그 13.4)
 */
@Tag(name = "Trending", description = "인기 세트 랭킹(Redis 캐싱)")
@RestController
@RequestMapping("/api/v1/pricing/trending")
public class TrendingController {

    private final GetTrendingSetsUseCase getTrendingSetsUseCase;

    public TrendingController(GetTrendingSetsUseCase getTrendingSetsUseCase) {
        this.getTrendingSetsUseCase = getTrendingSetsUseCase;
    }

    @GetMapping
    public List<TrendingSetResponse> trending(
            @RequestParam(value = "limit", required = false, defaultValue = "8") int limit) {
        return getTrendingSetsUseCase.getTrending(limit).stream()
                .map(TrendingSetResponse::from)
                .toList();
    }

    public record TrendingSetResponse(
            String setNumber, String name, String imageUrl, long tradeCount, long averagePrice) {

        static TrendingSetResponse from(TrendingSet set) {
            return new TrendingSetResponse(
                    set.setNumber(), set.name(), set.imageUrl(), set.tradeCount(), set.averagePrice());
        }
    }
}
