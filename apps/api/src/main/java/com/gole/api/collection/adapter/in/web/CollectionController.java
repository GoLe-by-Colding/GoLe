package com.gole.api.collection.adapter.in.web;

import com.gole.api.account.adapter.in.web.AuthenticatedUser;
import com.gole.api.collection.adapter.in.web.CollectionDtos.AddItemRequest;
import com.gole.api.collection.adapter.in.web.CollectionDtos.CollectionItemResponse;
import com.gole.api.collection.adapter.in.web.CollectionDtos.EstimateResponse;
import com.gole.api.collection.application.port.in.EstimateCollectionValueUseCase;
import com.gole.api.collection.application.port.in.ManageCollectionUseCase;
import com.gole.api.collection.application.port.in.ManageCollectionUseCase.AddCommand;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inbound 어댑터(REST): 사용자 컬렉션 관리/추정가. (요구사항 11)
 */
@Tag(name = "Collection", description = "보유·희망 컬렉션 관리")
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
    public List<CollectionItemResponse> items(@PathVariable String userId, HttpServletRequest http) {
        return manageCollectionUseCase.getCollection(AuthenticatedUser.id(http)).stream()
                .map(CollectionItemResponse::from)
                .toList();
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    public CollectionItemResponse add(@Valid @RequestBody AddItemRequest request, HttpServletRequest http) {
        String actorId = AuthenticatedUser.id(http);
        String id = manageCollectionUseCase.add(new AddCommand(actorId, request.setNumber(), request.status()));
        return manageCollectionUseCase.getCollection(actorId).stream()
                .filter(i -> i.id().equals(id))
                .findFirst()
                .map(CollectionItemResponse::from)
                .orElseThrow();
    }

    @DeleteMapping("/items/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable String itemId, HttpServletRequest http) {
        manageCollectionUseCase.remove(itemId, AuthenticatedUser.id(http));
    }

    @GetMapping("/{userId}/estimate")
    public EstimateResponse estimate(@PathVariable String userId, HttpServletRequest http) {
        return new EstimateResponse(estimateCollectionValueUseCase.estimateOwnedValue(AuthenticatedUser.id(http)));
    }
}
