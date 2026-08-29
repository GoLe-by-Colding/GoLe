package com.gole.api.launch.adapter.out.persistence;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface LaunchConfigHistoryMongoRepository extends MongoRepository<LaunchConfigHistoryDocument, String> {

    List<LaunchConfigHistoryDocument> findBy(Pageable pageable);
}
