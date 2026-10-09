package com.gole.api.listing.application.service;

import com.gole.api.common.exception.ForbiddenException;
import com.gole.api.listing.application.port.in.BrowseListingsUseCase;
import com.gole.api.listing.application.port.in.BumpListingUseCase;
import com.gole.api.listing.application.port.in.CreateListingUseCase;
import com.gole.api.listing.application.port.in.DeleteListingUseCase;
import com.gole.api.listing.application.port.in.GetListingUseCase;
import com.gole.api.listing.application.port.in.MarkListingSoldUseCase;
import com.gole.api.listing.application.port.in.ModerateListingUseCase;
import com.gole.api.listing.application.port.in.ReleaseListingUseCase;
import com.gole.api.listing.application.port.in.ReserveListingUseCase;
import com.gole.api.listing.application.port.in.ReviseListingUseCase;
import com.gole.api.listing.application.port.in.SearchListingsUseCase;
import com.gole.api.listing.application.port.out.InterestTagListingNotifierPort;
import com.gole.api.listing.application.port.out.ListingBidMatchNotifierPort;
import com.gole.api.listing.application.port.out.ListingIdGeneratorPort;
import com.gole.api.listing.application.port.out.ListingPhotoPort;
import com.gole.api.listing.application.port.out.ListingPriceDropNotifierPort;
import com.gole.api.listing.application.port.out.ListingRepositoryPort;
import com.gole.api.listing.application.port.out.NewListingNotifierPort;
import com.gole.api.listing.application.query.ListingSearchQuery;
import com.gole.api.listing.domain.exception.ListingConflictException;
import com.gole.api.listing.domain.exception.ListingNotFoundException;
import com.gole.api.listing.domain.model.Listing;
import com.gole.api.listing.domain.model.ListingRevision;
import com.gole.api.listing.domain.model.Money;
import com.gole.api.listing.domain.model.PriceChange;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
                DeleteListingUseCase,
                ModerateListingUseCase,
                ReviseListingUseCase,
                BumpListingUseCase {

    private final ListingRepositoryPort listingRepository;
    private final ListingIdGeneratorPort idGenerator;
    private final NewListingNotifierPort newListingNotifier;
    private final InterestTagListingNotifierPort interestTagListingNotifier;
    private final ListingPriceDropNotifierPort priceDropNotifier;
    private final ListingBidMatchNotifierPort bidMatchNotifier;
    private final ListingPhotoPort photos;
    private final Clock clock;
    private final Duration bumpCooldown;

    public ListingService(
            ListingRepositoryPort listingRepository,
            ListingIdGeneratorPort idGenerator,
            NewListingNotifierPort newListingNotifier,
            InterestTagListingNotifierPort interestTagListingNotifier,
            ListingPriceDropNotifierPort priceDropNotifier,
            ListingBidMatchNotifierPort bidMatchNotifier,
            ListingPhotoPort photos,
            Clock clock,
            @Value("${gole.listing.bump.cooldown:PT24H}") Duration bumpCooldown) {
        if (bumpCooldown == null || bumpCooldown.isNegative()) {
            throw new IllegalArgumentException("gole.listing.bump.cooldown must not be negative");
        }
        this.listingRepository = listingRepository;
        this.idGenerator = idGenerator;
        this.newListingNotifier = newListingNotifier;
        this.interestTagListingNotifier = interestTagListingNotifier;
        this.priceDropNotifier = priceDropNotifier;
        this.bidMatchNotifier = bidMatchNotifier;
        this.photos = photos;
        this.clock = clock;
        this.bumpCooldown = bumpCooldown;
    }

    @Override
    @Transactional
    public String create(CreateListingCommand command) {
        String listingId = idGenerator.newListingId();
        Listing listing = Listing.create(
                listingId,
                command.sellerId(),
                command.title(),
                command.description(),
                Money.won(command.price()),
                command.condition(),
                command.disclosure(),
                command.photoKeys(),
                command.catalogSetNumber(),
                command.category(),
                command.interestTag(),
                Instant.now(clock));
        photos.replacePhotos(command.sellerId(), listingId, command.photoKeys());
        Listing saved = listingRepository.save(listing);
        newListingNotifier.notifyFollowers(saved.getSellerId(), saved.getId(), saved.getTitle());
        if (saved.getCatalogSetNumber() != null) {
            newListingNotifier.notifySetWatchers(
                    saved.getSellerId(), saved.getId(), saved.getTitle(), saved.getCatalogSetNumber());
        }
        if (saved.wholeSetNumber() != null) {
            notifyMatchingBids(saved);
        }
        if (saved.getInterestTag() != null) {
            notifyInterestTagSubscribers(saved);
        }
        return saved.getId();
    }

    private void notifyInterestTagSubscribers(Listing listing) {
        try {
            interestTagListingNotifier.notifyInterestTagSubscribers(
                    listing.getSellerId(), listing.getId(), listing.getTitle(), listing.getInterestTag());
        } catch (RuntimeException ignored) {
            // 알림 연계 장애가 매물 등록을 되돌리지 않게 출력 포트 경계에서 한 번 더 격리한다.
        }
    }

    /**
     * 판매자 수정. (E1~E9)
     *
     * <p>순서가 의미를 가진다. (1) 소유자·상태를 메모리에서 먼저 거른다. (2) 사진 참조를 교체한다 —
     * 잘못된 키는 여기서 400으로 끝난다. (3) {@code {_id, status: ACTIVE}} 조건으로 원자 갱신한다.
     * 그 사이 주문이 예약을 잡았으면 수정이 진다(E4). 트랜잭션이 (2)의 사진 교체까지 되돌린다.
     * (4) 커밋된 가격 인하만 후속 처리한다.
     *
     * <p>관심 테마를 바꿔도 알림톡·팔로워·관심 세트 알림을 다시 보내지 않는다(E7). 수정은 새 매물이 아니다.
     */
    @Override
    @Transactional
    public RevisionResult revise(ReviseListingCommand command) {
        Listing listing = requireOwnedBy(command.listingId(), command.sellerId());
        PriceChange priceChange = listing.revise(
                new ListingRevision(
                        command.title(),
                        command.description(),
                        Money.won(command.price()),
                        command.condition(),
                        command.disclosure(),
                        command.photoKeys(),
                        command.interestTag()),
                Instant.now(clock));
        photos.replacePhotos(command.sellerId(), listing.getId(), command.photoKeys());
        if (!listingRepository.updateIfActive(listing)) {
            throw lostRace(listing.getId(), Listing::requireEditable);
        }
        if (priceChange.dropped()) {
            onPriceDropped(listing, priceChange);
        }
        return new RevisionResult(
                listing, priceChange.dropped(), priceChange.before().amount());
    }

    /**
     * 가격 인하 후속 처리의 단일 지점. 지금은 찜한 사람 알림(E8)뿐이고, 인하에 반응해야 하는
     * 다음 연계(입찰 매칭 등)도 여기에 붙인다. 어느 것도 수정을 실패시키지 않는다(E9).
     */
    private void onPriceDropped(Listing listing, PriceChange priceChange) {
        try {
            priceDropNotifier.priceDropped(
                    listing.getId(),
                    listing.getSellerId(),
                    listing.getTitle(),
                    priceChange.before().amount(),
                    priceChange.after().amount());
        } catch (RuntimeException ignored) {
            // 어댑터가 이미 흡수하지만, 알림 연계 장애가 수정을 되돌리지 않게 포트 경계에서 한 번 더 격리한다.
        }
        if (listing.wholeSetNumber() != null) {
            notifyMatchingBids(listing);
        }
    }

    /**
     * 이 세트·상태에 매물가 이상으로 건 입찰자에게 알린다(buy-bids D8). 등록·인하 공통, 실패는 흡수한다.
     * 세트 한 벌 매물만 대상이다 — 출처 세트 번호를 단 미니피규어·부품으로 세트 입찰자를 부르지 않는다.
     */
    private void notifyMatchingBids(Listing listing) {
        try {
            bidMatchNotifier.listingAvailable(
                    listing.getId(),
                    listing.getSellerId(),
                    listing.getTitle(),
                    listing.wholeSetNumber(),
                    listing.getCondition().key(),
                    listing.getPrice().amount());
        } catch (RuntimeException ignored) {
            // 어댑터가 이미 흡수한다. 입찰 연계 장애가 매물 등록·수정을 되돌리지 않게 한 번 더 격리한다.
        }
    }

    /** 끌올. 정렬 키만 원자적으로 올리고 알림은 보내지 않는다. (B1~B4) */
    @Override
    public Listing bump(String listingId, String sellerId) {
        Listing listing = requireOwnedBy(listingId, sellerId);
        Instant now = Instant.now(clock);
        listing.bump(now, bumpCooldown);
        if (!listingRepository.bumpIfActive(listingId, now)) {
            throw lostRace(listingId, Listing::requireBumpable);
        }
        return listing;
    }

    @Override
    public Duration bumpCooldown() {
        return bumpCooldown;
    }

    /** 기존 컨트롤러 {@code requireSeller}와 같은 규칙·코드. 없는 매물은 404가 먼저다. */
    private Listing requireOwnedBy(String listingId, String sellerId) {
        Listing listing = getById(listingId);
        if (!listing.getSellerId().equals(sellerId)) {
            throw new ForbiddenException("LISTING_ACCESS_DENIED", "본인의 매물만 처리할 수 있습니다");
        }
        return listing;
    }

    /**
     * 원자 갱신이 매칭되지 않았다 — 읽은 뒤 판매 중이 아니게 됐다. 다시 읽어 지금 상태에 맞는
     * 오류(E3·B3)로 바꾼다. 다시 읽었더니 판매 중으로 돌아와 있으면(예약 후 곧바로 해제) 그 사이
     * 주문이 끼어들었던 것이므로 주문 진행 중으로 돌려준다 — 재시도하면 된다.
     */
    private RuntimeException lostRace(String listingId, Consumer<Listing> stateCheck) {
        Listing current = getById(listingId);
        stateCheck.accept(current);
        return ListingConflictException.orderInProgress();
    }

    @Override
    public Listing getById(String listingId) {
        return listingRepository.findById(listingId).orElseThrow(() -> new ListingNotFoundException(listingId));
    }

    @Override
    public Listing getPublicById(String listingId) {
        Listing listing = getById(listingId);
        if (!listing.isPubliclyVisible()) {
            throw new ListingNotFoundException(listingId);
        }
        return listing;
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
    public boolean markDirectTradeSoldIfActive(String listingId) {
        return listingRepository.markSoldIfActive(listingId);
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
    public List<Listing> bySeller(String sellerId) {
        return listingRepository.findBySeller(sellerId);
    }

    @Override
    public List<Listing> activeBySellers(List<String> sellerIds, int limit) {
        return listingRepository.findActiveBySellers(sellerIds, limit);
    }

    @Override
    public List<Listing> byIds(List<String> ids) {
        return listingRepository.findByIds(ids);
    }

    @Override
    public List<Listing> newestActive(int limit) {
        return listingRepository.search(ListingSearchQuery.newestAll()).stream()
                .limit(Math.max(0, limit))
                .toList();
    }

    @Override
    @Transactional
    public void delete(String listingId) {
        Listing listing = getById(listingId);
        listing.delete();
        listingRepository.save(listing);
        photos.revokePhotos(listingId);
    }

    /**
     * 운영자 강제 내림. 사유는 관리자 컨텍스트의 감사 로그가 보관하므로 여기서는 상태 전이만 책임진다.
     * (admin-console 요구사항 4.2)
     */
    @Override
    @Transactional
    public void takedown(String listingId, String reason) {
        Listing listing = getById(listingId);
        listing.takedown();
        listingRepository.save(listing);
        photos.revokePhotos(listingId);
    }
}
