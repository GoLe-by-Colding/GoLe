package com.gole.api.media.application.port.out;

import com.gole.api.media.domain.model.MediaDeletionTask;
import java.time.Instant;
import java.util.List;

/** 미디어 물리 삭제 outbox와 영구 삭제 journal 저장 포트. */
public interface MediaDeletionOutboxPort {

    /** 같은 mediaKey는 최초 한 건만 삽입한다. */
    void enqueueIfAbsent(MediaDeletionTask task);

    /** 이미 COMPLETED였어도 객체가 다시 생긴 것이 확인되면 동일 키를 멱등 재처리한다. */
    void requeue(MediaDeletionTask task);

    List<MediaDeletionTask> findDue(Instant now, int limit);

    /** 복구 시점 이후 이미 완료된 삭제를 객체 스토리지에 재적용한다. */
    List<MediaDeletionTask> findCompletedSince(Instant since);

    void save(MediaDeletionTask task);
}
