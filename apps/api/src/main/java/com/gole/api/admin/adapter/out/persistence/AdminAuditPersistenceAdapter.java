package com.gole.api.admin.adapter.out.persistence;

import com.gole.api.admin.application.port.out.AdminAuditPort;
import com.gole.api.admin.domain.model.AdminAction;
import com.gole.api.admin.domain.model.AdminActionType;
import com.gole.api.admin.domain.model.AdminTargetType;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

/**
 * 감사 로그 영속성 어댑터. 도메인과 도큐먼트를 양방향 매핑한다.
 */
@Component
public class AdminAuditPersistenceAdapter implements AdminAuditPort {

    private final AdminActionMongoRepository repository;

    public AdminAuditPersistenceAdapter(AdminActionMongoRepository repository) {
        this.repository = repository;
    }

    @Override
    public void append(AdminAction action) {
        repository.save(new AdminActionDocument(
                action.getId(),
                action.getActorId(),
                action.getActorEmail(),
                action.getType().name(),
                action.getTargetType().name(),
                action.getTargetId(),
                action.getReason(),
                action.getOccurredAt()));
    }

    @Override
    public List<AdminAction> findRecent(int limit) {
        return repository.findBy(PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "occurredAt"))).stream()
                .map(AdminAuditPersistenceAdapter::toDomain)
                .toList();
    }

    private static AdminAction toDomain(AdminActionDocument document) {
        return new AdminAction(
                document.getId(),
                document.getActorId(),
                document.getActorEmail(),
                AdminActionType.valueOf(document.getType()),
                AdminTargetType.valueOf(document.getTargetType()),
                document.getTargetId(),
                document.getReason(),
                document.getOccurredAt());
    }
}
