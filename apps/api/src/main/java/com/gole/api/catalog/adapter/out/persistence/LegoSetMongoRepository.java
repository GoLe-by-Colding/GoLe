package com.gole.api.catalog.adapter.out.persistence;

import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Spring Data MongoDB 리포지토리. 파생 쿼리(derived query)로 조회 메서드를 제공한다.
 */
public interface LegoSetMongoRepository extends MongoRepository<LegoSetDocument, String> {

    /**
     * 이름 또는 테마에 검색어가 (대소문자 무시) 포함된 문서 검색.
     */
    List<LegoSetDocument> findByNameContainingIgnoreCaseOrThemeContainingIgnoreCase(
            String name, String theme);

    /** 세트 번호(id)가 검색어로 시작하는 문서 검색(자동완성용). */
    List<LegoSetDocument> findByIdStartingWith(String setNumber);

    /**
     * 추천(featured) 플래그가 켜진 문서 목록을 최대 limit 개 조회.
     */
    List<LegoSetDocument> findByFeaturedIsTrue(Limit limit);
}
