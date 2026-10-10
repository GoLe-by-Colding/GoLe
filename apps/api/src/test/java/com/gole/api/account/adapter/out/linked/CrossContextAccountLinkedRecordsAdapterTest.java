package com.gole.api.account.adapter.out.linked;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gole.api.account.domain.model.AccountDeletionBlocker;
import com.gole.api.admin.application.port.in.PseudonymizeAdminActionTargetsUseCase;
import com.gole.api.admin.domain.model.AdminTargetType;
import com.gole.api.bid.application.port.in.BidAccountErasureUseCase;
import com.gole.api.bid.application.port.in.BidAccountErasureUseCase.BidErasure;
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
import com.gole.api.media.application.port.in.MediaAccountErasureUseCase.MediaErasure;
import com.gole.api.notification.application.port.in.NotificationAccountErasureUseCase;
import com.gole.api.notification.application.port.in.NotificationAccountErasureUseCase.NotificationErasure;
import com.gole.api.offer.application.port.in.OfferAccountErasureUseCase;
import com.gole.api.offer.application.port.in.OfferAccountErasureUseCase.OfferErasure;
import com.gole.api.order.application.port.in.OrderAccountErasureUseCase;
import com.gole.api.parts.application.port.in.PartsAccountErasureUseCase;
import com.gole.api.parts.application.port.in.PartsAccountErasureUseCase.PartsErasure;
import com.gole.api.report.application.port.in.ReportAccountErasureUseCase;
import com.gole.api.report.application.port.in.ReportAccountErasureUseCase.ReportErasure;
import com.gole.api.review.application.port.in.ReviewAccountErasureUseCase;
import com.gole.api.review.application.port.in.ReviewAccountErasureUseCase.ReviewErasure;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CrossContextAccountLinkedRecordsAdapterTest {

    private final OrderAccountErasureUseCase orders = mock(OrderAccountErasureUseCase.class);
    private final ReportAccountErasureUseCase reports = mock(ReportAccountErasureUseCase.class);
    private final ChatAccountErasureUseCase chat = mock(ChatAccountErasureUseCase.class);
    private final ListingAccountErasureUseCase listings = mock(ListingAccountErasureUseCase.class);
    private final CommunityAccountErasureUseCase community = mock(CommunityAccountErasureUseCase.class);
    private final MediaAccountErasureUseCase media = mock(MediaAccountErasureUseCase.class);
    private final NotificationAccountErasureUseCase notifications = mock(NotificationAccountErasureUseCase.class);
    private final DiscoveryAccountErasureUseCase discovery = mock(DiscoveryAccountErasureUseCase.class);
    private final CollectionAccountErasureUseCase collection = mock(CollectionAccountErasureUseCase.class);
    private final PartsAccountErasureUseCase parts = mock(PartsAccountErasureUseCase.class);
    private final BidAccountErasureUseCase bids = mock(BidAccountErasureUseCase.class);
    private final OfferAccountErasureUseCase offers = mock(OfferAccountErasureUseCase.class);
    private final ReviewAccountErasureUseCase reviews = mock(ReviewAccountErasureUseCase.class);
    private final PseudonymizeAdminActionTargetsUseCase adminAudit = mock(PseudonymizeAdminActionTargetsUseCase.class);
    private final CrossContextAccountLinkedRecordsAdapter adapter = new CrossContextAccountLinkedRecordsAdapter(
            orders,
            reports,
            chat,
            listings,
            community,
            media,
            notifications,
            discovery,
            collection,
            parts,
            bids,
            offers,
            reviews,
            adminAudit);

    @Test
    @DisplayName("차단 사유는 이전 계정 어댑터와 같은 순서로 모으고, 매물이나 커뮤니티 어느 쪽이든 공개 콘텐츠면 막는다")
    void blockers_keepPreviousOrder() {
        when(orders.hasActiveOrder("account-1")).thenReturn(true);
        when(orders.hasUnsettledPayout("account-1")).thenReturn(true);
        when(reports.hasPendingReport("account-1")).thenReturn(true);
        when(chat.hasSupportRecords("account-1")).thenReturn(true);
        when(community.hasPublicContent("account-1")).thenReturn(true);
        when(media.hasLiveMedia("account-1")).thenReturn(true);
        when(chat.ownsOpenGroup("account-1")).thenReturn(true);

        assertThat(adapter.blockers("account-1"))
                .containsExactly(
                        AccountDeletionBlocker.ACTIVE_ORDER,
                        AccountDeletionBlocker.UNSETTLED_PAYOUT,
                        AccountDeletionBlocker.PENDING_REPORT,
                        AccountDeletionBlocker.SUPPORT_RECORDS_REQUIRE_PURGE,
                        AccountDeletionBlocker.PUBLIC_CONTENT_REQUIRES_LIFECYCLE_REVIEW,
                        AccountDeletionBlocker.MEDIA_REQUIRES_LIFECYCLE_REVIEW,
                        AccountDeletionBlocker.OWNED_GROUP_REQUIRES_TRANSFER);
        assertThat(new CrossContextAccountLinkedRecordsAdapter(
                                mock(OrderAccountErasureUseCase.class),
                                mock(ReportAccountErasureUseCase.class),
                                mock(ChatAccountErasureUseCase.class),
                                mock(ListingAccountErasureUseCase.class),
                                mock(CommunityAccountErasureUseCase.class),
                                mock(MediaAccountErasureUseCase.class),
                                notifications,
                                discovery,
                                collection,
                                parts,
                                bids,
                                offers,
                                reviews,
                                adminAudit)
                        .blockers("account-1"))
                .isEmpty();
    }

    @Test
    @DisplayName("영수증 카운트는 이전 계정 어댑터와 같은 키·순서이고, 감사 기록은 영수증 ID 로 가명화한다")
    void erase_keepsReceiptKeysAndOrder() {
        when(notifications.erase(anyString(), anyString())).thenReturn(new NotificationErasure(1, 2));
        when(discovery.erase(anyString(), anyString())).thenReturn(new DiscoveryErasure(3, 9));
        when(collection.erase(anyString(), anyString())).thenReturn(new CollectionErasure(4, 5));
        when(parts.erase(anyString(), anyString())).thenReturn(new PartsErasure(6));
        when(bids.erase(anyString(), anyString())).thenReturn(new BidErasure(7));
        when(offers.erase(anyString(), anyString())).thenReturn(new OfferErasure(8));
        when(chat.erase(anyString(), anyString())).thenReturn(new ChatErasure(10, 11, 12, 13, 14, 17));
        when(reviews.erase(anyString(), anyString())).thenReturn(new ReviewErasure(15));
        when(reports.erase(anyString(), anyString())).thenReturn(new ReportErasure(16));
        when(listings.erase(anyString(), anyString())).thenReturn(new ListingErasure(18, 21));
        when(community.erase(anyString(), anyString())).thenReturn(new CommunityErasure(19, 20));
        when(media.erase(anyString(), anyString())).thenReturn(new MediaErasure(22));
        when(adminAudit.replaceTargetIdAndDropReason(AdminTargetType.ACCOUNT, "account-1", "receipt-1"))
                .thenReturn(23L);

        Map<String, Long> counts = adapter.erase("account-1", "withdrawn-1", "receipt-1");

        assertThat(counts.keySet())
                .containsExactly(
                        "notifications",
                        "notificationPreferences",
                        "wishlistEntries",
                        "collectionItems",
                        "collectionValueSnapshots",
                        "partRequests",
                        "bids",
                        "offers",
                        "follows",
                        "chatReadCursors",
                        "chatBlocks",
                        "chatMessages",
                        "marketChatRooms",
                        "socialChatRooms",
                        "reviews",
                        "reports",
                        "chatReportSnapshots",
                        "retiredListings",
                        "deletedPosts",
                        "hiddenComments",
                        "deletedListingComments",
                        "revokedMediaAssets",
                        "adminAuditTargets");
        assertThat(counts.values())
                .containsExactly(
                        1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L, 12L, 13L, 14L, 15L, 16L, 17L, 18L, 19L, 20L, 21L,
                        22L, 23L);
    }
}
