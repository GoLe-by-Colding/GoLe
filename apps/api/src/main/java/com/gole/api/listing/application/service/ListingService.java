package com.gole.api.listing.application.service;

import com.gole.api.listing.application.port.in.BrowseListingsUseCase;
import com.gole.api.listing.application.port.in.CreateListingUseCase;
import com.gole.api.listing.application.port.in.DeleteListingUseCase;
import com.gole.api.listing.application.port.in.GetListingUseCase;
import com.gole.api.listing.application.port.in.MarkListingSoldUseCase;
import com.gole.api.listing.application.port.in.ReleaseListingUseCase;
import com.gole.api.listing.application.port.in.ReserveListingUseCase;
import com.gole.api.listing.application.port.in.SearchListingsUseCase;
import com.gole.api.listing.application.port.out.ListingIdGeneratorPort;
import com.gole.api.listing.application.port.out.ListingRepositoryPort;
import com.gole.api.listing.application.query.ListingSearchQuery;
import com.gole.api.listing.domain.exception.ListingNotFoundException;
import com.gole.api.listing.domain.model.Listing;
import com.gole.api.listing.domain.model.Money;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 리스팅 유스케이스 구현. inbound port를 구현하고 outbound port에만 의존한다.
 * 횡단 로깅은 UseCaseLoggingAspect가 AOP로 처리.
 */
@Service
public class ListingService
        implements CreateListingUseCase,
                GetListingUseCase,
                SearchListingsUseCase,
                MarkListingSoldUseCase,
                ReserveListingUseCase,
                ReleaseListingUseCase,
                BrowseListingsUseCase,
                DeleteListingUseCase {

    private final ListingRepositoryPort listingRepository;
    private final ListingIdGeneratorPort idGenerator;
    private final Clock clock;

    public ListingService(
            ListingRepositoryPort listingRepository,
            ListingIdGeneratorPort idGenerator,
            Clock clock) {
        this.listingRepository = listingRepository;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    @Override
    public String create(CreateListingCommand command) {
        Listing listing = Listing.create(
                idGenerator.newListingId(),
                command.sellerId(),
                command.title(),
                command.description(),
                Money.won(command.price()),
                command.condition(),
                command.photoUrls(),
                command.catalogSetNumber(),
                Instant.now(clock));
        return listingRepository.save(listing).getId();
    }

    @Override
    public Listing getById(String listingId) {
        return listingRepository.findById(listingId)
                .orElseThrow(() -> new ListingNotFoundException(listingId));
    }

    @Override
    public List<Listing> search(ListingSearchQuery query) {
        return listingRepository.search(query);
    }

    @Override
    public void markSold(String listingId) {
        Listing listing = getById(listingId);
        listing.markSold();
        listingRepository.save(listing);
    }

    @Override
    public Optional<Listing> reserve(String listingId) {
        return listingRepository.reserveIfActive(listingId);
    }

    @Override
    public void release(String listingId) {
        Listing listing = getById(listingId);
        listing.release();
        listingRepository.save(listing);
    }

    @Override
    public List<Listing> activeBySeller(String sellerId) {
        return listingRepository.findActiveBySeller(sellerId);
    }

    @Override
    public List<Listing> activeBySellers(List<String> sellerIds) {
        return listingRepository.findActiveBySellers(sellerIds);
    }

    @Override
    public List<Listing> byIds(List<String> ids) {
        return listingRepository.findByIds(ids);
    }

    @Override
    public void delete(String listingId) {
        Listing listing = getById(listingId);
        listing.delete();
        listingRepository.save(listing);
    }
}
