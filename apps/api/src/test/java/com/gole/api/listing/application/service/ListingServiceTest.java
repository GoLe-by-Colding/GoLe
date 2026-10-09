package com.gole.api.listing.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.gole.api.common.exception.ConflictException;
import com.gole.api.common.exception.ForbiddenException;
import com.gole.api.listing.adapter.out.media.MediaListingPhotoAdapter;
import com.gole.api.listing.application.port.in.CreateListingUseCase.CreateListingCommand;
import com.gole.api.listing.application.port.in.ReviseListingUseCase.ReviseListingCommand;
import com.gole.api.listing.application.port.in.ReviseListingUseCase.RevisionResult;
import com.gole.api.listing.application.port.out.InterestTagListingNotifierPort;
import com.gole.api.listing.application.port.out.ListingBidMatchNotifierPort;
import com.gole.api.listing.application.port.out.ListingIdGeneratorPort;
import com.gole.api.listing.application.port.out.ListingPriceDropNotifierPort;
import com.gole.api.listing.application.port.out.ListingRepositoryPort;
import com.gole.api.listing.application.port.out.NewListingNotifierPort;
import com.gole.api.listing.application.query.ListingSearchQuery;
import com.gole.api.listing.domain.exception.InvalidPriceException;
import com.gole.api.listing.domain.exception.ListingBumpCooldownException;
import com.gole.api.listing.domain.exception.ListingNotFoundException;
import com.gole.api.listing.domain.exception.ListingStateException;
import com.gole.api.listing.domain.exception.MissingPhotoException;
import com.gole.api.listing.domain.model.Completeness;
import com.gole.api.listing.domain.model.ConditionDisclosure;
import com.gole.api.listing.domain.model.InterestTag;
import com.gole.api.listing.domain.model.ItemCondition;
import com.gole.api.listing.domain.model.Listing;
import com.gole.api.listing.domain.model.ListingCategory;
import com.gole.api.listing.domain.model.ListingStatus;
import com.gole.api.media.application.port.in.ManageMediaAssetsUseCase;
import com.gole.api.media.domain.model.MediaTargetType;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ListingServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final Duration BUMP_COOLDOWN = Duration.ofHours(24);

    private InMemoryListingRepository repository;
    private RecordingNewListingNotifier notifier;
    private RecordingInterestTagListingNotifier interestTagNotifier;
    private RecordingPriceDropNotifier priceDropNotifier;
    private RecordingBidMatchNotifier bidMatchNotifier;
    private ManageMediaAssetsUseCase mediaAssets;
    private MutableClock clock;
    private ListingService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryListingRepository();
        notifier = new RecordingNewListingNotifier();
        interestTagNotifier = new RecordingInterestTagListingNotifier();
        priceDropNotifier = new RecordingPriceDropNotifier();
        bidMatchNotifier = new RecordingBidMatchNotifier();
        mediaAssets = mock(ManageMediaAssetsUseCase.class);
        clock = new MutableClock(NOW);
        service = new ListingService(
                repository,
                new SequentialIdGenerator(),
                notifier,
                interestTagNotifier,
                priceDropNotifier,
                bidMatchNotifier,
                new MediaListingPhotoAdapter(mediaAssets),
                clock,
                BUMP_COOLDOWN);
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
        assertThat(notifier.setWatcherNotices).containsExactly(new SetWatcherNotice("seller-1", id, "10307"));
        assertThat(interestTagNotifier.notifications).isEmpty();
    }

    @Test
    @DisplayName("최근 판매 중 매물을 상한까지만 낸다")
    void newestActive_appliesLimit() {
        service.create(validCommand());
        service.create(validCommand());
        service.create(validCommand());

        assertThat(service.newestActive(2)).hasSize(2).allMatch(Listing::isActive);
        assertThat(service.newestActive(0)).isEmpty();
    }

    @Test
    void create_notifiesInterestTagSubscribersWhenThemeIsSpecified() {
        CreateListingCommand command = new CreateListingCommand(
                "seller-1",
                "테크닉 매물",
                "설명",
                280_000,
                ItemCondition.NEW_SEALED,
                ConditionDisclosure.basic(),
                List.of("photo-1.jpg"),
                "42143",
                ListingCategory.SET,
                InterestTag.TECHNIC);

        String id = service.create(command);

        assertThat(interestTagNotifier.notifications)
                .containsExactly(new InterestTagListingNotice("seller-1", id, "테크닉 매물", InterestTag.TECHNIC));
    }

    @Test
    void create_succeedsWhenInterestTagNotifierFails() {
        interestTagNotifier.failure = new IllegalStateException("notification unavailable");
        CreateListingCommand command = new CreateListingCommand(
                "seller-1",
                "테크닉 매물",
                "설명",
                280_000,
                ItemCondition.NEW_SEALED,
                ConditionDisclosure.basic(),
                List.of("photo-1.jpg"),
                "42143",
                ListingCategory.SET,
                InterestTag.TECHNIC);

        String id = service.create(command);

        assertThat(service.getById(id).getInterestTag()).isEqualTo(InterestTag.TECHNIC);
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

    // ── 수정 (listing-edit-and-bump E1~E9) ─────────────────────────────────────

    private ReviseListingCommand revision(String listingId, String sellerId, long price) {
        return revision(listingId, sellerId, price, null);
    }

    private ReviseListingCommand revision(String listingId, String sellerId, long price, InterestTag interestTag) {
        return new ReviseListingCommand(
                listingId,
                sellerId,
                "에펠탑 10307 (가격 조정)",
                "미개봉, 박스 모서리 눌림",
                price,
                ItemCondition.LIKE_NEW,
                new ConditionDisclosure(Completeness.FULL_BOX, true, true, false, "", "박스 모서리 눌림"),
                List.of("photo-1.jpg", "photo-9.jpg"),
                interestTag);
    }

    @Test
    void revise_replacesEditableFieldsAndKeepsSetCategoryAndListedAt() {
        String id = service.create(validCommand());

        RevisionResult result = service.revise(revision(id, "seller-1", 280_000));

        Listing revised = service.getById(id);
        assertThat(revised.getTitle()).isEqualTo("에펠탑 10307 (가격 조정)");
        assertThat(revised.getDescription()).isEqualTo("미개봉, 박스 모서리 눌림");
        assertThat(revised.getCondition()).isEqualTo(ItemCondition.LIKE_NEW);
        assertThat(revised.getDisclosure().completeness()).isEqualTo(Completeness.FULL_BOX);
        assertThat(revised.getPhotoUrls()).containsExactly("photo-1.jpg", "photo-9.jpg");
        assertThat(revised.getCatalogSetNumber()).isEqualTo("10307");
        assertThat(revised.getCategory()).isEqualTo(ListingCategory.SET);
        assertThat(revised.getListedAt()).isEqualTo(NOW);
        assertThat(result.priceDropped()).isFalse();
        assertThat(result.oldPrice()).isEqualTo(280_000);
        verify(mediaAssets)
                .replaceReferences(
                        "seller-1", MediaTargetType.LISTING, id, List.of("photo-1.jpg", "photo-9.jpg"), true);
    }

    @Test
    void revise_rejectsNonOwnerWith403BeforeTouchingMediaOrStore() {
        String id = service.create(validCommand());

        assertThatThrownBy(() -> service.revise(revision(id, "intruder", 1_000)))
                .isInstanceOfSatisfying(
                        ForbiddenException.class,
                        error -> assertThat(error.getCode()).isEqualTo("LISTING_ACCESS_DENIED"));
        assertThat(service.getById(id).getPrice().amount()).isEqualTo(280_000);
        assertThat(repository.updateCalls).isZero();
        verify(mediaAssets, never())
                .replaceReferences(any(), any(), eq(id), eq(List.of("photo-1.jpg", "photo-9.jpg")), anyBoolean());
    }

    @Test
    void revise_rejectsReservedListingAsOrderInProgress() {
        repository.save(listingWithStatus("reserved-1", ListingStatus.RESERVED));

        assertThatThrownBy(() -> service.revise(revision("reserved-1", "seller-1", 1_000)))
                .isInstanceOfSatisfying(
                        ConflictException.class,
                        error -> assertThat(error.getCode()).isEqualTo("LISTING_ORDER_IN_PROGRESS"));
        assertThat(repository.updateCalls).isZero();
    }

    @Test
    void revise_rejectsSoldAndDeletedListingsAsNotEditable() {
        repository.save(listingWithStatus("sold-1", ListingStatus.SOLD));
        repository.save(listingWithStatus("deleted-1", ListingStatus.DELETED));

        assertThatThrownBy(() -> service.revise(revision("sold-1", "seller-1", 1_000)))
                .isInstanceOfSatisfying(
                        ConflictException.class,
                        error -> assertThat(error.getCode()).isEqualTo("LISTING_NOT_EDITABLE"));
        assertThatThrownBy(() -> service.revise(revision("deleted-1", "seller-1", 1_000)))
                .isInstanceOfSatisfying(
                        ConflictException.class,
                        error -> assertThat(error.getCode()).isEqualTo("LISTING_NOT_EDITABLE"));
    }

    @Test
    void revise_losesToReservationTakenBetweenReadAndWrite() {
        String id = service.create(validCommand());
        repository.raceTo = ListingStatus.RESERVED;

        assertThatThrownBy(() -> service.revise(revision(id, "seller-1", 200_000)))
                .isInstanceOfSatisfying(
                        ConflictException.class,
                        error -> assertThat(error.getCode()).isEqualTo("LISTING_ORDER_IN_PROGRESS"));

        // 예약을 덮어쓰지 않고, 지는 쪽의 가격 인하도 알리지 않는다. (E4)
        Listing stored = service.getById(id);
        assertThat(stored.getStatus()).isEqualTo(ListingStatus.RESERVED);
        assertThat(stored.getPrice().amount()).isEqualTo(280_000);
        assertThat(priceDropNotifier.notices).isEmpty();
    }

    @Test
    void revise_reportsOrderInProgressWhenRaceAlreadyResolvedBackToActive() {
        String id = service.create(validCommand());
        repository.raceTo = ListingStatus.ACTIVE; // 예약됐다가 곧바로 풀린 경우 — 갱신은 이미 놓쳤다

        assertThatThrownBy(() -> service.revise(revision(id, "seller-1", 200_000)))
                .isInstanceOfSatisfying(
                        ConflictException.class,
                        error -> assertThat(error.getCode()).isEqualTo("LISTING_ORDER_IN_PROGRESS"));
    }

    @Test
    void revise_priceDropNotifiesWishersWithOldAndNewPrice() {
        String id = service.create(validCommand());

        RevisionResult result = service.revise(revision(id, "seller-1", 250_000));

        assertThat(result.priceDropped()).isTrue();
        assertThat(result.oldPrice()).isEqualTo(280_000);
        assertThat(result.listing().getPreviousPrice().amount()).isEqualTo(280_000);
        assertThat(result.listing().getPriceChangedAt()).isEqualTo(NOW);
        assertThat(priceDropNotifier.notices)
                .containsExactly(new PriceDropNotice(id, "seller-1", "에펠탑 10307 (가격 조정)", 280_000, 250_000));
        assertThat(service.getById(id).getPreviousPrice().amount()).isEqualTo(280_000);
    }

    @Test
    void create_withSetNotifiesMatchingBiddersWithConditionAndPrice() {
        String id = service.create(validCommand());

        assertThat(bidMatchNotifier.notices)
                .containsExactly(new BidMatchNotice(id, "seller-1", "에펠탑 10307", "10307", "new_sealed", 280_000));
    }

    @Test
    void create_withoutSetDoesNotLookForBids() {
        service.create(new CreateListingCommand(
                "seller-1",
                "브릭 벌크",
                "섞인 부품",
                30_000,
                ItemCondition.USED_GOOD,
                ConditionDisclosure.basic(),
                List.of("photo-1.jpg"),
                null));

        assertThat(bidMatchNotifier.notices).isEmpty();
    }

    @Test
    void create_minifigWithSourceSetNotifiesSetWatchersButNotSetBidders() {
        String id = service.create(minifigCommand());

        // 세트 입찰은 세트 한 벌을 사려는 것이다 — 출처 세트 번호를 단 미니피규어로 입찰자를 부르지 않는다.
        assertThat(bidMatchNotifier.notices).isEmpty();
        assertThat(notifier.setWatcherNotices).containsExactly(new SetWatcherNotice("seller-1", id, "75192"));
    }

    @Test
    void revise_minifigPriceDropDoesNotNotifySetBidders() {
        String id = service.create(minifigCommand());

        service.revise(revision(id, "seller-1", 20_000));

        assertThat(bidMatchNotifier.notices).isEmpty();
    }

    private CreateListingCommand minifigCommand() {
        return new CreateListingCommand(
                "seller-1",
                "한 솔로 미니피규어",
                "75192 구성품",
                30_000,
                ItemCondition.USED_GOOD,
                ConditionDisclosure.basic(),
                List.of("photo-1.jpg"),
                "75192",
                ListingCategory.MINIFIG);
    }

    @Test
    void create_succeedsWhenBidMatchNotifierFails() {
        bidMatchNotifier.failure = new IllegalStateException("bid lookup unavailable");

        String id = service.create(validCommand());

        assertThat(service.getById(id).isActive()).isTrue();
    }

    @Test
    void revise_priceDropNotifiesMatchingBiddersAtNewPrice_butRaiseDoesNot() {
        String id = service.create(validCommand());
        bidMatchNotifier.notices.clear();

        service.revise(revision(id, "seller-1", 250_000));
        service.revise(revision(id, "seller-1", 260_000));

        assertThat(bidMatchNotifier.notices)
                .containsExactly(
                        // 수정 후 상태(like_new) 기준으로 찾는다 — 입찰은 세트·상태별이다.
                        new BidMatchNotice(id, "seller-1", "에펠탑 10307 (가격 조정)", "10307", "like_new", 250_000));
    }

    @Test
    void revise_priceRaiseClearsPreviousPriceAndDoesNotNotify() {
        String id = service.create(validCommand());
        service.revise(revision(id, "seller-1", 250_000));
        priceDropNotifier.notices.clear();

        RevisionResult result = service.revise(revision(id, "seller-1", 260_000));

        assertThat(result.priceDropped()).isFalse();
        assertThat(result.oldPrice()).isEqualTo(250_000);
        assertThat(service.getById(id).getPreviousPrice()).isNull();
        assertThat(priceDropNotifier.notices).isEmpty();
    }

    @Test
    void revise_samePriceKeepsHistoryAndDoesNotNotify() {
        String id = service.create(validCommand());
        service.revise(revision(id, "seller-1", 250_000));
        priceDropNotifier.notices.clear();
        clock.advance(Duration.ofHours(1));

        service.revise(revision(id, "seller-1", 250_000));

        Listing stored = service.getById(id);
        assertThat(stored.getPreviousPrice().amount()).isEqualTo(280_000);
        assertThat(stored.getPriceChangedAt()).isEqualTo(NOW);
        assertThat(priceDropNotifier.notices).isEmpty();
    }

    @Test
    void revise_doesNotResendNewListingOrInterestTagNotices() {
        String id = service.create(new CreateListingCommand(
                "seller-1",
                "테크닉 매물",
                "설명",
                280_000,
                ItemCondition.NEW_SEALED,
                ConditionDisclosure.basic(),
                List.of("photo-1.jpg"),
                "42143",
                ListingCategory.SET,
                InterestTag.TECHNIC));

        service.revise(revision(id, "seller-1", 280_000, InterestTag.STAR_WARS));

        // 수정은 새 매물이 아니다 — 팔로워·관심 세트·관심 테마 알림은 등록 때 한 번뿐이다. (E7)
        assertThat(service.getById(id).getInterestTag()).isEqualTo(InterestTag.STAR_WARS);
        assertThat(interestTagNotifier.notifications).hasSize(1);
        assertThat(notifier.notifications).hasSize(1);
        assertThat(notifier.setWatcherNotices).hasSize(1);
    }

    @Test
    void revise_succeedsWhenPriceDropNotifierFails() {
        String id = service.create(validCommand());
        priceDropNotifier.failure = new IllegalStateException("notification unavailable");

        RevisionResult result = service.revise(revision(id, "seller-1", 100_000));

        assertThat(result.priceDropped()).isTrue();
        assertThat(service.getById(id).getPrice().amount()).isEqualTo(100_000);
    }

    // ── 끌올 (listing-edit-and-bump B1~B5) ─────────────────────────────────────

    @Test
    void bump_rejectsRightAfterCreationWithRemainingCooldown() {
        String id = service.create(validCommand());
        clock.advance(Duration.ofHours(23));

        assertThatThrownBy(() -> service.bump(id, "seller-1"))
                .isInstanceOfSatisfying(ListingBumpCooldownException.class, error -> {
                    assertThat(error.getCode()).isEqualTo("LISTING_BUMP_COOLDOWN");
                    assertThat(error.getRetryAfter()).isEqualTo(Duration.ofHours(1));
                });
        assertThat(repository.updateCalls).isZero();
    }

    @Test
    void bump_movesListingToTopOfNewestEverywhere() {
        String older = service.create(validCommand());
        clock.advance(Duration.ofHours(1));
        String newer = service.create(validCommand());
        assertThat(service.search(ListingSearchQuery.newestAll()))
                .extracting(Listing::getId)
                .containsExactly(newer, older);

        clock.advance(Duration.ofHours(23));
        Instant bumpedAt = NOW.plus(Duration.ofHours(24));
        Listing bumped = service.bump(older, "seller-1");

        assertThat(bumped.getListedAt()).isEqualTo(bumpedAt);
        assertThat(bumped.getBumpedAt()).isEqualTo(bumpedAt);
        assertThat(bumped.bumpAvailableAt(service.bumpCooldown())).isEqualTo(bumpedAt.plus(BUMP_COOLDOWN));
        assertThat(service.search(ListingSearchQuery.newestAll()))
                .extracting(Listing::getId)
                .containsExactly(older, newer);
        assertThat(service.bySeller("seller-1")).extracting(Listing::getId).containsExactly(older, newer);
        assertThat(service.activeBySeller("seller-1"))
                .extracting(Listing::getId)
                .containsExactly(older, newer);
    }

    @Test
    void bump_cooldownRestartsFromLastBump() {
        String id = service.create(validCommand());
        clock.advance(BUMP_COOLDOWN);
        service.bump(id, "seller-1");
        clock.advance(Duration.ofHours(2));

        assertThatThrownBy(() -> service.bump(id, "seller-1"))
                .isInstanceOfSatisfying(
                        ListingBumpCooldownException.class,
                        error -> assertThat(error.getRetryAfter()).isEqualTo(Duration.ofHours(22)));
    }

    @Test
    void bump_doesNotNotifyAnyone() {
        String id = service.create(validCommand());
        clock.advance(BUMP_COOLDOWN);

        service.bump(id, "seller-1");

        assertThat(notifier.notifications).hasSize(1); // 등록 때 한 번
        assertThat(notifier.setWatcherNotices).hasSize(1);
        assertThat(priceDropNotifier.notices).isEmpty();
    }

    @Test
    void bump_rejectsNonOwnerWith403() {
        String id = service.create(validCommand());
        clock.advance(BUMP_COOLDOWN);

        assertThatThrownBy(() -> service.bump(id, "intruder"))
                .isInstanceOfSatisfying(
                        ForbiddenException.class,
                        error -> assertThat(error.getCode()).isEqualTo("LISTING_ACCESS_DENIED"));
    }

    @Test
    void bump_rejectsReservedAsOrderInProgressAndOtherInactiveAsNotBumpable() {
        repository.save(listingWithStatus("reserved-1", ListingStatus.RESERVED));
        repository.save(listingWithStatus("sold-1", ListingStatus.SOLD));
        clock.advance(BUMP_COOLDOWN);

        assertThatThrownBy(() -> service.bump("reserved-1", "seller-1"))
                .isInstanceOfSatisfying(
                        ConflictException.class,
                        error -> assertThat(error.getCode()).isEqualTo("LISTING_ORDER_IN_PROGRESS"));
        assertThatThrownBy(() -> service.bump("sold-1", "seller-1"))
                .isInstanceOfSatisfying(
                        ConflictException.class,
                        error -> assertThat(error.getCode()).isEqualTo("LISTING_NOT_BUMPABLE"));
    }

    @Test
    void bump_losesToReservationTakenBetweenReadAndWrite() {
        String id = service.create(validCommand());
        clock.advance(BUMP_COOLDOWN);
        repository.raceTo = ListingStatus.RESERVED;

        assertThatThrownBy(() -> service.bump(id, "seller-1"))
                .isInstanceOfSatisfying(
                        ConflictException.class,
                        error -> assertThat(error.getCode()).isEqualTo("LISTING_ORDER_IN_PROGRESS"));
        assertThat(service.getById(id).getListedAt()).isEqualTo(NOW);
    }

    private static Listing copyWithStatus(Listing l, ListingStatus status) {
        return new Listing(
                l.getId(),
                l.getSellerId(),
                l.getTitle(),
                l.getDescription(),
                l.getPrice(),
                l.getCondition(),
                l.getDisclosure(),
                l.getPhotoUrls(),
                l.getCatalogSetNumber(),
                l.getCategory(),
                l.getInterestTag(),
                status,
                l.getCreatedAt(),
                l.getListedAt(),
                l.getBumpedAt(),
                l.getPreviousPrice(),
                l.getPriceChangedAt());
    }

    private static Listing copyWithTimeline(Listing l, Instant bumpedAt) {
        return new Listing(
                l.getId(),
                l.getSellerId(),
                l.getTitle(),
                l.getDescription(),
                l.getPrice(),
                l.getCondition(),
                l.getDisclosure(),
                l.getPhotoUrls(),
                l.getCatalogSetNumber(),
                l.getCategory(),
                l.getInterestTag(),
                l.getStatus(),
                l.getCreatedAt(),
                bumpedAt,
                bumpedAt,
                l.getPreviousPrice(),
                l.getPriceChangedAt());
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
        /** 실제 어댑터와 같은 "최신순" — listedAt 내림차순. (B5) */
        private static final Comparator<Listing> NEWEST =
                Comparator.comparing(Listing::getListedAt).reversed();

        private final List<Listing> store = new ArrayList<>();

        // 실제 저장소처럼 넣고 꺼낼 때 복사한다. 같은 객체를 돌려주면 서비스가 메모리에서 바꾼 값이
        // 원자 갱신 전에 이미 "저장된" 것처럼 보여, 경합에서 지는 경로를 검증할 수 없다.
        @Override
        public Listing save(Listing listing) {
            store.removeIf(l -> l.getId().equals(listing.getId()));
            store.add(copyWithStatus(listing, listing.getStatus()));
            return listing;
        }

        @Override
        public Optional<Listing> findById(String listingId) {
            return store.stream()
                    .filter(l -> l.getId().equals(listingId))
                    .findFirst()
                    .map(l -> copyWithStatus(l, l.getStatus()));
        }

        @Override
        public List<Listing> search(ListingSearchQuery query) {
            return store.stream()
                    .filter(Listing::isActive)
                    .filter(l -> query.setNumber() == null || query.setNumber().equals(l.getCatalogSetNumber()))
                    .sorted(NEWEST)
                    .toList();
        }

        @Override
        public Optional<Listing> reserveIfActive(String listingId) {
            return findById(listingId).filter(Listing::isActive);
        }

        @Override
        public boolean markSoldIfActive(String listingId) {
            Optional<Listing> listing = findById(listingId).filter(Listing::isActive);
            listing.ifPresent(found -> {
                found.markSold();
                save(found);
            });
            return listing.isPresent();
        }

        /** 다음 원자 갱신을 "그 사이 주문이 예약을 잡았다"로 만든다. 경합을 결정적으로 재현한다. */
        private ListingStatus raceTo;

        int updateCalls;

        @Override
        public boolean updateIfActive(Listing listing) {
            updateCalls++;
            if (applyRace(listing.getId())) {
                return false;
            }
            Optional<Listing> stored = findById(listing.getId()).filter(Listing::isActive);
            stored.ifPresent(ignored -> save(listing));
            return stored.isPresent();
        }

        @Override
        public boolean bumpIfActive(String listingId, Instant bumpedAt) {
            updateCalls++;
            if (applyRace(listingId)) {
                return false;
            }
            Optional<Listing> stored = findById(listingId).filter(Listing::isActive);
            stored.ifPresent(listing -> save(copyWithTimeline(listing, bumpedAt)));
            return stored.isPresent();
        }

        private boolean applyRace(String listingId) {
            if (raceTo == null) {
                return false;
            }
            Listing current = findById(listingId).orElseThrow();
            save(copyWithStatus(current, raceTo));
            raceTo = null;
            return true;
        }

        @Override
        public List<Listing> findActiveBySeller(String sellerId) {
            return store.stream()
                    .filter(Listing::isActive)
                    .filter(l -> l.getSellerId().equals(sellerId))
                    .sorted(NEWEST)
                    .toList();
        }

        @Override
        public List<Listing> findBySeller(String sellerId) {
            return store.stream()
                    .filter(l -> l.getSellerId().equals(sellerId))
                    .filter(l -> l.getStatus() != ListingStatus.DELETED)
                    .sorted(NEWEST)
                    .toList();
        }

        @Override
        public List<Listing> findActiveBySellers(List<String> sellerIds, int limit) {
            return store.stream()
                    .filter(Listing::isActive)
                    .filter(l -> sellerIds.contains(l.getSellerId()))
                    .sorted(NEWEST)
                    .limit(limit)
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

    private record SetWatcherNotice(String sellerId, String listingId, String setNumber) {}

    private record InterestTagListingNotice(String sellerId, String listingId, String title, InterestTag interestTag) {}

    private static final class RecordingNewListingNotifier implements NewListingNotifierPort {
        private final List<NewListingNotice> notifications = new ArrayList<>();

        @Override
        public void notifyFollowers(String sellerId, String listingId, String title) {
            notifications.add(new NewListingNotice(sellerId, listingId, title));
        }

        private final List<SetWatcherNotice> setWatcherNotices = new ArrayList<>();

        @Override
        public void notifySetWatchers(String sellerId, String listingId, String title, String setNumber) {
            setWatcherNotices.add(new SetWatcherNotice(sellerId, listingId, setNumber));
        }
    }

    private record PriceDropNotice(String listingId, String sellerId, String title, long oldPrice, long newPrice) {}

    private static final class RecordingPriceDropNotifier implements ListingPriceDropNotifierPort {
        private final List<PriceDropNotice> notices = new ArrayList<>();
        private RuntimeException failure;

        @Override
        public void priceDropped(String listingId, String sellerId, String title, long oldPrice, long newPrice) {
            if (failure != null) {
                throw failure;
            }
            notices.add(new PriceDropNotice(listingId, sellerId, title, oldPrice, newPrice));
        }
    }

    private record BidMatchNotice(
            String listingId, String sellerId, String title, String setNumber, String conditionKey, long price) {}

    private static final class RecordingBidMatchNotifier implements ListingBidMatchNotifierPort {
        private final List<BidMatchNotice> notices = new ArrayList<>();
        private RuntimeException failure;

        @Override
        public void listingAvailable(
                String listingId, String sellerId, String title, String setNumber, String conditionKey, long price) {
            if (failure != null) {
                throw failure;
            }
            notices.add(new BidMatchNotice(listingId, sellerId, title, setNumber, conditionKey, price));
        }
    }

    /** 쿨다운 경계를 재현하려고 시간을 앞으로 돌릴 수 있는 시계. */
    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private static final class RecordingInterestTagListingNotifier implements InterestTagListingNotifierPort {
        private final List<InterestTagListingNotice> notifications = new ArrayList<>();
        private RuntimeException failure;

        @Override
        public void notifyInterestTagSubscribers(String sellerId, String listingId, String title, InterestTag tag) {
            if (failure != null) {
                throw failure;
            }
            notifications.add(new InterestTagListingNotice(sellerId, listingId, title, tag));
        }
    }
}
