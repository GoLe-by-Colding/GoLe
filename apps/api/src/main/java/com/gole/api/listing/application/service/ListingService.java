package com.gole.api.listing.application.service;

import com.gole.api.listing.application.port.in.BrowseListingsUseCase;
import com.gole.api.listing.application.port.in.CreateListingUseCase;
import com.gole.api.listing.application.port.in.DeleteListingUseCase;
import com.gole.api.listing.application.port.in.GetListingUseCase;
import com.gole.api.listing.application.port.in.MarkListingSoldUseCase;
import com.gole.api.listing.application.port.in.ModerateListingUseCase;
import com.gole.api.listing.application.port.in.ReleaseListingUseCase;
import com.gole.api.listing.application.port.in.ReserveListingUseCase;
import com.gole.api.listing.application.port.in.SearchListingsUseCase;
import com.gole.api.listing.application.port.out.ListingIdGeneratorPort;
import com.gole.api.listing.application.port.out.ListingRepositoryPort;
import com.gole.api.listing.application.port.out.NewListingNotifierPort;
import com.gole.api.listing.application.query.ListingSearchQuery;
import com.gole.api.listing.domain.exception.ListingNotFoundException;
import com.gole.api.listing.domain.model.Listing;
import com.gole.api.listing.domain.model.Money;
import com.gole.api.media.application.port.in.ManageMediaAssetsUseCase;
import com.gole.api.media.domain.model.MediaTargetType;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
                ModerateListingUseCase {

    private final ListingRepositoryPort listingRepository;
    private final ListingIdGeneratorPort idGenerator;
    private final NewListingNotifierPort newListingNotifier;
    private final ManageMediaAssetsUseCase mediaAssets;
    private final Clock clock;

    public ListingService(
            ListingRepositoryPort listingRepository,
            ListingIdGeneratorPort idGenerator,
            NewListingNotifierPort newListingNotifier,
            ManageMediaAssetsUseCase mediaAssets,
            Clock clock) {
        this.listingRepository = listingRepository;
        this.idGenerator = idGenerator;
        this.newListingNotifier = newListingNotifier;
        this.mediaAssets = mediaAssets;
        this.clock = clock;
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
                Instant.now(clock));
        mediaAssets.replaceReferences(
                command.sellerId(), MediaTargetType.LISTING, listingId, command.photoKeys(), true);
        Listing saved = listingRepository.save(listing);
        newListingNotifier.notifyFollowers(saved.getSellerId(), saved.getId(), saved.getTitle());
        return saved.getId();
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
    @Transactional
    public void delete(String listingId) {
        Listing listing = getById(listingId);
        listing.delete();
        listingRepository.save(listing);
        mediaAssets.revokeTarget(MediaTargetType.LISTING, listingId);
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
        mediaAssets.revokeTarget(MediaTargetType.LISTING, listingId);
    }
}
