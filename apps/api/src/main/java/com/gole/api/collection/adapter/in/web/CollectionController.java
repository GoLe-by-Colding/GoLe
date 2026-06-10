package com.gole.api.collection.adapter.in.web;

import com.gole.api.collection.adapter.in.web.CollectionDtos.AddItemRequest;
import com.gole.api.collection.adapter.in.web.CollectionDtos.CollectionItemResponse;
import com.gole.api.collection.adapter.in.web.CollectionDtos.EstimateResponse;
import com.gole.api.collection.application.port.in.EstimateCollectionValueUseCase;
import com.gole.api.collection.application.port.in.ManageCollectionUseCase;
import com.gole.api.collection.application.port.in.ManageCollectionUseCase.AddCommand;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inbound 어댑터(REST): 사용자 컬렉션 관리/추정가. (요구사항 11)
 */
@RestController
@RequestMapping("/api/v1/collections")
public class CollectionController {

    private final ManageCollectionUseCase manageCollectionUseCase;
    private final EstimateCollectionValueUseCase estimateCollectionValueUseCase;

    public CollectionController(
            ManageCollectionUseCase manageCollectionUseCase,
            EstimateCollectionValueUseCase estimateCollectionValueUseCase) {
        this.manageCollectionUseCase = manageCollectionUseCase;
        this.estimateCollectionValueUseCase = estimateCollectionValueUseCase;
    }

    @GetMapping("/{userId}/items")
    public List<CollectionItemResponse> items(@PathVariable String userId) {
        return manageCollectionUseCase.getCollection(userId).stream()
                .map(CollectionItemResponse::from)
                .toList();
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    public CollectionItemResponse add(@Valid @RequestBody AddItemRequest request) {
        String id =
                manageCollectionUseCase.add(new AddCommand(request.userId(), request.setNumber(), request.status()));
        return manageCollectionUseCase.getCollection(request.userId()).stream()
                .filter(i -> i.id().equals(id))
                .findFirst()
                .map(CollectionItemResponse::from)
                .orElseThrow();
    }

    @DeleteMapping("/items/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable String itemId, @RequestParam("userId") String userId) {
        manageCollectionUseCase.remove(itemId, userId);
    }

    @GetMapping("/{userId}/estimate")
    public EstimateResponse estimate(@PathVariable String userId) {
        return new EstimateResponse(estimateCollectionValueUseCase.estimateOwnedValue(userId));
    }
}
