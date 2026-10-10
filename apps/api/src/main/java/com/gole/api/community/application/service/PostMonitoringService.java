package com.gole.api.community.application.service;

import com.gole.api.community.application.port.in.MonitorPostsUseCase;
import com.gole.api.community.application.port.out.PostMonitoringPort;
import java.util.List;
import org.springframework.stereotype.Service;

/** 운영 화면용 게시글 현황. 검색은 저장소 포트가 한다. */
@Service
public class PostMonitoringService implements MonitorPostsUseCase {

    private final PostMonitoringPort monitoring;

    public PostMonitoringService(PostMonitoringPort monitoring) {
        this.monitoring = monitoring;
    }

    @Override
    public List<PostMonitorRow> recentPosts(String status, String query, int limit) {
        return monitoring.recentPosts(status, query, limit);
    }

    @Override
    public long estimatedPostCount() {
        return monitoring.estimatedPostCount();
    }
}
