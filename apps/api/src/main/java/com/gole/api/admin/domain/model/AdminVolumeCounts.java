package com.gole.api.admin.domain.model;

/** 운영 대시보드의 전체 규모 숫자. 각 값은 소유 컨텍스트가 센 추정치다. (admin-console 요구사항 2.2) */
public record AdminVolumeCounts(
        long accounts, long legoSets, long listings, long orders, long posts, long reviews, long priceTransactions) {}
