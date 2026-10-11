package com.gole.api.account.adapter.out.linked;

import com.gole.api.account.application.port.out.AccountLinkedRecordsPort;
import com.gole.api.account.domain.model.AccountDeletionBlocker;
import com.gole.api.admin.application.port.in.PseudonymizeAdminActionTargetsUseCase;
import com.gole.api.admin.domain.model.AdminTargetType;
import com.gole.api.bid.application.port.in.BidAccountErasureUseCase;
import com.gole.api.chat.application.port.in.ChatAccountErasureUseCase;
import com.gole.api.chat.application.port.in.ChatAccountErasureUseCase.ChatErasure;
import com.gole.api.collection.application.port.in.CollectionAccountErasureUseCase;
import com.gole.api.collection.application.port.in.CollectionAccountErasureUseCase.CollectionErasure;
import com.gole.api.community.application.port.in.CommunityAccountErasureUseCase;
import com.gole.api.community.application.port.in.CommunityAccountErasureUseCase.CommunityErasure;
import com.gole.api.discovery.application.port.in.DiscoveryAccountErasureUseCase;
import com.gole.api.discovery.application.port.in.DiscoveryAccountErasureUseCase.DiscoveryErasure;
import com.gole.api.listing.application.port.in.ListingAccountErasureUseCase;
import com.gole.api.listing.application.port.in.ListingAccountErasureUseCase.ListingErasure;
import com.gole.api.media.application.port.in.MediaAccountErasureUseCase;
import com.gole.api.notification.application.port.in.NotificationAccountErasureUseCase;
import com.gole.api.notification.application.port.in.NotificationAccountErasureUseCase.NotificationErasure;
import com.gole.api.offer.application.port.in.OfferAccountErasureUseCase;
import com.gole.api.order.application.port.in.OrderAccountErasureUseCase;
import com.gole.api.parts.application.port.in.PartsAccountErasureUseCase;
import com.gole.api.report.application.port.in.ReportAccountErasureUseCase;
import com.gole.api.review.application.port.in.ReviewAccountErasureUseCase;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 회원 탈퇴의 연계 기록을 각 소유 컨텍스트의 탈퇴 참여 포트로 처리한다. 차단 사유 순서와 영수증 카운트 키·순서는 이전에
 * 계정 어댑터가 직접 컬렉션을 다루던 때와 같다(account-deletion-participants R3).
 */
@Component
public class CrossContextAccountLinkedRecordsAdapter implements AccountLinkedRecordsPort {

    private final OrderAccountErasureUseCase orders;
    private final ReportAccountErasureUseCase reports;
    private final ChatAccountErasureUseCase chat;
    private final ListingAccountErasureUseCase listings;
    private final CommunityAccountErasureUseCase community;
    private final MediaAccountErasureUseCase media;
    private final NotificationAccountErasureUseCase notifications;
    private final DiscoveryAccountErasureUseCase discovery;
    private final CollectionAccountErasureUseCase collection;
    private final PartsAccountErasureUseCase parts;
    private final BidAccountErasureUseCase bids;
    private final OfferAccountErasureUseCase offers;
    private final ReviewAccountErasureUseCase reviews;
    private final PseudonymizeAdminActionTargetsUseCase adminAudit;

    public CrossContextAccountLinkedRecordsAdapter(
            OrderAccountErasureUseCase orders,
            ReportAccountErasureUseCase reports,
            ChatAccountErasureUseCase chat,
            ListingAccountErasureUseCase listings,
            CommunityAccountErasureUseCase community,
            MediaAccountErasureUseCase media,
            NotificationAccountErasureUseCase notifications,
            DiscoveryAccountErasureUseCase discovery,
            CollectionAccountErasureUseCase collection,
            PartsAccountErasureUseCase parts,
            BidAccountErasureUseCase bids,
            OfferAccountErasureUseCase offers,
            ReviewAccountErasureUseCase reviews,
            PseudonymizeAdminActionTargetsUseCase adminAudit) {
        this.orders = orders;
        this.reports = reports;
        this.chat = chat;
        this.listings = listings;
        this.community = community;
        this.media = media;
        this.notifications = notifications;
        this.discovery = discovery;
        this.collection = collection;
        this.parts = parts;
        this.bids = bids;
        this.offers = offers;
        this.reviews = reviews;
        this.adminAudit = adminAudit;
    }

