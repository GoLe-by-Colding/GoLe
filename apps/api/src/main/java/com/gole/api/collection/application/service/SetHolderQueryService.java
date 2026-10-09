package com.gole.api.collection.application.service;

import com.gole.api.collection.application.port.in.ListSetHoldersUseCase;
import com.gole.api.collection.application.port.out.CollectionRepositoryPort;
import com.gole.api.collection.domain.model.OwnershipStatus;
import java.util.List;
import org.springframework.stereotype.Service;

/** catalog·listing이 순환 의존 없이 세트 보유·희망 수신자를 조회하도록 분리한 읽기 서비스. */
@Service
public class SetHolderQueryService implements ListSetHoldersUseCase {

    private final CollectionRepositoryPort collectionRepository;

    public SetHolderQueryService(CollectionRepositoryPort collectionRepository) {
        this.collectionRepository = collectionRepository;
    }

    @Override
    public List<String> holdersOf(String setNumber, OwnershipStatus status) {
        return collectionRepository.findUserIdsBySetAndStatus(setNumber, status);
    }
}
