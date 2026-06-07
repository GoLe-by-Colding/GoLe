package com.gole.api.listing.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gole.api.listing.application.port.in.CreateListingUseCase.CreateListingCommand;
import com.gole.api.listing.application.port.out.ListingIdGeneratorPort;
import com.gole.api.listing.application.port.out.ListingRepositoryPort;
import com.gole.api.listing.application.query.ListingSearchQuery;
import com.gole.api.listing.domain.exception.InvalidPriceException;
import com.gole.api.listing.domain.exception.ListingStateException;
import com.gole.api.listing.domain.exception.MissingPhotoException;
import com.gole.api.listing.domain.model.ItemCondition;
import com.gole.api.listing.domain.model.Listing;
import com.gole.api.listing.domain.model.ListingStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ListingServiceTest {

    private InMemoryListingRepository repository;
    private ListingService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryListingRepository();
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        service = new ListingService(repository, new SequentialIdGenerator(), clock);
    }

    private CreateListingCommand validCommand() {
        return new CreateListingCommand(
                "seller-1", "에펠탑 10307", "미개봉", 280_000,
                ItemCondition.NEW_SEALED, List.of("photo-1.jpg"), "10307");
    }

    @Test
    void create_persistsActiveListing() {
        String id = service.create(validCommand());
        Listing saved = service.getById(id);
        assertThat(saved.isActive()).isTrue();
        assertThat(saved.getPrice().amount()).isEqualTo(280_000);
    }

    @Test
    void create_rejectsMissingPhoto() {
        CreateListingCommand cmd = new CreateListingCommand(
                "seller-1", "title", "desc", 1000, ItemCondition.NEW_SEALED, List.of(), "10307");
        assertThatThrownBy(() -> service.create(cmd)).isInstanceOf(MissingPhotoException.class);
    }

    @Test
    void create_rejectsNegativePrice() {
        CreateListingCommand cmd = new CreateListingCommand(
                "seller-1", "title", "desc", -1, ItemCondition.NEW_SEALED, List.of("p.jpg"), null);
        assertThatThrownBy(() -> service.create(cmd)).isInstanceOf(InvalidPriceException.class);
    }

    @Test
    void markSold_excludesFromActive() {
        String id = service.create(validCommand());
        service.markSold(id);
        assertThat(service.search(ListingSearchQuery.newestAll())).isEmpty();
        assertThat(service.getById(id).getStatus()).isEqualTo(ListingStatus.SOLD);
    }

    @Test
    void delete_rejectedWhenReserved() {
        Listing reserved = new Listing(
                "r1", "seller-1", "t", "d",
                com.gole.api.listing.domain.model.Money.won(1000),
                ItemCondition.NEW_SEALED, List.of("p.jpg"), null,
                ListingStatus.RESERVED, Instant.parse("2026-01-01T00:00:00Z"));
        repository.save(reserved);
        assertThatThrownBy(() -> service.delete("r1")).isInstanceOf(ListingStateException.class);
    }

    private static final class InMemoryListingRepository implements ListingRepositoryPort {
        private final List<Listing> store = new ArrayList<>();

        @Override
        public Listing save(Listing listing) {
            store.removeIf(l -> l.getId().equals(listing.getId()));
            store.add(listing);
            return listing;
        }

        @Override
        public Optional<Listing> findById(String listingId) {
            return store.stream().filter(l -> l.getId().equals(listingId)).findFirst();
        }

        @Override
        public List<Listing> search(ListingSearchQuery query) {
            return store.stream().filter(Listing::isActive).toList();
        }

        @Override
        public Optional<Listing> reserveIfActive(String listingId) {
            return findById(listingId).filter(Listing::isActive);
        }

        @Override
        public List<Listing> findActiveBySeller(String sellerId) {
            return store.stream()
                    .filter(Listing::isActive)
                    .filter(l -> l.getSellerId().equals(sellerId))
                    .toList();
        }

        @Override
        public List<Listing> findActiveBySellers(List<String> sellerIds) {
            return store.stream()
                    .filter(Listing::isActive)
                    .filter(l -> sellerIds.contains(l.getSellerId()))
                    .toList();
        }

        @Override
        public List<Listing> findByIds(List<String> ids) {
            return store.stream().filter(l -> ids.contains(l.getId())).toList();
        }
    }

    private static final class SequentialIdGenerator implements ListingIdGeneratorPort {
        private int counter = 0;

        @Override
        public String newListingId() {
            return "listing-" + (++counter);
        }
    }
}
