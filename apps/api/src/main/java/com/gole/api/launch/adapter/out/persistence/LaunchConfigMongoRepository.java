package com.gole.api.launch.adapter.out.persistence;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface LaunchConfigMongoRepository extends MongoRepository<LaunchConfigDocument, String> {}