    @Override
    public List<AccountDeletionBlocker> blockers(String accountId) {
        List<AccountDeletionBlocker> blockers = new ArrayList<>();
        if (orders.hasActiveOrder(accountId)) {
            blockers.add(AccountDeletionBlocker.ACTIVE_ORDER);
        }
        if (orders.hasUnsettledPayout(accountId)) {
            blockers.add(AccountDeletionBlocker.UNSETTLED_PAYOUT);
        }
        if (reports.hasPendingReport(accountId)) {
            blockers.add(AccountDeletionBlocker.PENDING_REPORT);
        }
        if (chat.hasSupportRecords(accountId)) {
            blockers.add(AccountDeletionBlocker.SUPPORT_RECORDS_REQUIRE_PURGE);
        }
        if (listings.hasPublicContent(accountId) || community.hasPublicContent(accountId)) {
            blockers.add(AccountDeletionBlocker.PUBLIC_CONTENT_REQUIRES_LIFECYCLE_REVIEW);
        }
        if (media.hasLiveMedia(accountId)) {
            blockers.add(AccountDeletionBlocker.MEDIA_REQUIRES_LIFECYCLE_REVIEW);
        }
        if (chat.ownsOpenGroup(accountId)) {
            blockers.add(AccountDeletionBlocker.OWNED_GROUP_REQUIRES_TRANSFER);
        }
        return blockers;
    }

    @Override
    public Map<String, Long> erase(String accountId, String anonymousSubject, String receiptId) {
        NotificationErasure notification = notifications.erase(accountId, anonymousSubject);
        DiscoveryErasure discovered = discovery.erase(accountId, anonymousSubject);
        CollectionErasure collected = collection.erase(accountId, anonymousSubject);
        long partRequests = parts.erase(accountId, anonymousSubject).partRequests();
        long bidCount = bids.erase(accountId, anonymousSubject).bids();
        long offerCount = offers.erase(accountId, anonymousSubject).offers();
        ChatErasure chatted = chat.erase(accountId, anonymousSubject);
        long reviewCount = reviews.erase(accountId, anonymousSubject).reviews();
        long reportCount = reports.erase(accountId, anonymousSubject).reports();
        ListingErasure listed = listings.erase(accountId, anonymousSubject);
        CommunityErasure posted = community.erase(accountId, anonymousSubject);
        long revokedMedia = media.erase(accountId, anonymousSubject).revokedMediaAssets();
        long auditTargets = adminAudit.replaceTargetIdAndDropReason(AdminTargetType.ACCOUNT, accountId, receiptId);

        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("notifications", notification.notifications());
        counts.put("notificationPreferences", notification.notificationPreferences());
        counts.put("wishlistEntries", discovered.wishlistEntries());
        counts.put("collectionItems", collected.collectionItems());
        counts.put("collectionValueSnapshots", collected.collectionValueSnapshots());
        counts.put("partRequests", partRequests);
        counts.put("bids", bidCount);
        counts.put("offers", offerCount);
        counts.put("follows", discovered.follows());
        counts.put("chatReadCursors", chatted.chatReadCursors());
        counts.put("chatBlocks", chatted.chatBlocks());
        counts.put("chatMessages", chatted.chatMessages());
        counts.put("marketChatRooms", chatted.marketChatRooms());
        counts.put("socialChatRooms", chatted.socialChatRooms());
        counts.put("reviews", reviewCount);
        counts.put("reports", reportCount);
        counts.put("chatReportSnapshots", chatted.chatReportSnapshots());
        counts.put("retiredListings", listed.retiredListings());
        counts.put("deletedPosts", posted.deletedPosts());
        counts.put("hiddenComments", posted.hiddenComments());
        counts.put("deletedListingComments", listed.deletedListingComments());
        counts.put("revokedMediaAssets", revokedMedia);
        counts.put("adminAuditTargets", auditTargets);
        return counts;
    }
}
