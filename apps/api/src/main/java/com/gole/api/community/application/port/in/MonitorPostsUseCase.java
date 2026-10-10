package com.gole.api.community.application.port.in;

import java.time.Instant;
import java.util.List;

/** Inbound port: 운영 화면이 보는 게시글 현황. 숨김·삭제를 포함한 모든 상태를 본다. */
public interface MonitorPostsUseCase {

    /** 최근 게시글. status 가 null 이거나 비면 전체, query 는 글 ID·내용·작성자 ID·주제의 부분 일치. */
    List<PostMonitorRow> recentPosts(String status, String query, int limit);

    /** 전체 게시글 수(추정치). */
    long estimatedPostCount();

    record PostMonitorRow(String id, String authorId, String content, String type, String status, Instant createdAt) {}
}
