package com.gole.api.account.support;

import com.gole.api.account.adapter.out.linked.CrossContextAccountLinkedRecordsAdapter;
import com.gole.api.admin.adapter.out.persistence.AdminAuditPseudonymizationAdapter;
import com.gole.api.admin.application.service.AdminAuditPseudonymizationService;
import com.gole.api.bid.adapter.out.persistence.MongoBidAccountErasureAdapter;
import com.gole.api.bid.application.service.BidAccountErasureService;
import com.gole.api.chat.adapter.out.persistence.MongoChatAccountErasureAdapter;
import com.gole.api.chat.application.service.ChatAccountErasureService;
import com.gole.api.collection.adapter.out.persistence.MongoCollectionAccountErasureAdapter;
import com.gole.api.collection.application.service.CollectionAccountErasureService;
import com.gole.api.community.adapter.out.persistence.MongoCommunityAccountErasureAdapter;
import com.gole.api.community.application.service.CommunityAccountErasureService;
import com.gole.api.discovery.adapter.out.persistence.MongoDiscoveryAccountErasureAdapter;
import com.gole.api.discovery.application.service.DiscoveryAccountErasureService;
import com.gole.api.listing.adapter.out.persistence.MongoListingAccountErasureAdapter;
import com.gole.api.listing.application.service.ListingAccountErasureService;
import com.gole.api.media.adapter.out.persistence.MongoMediaAccountErasureAdapter;
import com.gole.api.media.application.service.MediaAccountErasureService;
import com.gole.api.notification.adapter.out.persistence.MongoNotificationAccountErasureAdapter;
import com.gole.api.notification.application.service.NotificationAccountErasureService;
import com.gole.api.offer.adapter.out.persistence.MongoOfferAccountErasureAdapter;
import com.gole.api.offer.application.service.OfferAccountErasureService;
import com.gole.api.order.adapter.out.persistence.MongoOrderAccountErasureAdapter;
import com.gole.api.order.application.service.OrderAccountErasureService;
import com.gole.api.parts.adapter.out.persistence.MongoPartsAccountErasureAdapter;
import com.gole.api.parts.application.service.PartsAccountErasureService;
import com.gole.api.report.adapter.out.persistence.MongoReportAccountErasureAdapter;
import com.gole.api.report.application.service.ReportAccountErasureService;
import com.gole.api.review.adapter.out.persistence.MongoReviewAccountErasureAdapter;
import com.gole.api.review.application.service.ReviewAccountErasureService;
import org.springframework.data.mongodb.core.MongoTemplate;

/** 통합 테스트용 배선: 계정 탈퇴의 연계 기록을 각 컨텍스트의 실제 탈퇴 참여 어댑터로 처리한다. */
public final class AccountLinkedRecordsWiring {

    private AccountLinkedRecordsWiring() {}

    public static CrossContextAccountLinkedRecordsAdapter realParticipants(MongoTemplate mongo) {
        return new CrossContextAccountLinkedRecordsAdapter(
                new OrderAccountErasureService(new MongoOrderAccountErasureAdapter(mongo)),
                new ReportAccountErasureService(new MongoReportAccountErasureAdapter(mongo)),
                new ChatAccountErasureService(new MongoChatAccountErasureAdapter(mongo)),
                new ListingAccountErasureService(new MongoListingAccountErasureAdapter(mongo)),
                new CommunityAccountErasureService(new MongoCommunityAccountErasureAdapter(mongo)),
                new MediaAccountErasureService(new MongoMediaAccountErasureAdapter(mongo)),
                new NotificationAccountErasureService(new MongoNotificationAccountErasureAdapter(mongo)),
                new DiscoveryAccountErasureService(new MongoDiscoveryAccountErasureAdapter(mongo)),
                new CollectionAccountErasureService(new MongoCollectionAccountErasureAdapter(mongo)),
                new PartsAccountErasureService(new MongoPartsAccountErasureAdapter(mongo)),
                new BidAccountErasureService(new MongoBidAccountErasureAdapter(mongo)),
                new OfferAccountErasureService(new MongoOfferAccountErasureAdapter(mongo)),
                new ReviewAccountErasureService(new MongoReviewAccountErasureAdapter(mongo)),
                new AdminAuditPseudonymizationService(new AdminAuditPseudonymizationAdapter(mongo)));
    }
}
