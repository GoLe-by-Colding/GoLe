package com.gole.api.report.adapter.out.persistence;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ReportMongoRepository extends MongoRepository<ReportDocument, String> {

    List<ReportDocument> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);

    List<ReportDocument> findAllByOrderByCreatedAtDesc(Pageable pageable);

    boolean existsByReporterIdAndTargetTypeAndTargetIdAndStatus(
            String reporterId, String targetType, String targetId, String status);

    long countByStatus(String status);
}
