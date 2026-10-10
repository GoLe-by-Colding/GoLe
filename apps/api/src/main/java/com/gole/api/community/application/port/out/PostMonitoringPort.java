package com.gole.api.community.application.port.out;

import com.gole.api.community.application.port.in.MonitorPostsUseCase.PostMonitorRow;
import java.util.List;

/** Outbound port: 운영 화면용 게시글 목록·건수 조회. */
public interface PostMonitoringPort {

    List<PostMonitorRow> recentPosts(String status, String query, int limit);

    long estimatedPostCount();
}
