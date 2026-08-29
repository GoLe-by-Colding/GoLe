package com.gole.api.listing.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gole.api.listing.application.port.in.CreateListingUseCase.CreateListingCommand;
import com.gole.api.listing.application.port.out.ListingIdGeneratorPort;
import com.gole.api.listing.application.port.out.ListingRepositoryPort;
import com.gole.api.listing.application.port.out.NewListingNotifierPort;
import com.gole.api.listing.application.query.ListingSearchQuery;
import com.gole.api.listing.domain.exception.InvalidPriceException;
import com.gole.api.listing.domain.exception.ListingNotFoundException;
import com.gole.api.listing.domain.exception.ListingStateException;
import com.gole.api.listing.domain.exception.MissingPhotoException;
import com.gole.api.listing.domain.model.ConditionDisclosure;
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
    private RecordingNewListingNotifier notifier;
    private ListingService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryListingRepository();
        notifier = new RecordingNewListingNotifier();
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        service = new ListingService(repository, new SequentialIdGenerator(), notifier, clock);
    }

    private CreateListingCommand validCommand() {
        return new CreateListingCommand(
                "seller-1",
                "에펠탑 10307",
                "미개봉",
                280_000,
                ItemCondition.NEW_SEALED,
                ConditionDisclosure.basic(),
                List.of("photo-1.jpg"),
                "10307");
    }

    @Test
    void create_persistsActiveListing() {
        String id = service.create(validCommand());
        Listing saved = service.getById(id);
        assertThat(saved.isActive()).isTrue();
        assertThat(saved.getPrice().amount()).isEqualTo(280_000);
        assertThat(notifier.notifications).containsExactly(new NewListingNotice("seller-1", id, "에펠탑 10307"));
    }

    @Test
    void create_rejectsMissingPhoto() {
        CreateListingCommand cmd = new CreateListingCommand(
                "seller-1",
                "title",
                "desc",
                1000,
                ItemCondition.NEW_SEALED,
                ConditionDisclosure.basic(),
                List.of(),
                "10307");
        assertThatThrownBy(() -> service.create(cmd)).isInstanceOf(MissingPhotoException.class);
    }

    @Test
    void create_rejectsNegativePrice() {
        CreateListingCommand cmd = new CreateListingCommand(
                "seller-1",
                "title",
                "desc",
                -1,
                ItemCondition.NEW_SEALED,
                ConditionDisclosure.basic(),
                List.of("p.jpg"),
                null);
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
    void getPublicById_hidesDeletedListingWhileInternalLookupStillFindsIt() {
        String id = service.create(validCommand());
        service.delete(id);

        assertThatThrownBy(() -> service.getPublicById(id)).isInstanceOf(ListingNotFoundException.class);
        assertThat(service.getById(id).getStatus()).isEqualTo(ListingStatus.DELETED);
    }

    @Test
    void getPublicById_keepsActiveReservedAndSoldListingsVisible() {
        String activeId = service.create(validCommand());
        Listing reserved = listingWithStatus("reserved-1", ListingStatus.RESERVED);
        Listing sold = listingWithStatus("sold-1", ListingStatus.SOLD);
        repository.save(reserved);
        repository.save(sold);

        assertThat(service.getPublicById(activeId).getStatus()).isEqualTo(ListingStatus.ACTIVE);
        assertThat(service.getPublicById("reserved-1").getStatus()).isEqualTo(ListingStatus.RESERVED);
        assertThat(service.getPublicById("sold-1").getStatus()).isEqualTo(ListingStatus.SOLD);
    }

    @Test
    void delete_rejectedWhenReserved() {
        Listing reserved = new Listing(
                "r1",
                "seller-1",
                "t",
                "d",
                com.gole.api.listing.domain.model.Money.won(1000),
                ItemCondition.NEW_SEALED,
                ConditionDisclosure.basic(),
                List.of("p.jpg"),
                null,
                com.gole.api.listing.domain.model.ListingCategory.SET,
                ListingStatus.RESERVED,
                Instant.parse("2026-01-01T00:00:00Z"));
        repository.save(reserved);
        assertThatThrownBy(() -> service.delete("r1")).isInstanceOf(ListingStateException.class);
    }

    @Test
    void search_bySetNumber_returnsOnlyThatSet() {
        service.create(validCommand()); // catalogSetNumber = 10307
        service.create(new CreateListingCommand(
                "seller-2",
                "밀레니엄 팰컨 75192",
                "중고",
                900_000,
                ItemCondition.USED_GOOD,
                ConditionDisclosure.basic(),
                List.of("photo-2.jpg"),
                "75192"));

        List<Listing> found = service.search(ListingSearchQuery.forSet("10307"));

        assertThat(found).hasSize(1);
        assertThat(found.getFirst().getCatalogSetNumber()).isEqualTo("10307");
    }

    @Test
    void search_withoutSetNumber_returnsAllActive() {
        service.create(validCommand());
        service.create(new CreateListingCommand(
                "seller-2",
                "밀레니엄 팰컨 75192",
                "중고",
                900_000,
                ItemCondition.USED_GOOD,
                ConditionDisclosure.basic(),
                List.of("photo-2.jpg"),
                "75192"));

        assertThat(service.search(ListingSearchQuery.newestAll())).hasSize(2);
    }

    /** 빈 문자열 setNumber는 "필터 없음"으로 정규화된다(쿼리스트링 `?setNumber=` 대응). */
    @Test
    void searchQuery_blankSetNumber_normalizesToNoFilter() {
        assertThat(ListingSearchQuery.forSet("  ").setNumber()).isNull();
        assertThat(new ListingSearchQuery(null, null, null, null, null, null, "").setNumber())
                .isNull();
    }

    @Test
    void bySeller_판매완료까지_포함하고_활성만_주는_조회와_구분된다() {
        String sold = service.create(validCommand());
        service.create(validCommand());
        service.markSold(sold);

        // "내 매물"은 판매완료도 보여야 한다 — 팔린 매물이 목록에서 사라지면 판매자는
        // 자기가 뭘 팔았는지 확인할 데가 없다.
        assertThat(service.bySeller("seller-1")).hasSize(2);
        assertThat(service.activeBySeller("seller-1")).hasSize(1);
    }

    @Test
    void bySeller_삭제한_매물은_빠진다() {
        String removed = service.create(validCommand());
        service.create(validCommand());
        service.delete(removed);

        // 본인이 내린 매물이 계속 남으면 목록에 쓰레기만 쌓인다.
        assertThat(service.bySeller("seller-1")).hasSize(1);
    }

    @Test
    void bySeller_다른_셀러의_매물은_섞이지_않는다() {
        service.create(validCommand());
        service.create(new CreateListingCommand(
                "seller-2",
                "밀레니엄 팰컨 75192",
                "미개봉",
                900_000,
                ItemCondition.NEW_SEALED,
                ConditionDisclosure.basic(),
                List.of("photo-2.jpg"),
                "75192"));

        assertThat(service.bySeller("seller-1")).hasSize(1);
        assertThat(service.bySeller("seller-1").getFirst().getSellerId()).isEqualTo("seller-1");
    }

    private static Listing listingWithStatus(String id, ListingStatus status) {
        return new Listing(
                id,
                "seller-1",
                "에펠탑 10307",
                "미개봉",
                com.gole.api.listing.domain.model.Money.won(280_000),
                ItemCondition.NEW_SEALED,
                ConditionDisclosure.basic(),
                List.of("photo-1.jpg"),
                "10307",
                com.gole.api.listing.domain.model.ListingCategory.SET,
                status,
                Instant.parse("2026-01-01T00:00:00Z"));
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
            return store.stream()
                    .filter(Listing::isActive)
                    .filter(l -> query.setNumber() == null || query.setNumber().equals(l.getCatalogSetNumber()))
                    .toList();
        }

        @Override
        public Optional<Listing> reserveIfActive(String listingId) {
            return findById(listingId).filter(Listing::isActive);
        }

        @Override
        public boolean markSoldIfActive(String listingId) {
            Optional<Listing> listing = findById(listingId).filter(Listing::isActive);
            listing.ifPresent(Listing::markSold);
            return listing.isPresent();
        }

        @Override
        public List<Listing> findActiveBySeller(String sellerId) {
            return store.stream()
                    .filter(Listing::isActive)
                    .filter(l -> l.getSellerId().equals(sellerId))
                    .toList();
        }

        @Override
        public List<Listing> findBySeller(String sellerId) {
            return store.stream()
                    .filter(l -> l.getSellerId().equals(sellerId))
                    .filter(l -> l.getStatus() != ListingStatus.DELETED)
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

    private record NewListingNotice(String sellerId, String listingId, String title) {}

    private static final class RecordingNewListingNotifier implements NewListingNotifierPort {
        private final List<NewListingNotice> notifications = new ArrayList<>();

        @Override
        public void notifyFollowers(String sellerId, String listingId, String title) {
            notifications.add(new NewListingNotice(sellerId, listingId, title));
        }
    }
}
