package com.gole.api.admin.adapter.out.readmodel;

import com.gole.api.account.application.port.in.CountAccountsUseCase;
import com.gole.api.admin.application.port.out.AdminReadModelPort;
import com.gole.api.admin.domain.model.AdminListingRow;
import com.gole.api.admin.domain.model.AdminOrderRow;
import com.gole.api.admin.domain.model.AdminOrderStats;
import com.gole.api.admin.domain.model.AdminPaymentMethodView;
import com.gole.api.admin.domain.model.AdminPostRow;
import com.gole.api.admin.domain.model.AdminVolumeCounts;
import com.gole.api.catalog.application.port.in.CountLegoSetsUseCase;
import com.gole.api.community.application.port.in.MonitorPostsUseCase;
import com.gole.api.listing.application.port.in.MonitorListingsUseCase;
import com.gole.api.order.application.port.in.MonitorOrdersUseCase;
import com.gole.api.order.application.port.in.MonitorOrdersUseCase.OrderStats;
import com.gole.api.pricing.application.port.in.CountPriceTransactionsUseCase;
import com.gole.api.review.application.port.in.CountReviewsUseCase;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 운영 화면 읽기 모델을 각 소유 컨텍스트의 조회 유스케이스로 모은다. admin 은 다른 컨텍스트의 컬렉션 이름과 문서
 * 필드를 모르고, 받은 값을 admin 행으로 옮기기만 한다.
 */
@Component
public class CrossContextAdminReadModelAdapter implements AdminReadModelPort {

    private final MonitorOrdersUseCase orders;
    private final MonitorListingsUseCase listings;
    private final MonitorPostsUseCase posts;
    private final CountAccountsUseCase accounts;
    private final CountLegoSetsUseCase legoSets;
    private final CountReviewsUseCase reviews;
    private final CountPriceTransactionsUseCase priceTransactions;

    public CrossContextAdminReadModelAdapter(
            MonitorOrdersUseCase orders,
            MonitorListingsUseCase listings,
            MonitorPostsUseCase posts,
            CountAccountsUseCase accounts,
            CountLegoSetsUseCase legoSets,
            CountReviewsUseCase reviews,
            CountPriceTransactionsUseCase priceTransactions) {
        this.orders = orders;
        this.listings = listings;
        this.posts = posts;
        this.accounts = accounts;
        this.legoSets = legoSets;
        this.reviews = reviews;
        this.priceTransactions = priceTransactions;
    }

    @Override
    public AdminVolumeCounts volumeCounts() {
        return new AdminVolumeCounts(
                accounts.estimatedAccountCount(),
                legoSets.estimatedLegoSetCount(),
                listings.estimatedListingCount(),
                orders.estimatedOrderCount(),
                posts.estimatedPostCount(),
                reviews.estimatedReviewCount(),
                priceTransactions.estimatedPriceTransactionCount());
    }

    @Override
    public AdminOrderStats orderStats() {
        OrderStats stats = orders.orderStats();
        return new AdminOrderStats(stats.countByStatus(), stats.completedGmv());
    }

    @Override
    public long activeListingCount() {
        return listings.activeListingCount();
    }

    @Override
    public List<AdminOrderRow> recentOrders(String status, String query, int limit) {
        return orders.recentOrders(status, query, limit).stream()
                .map(row -> new AdminOrderRow(
                        row.id(),
                        row.status(),
                        row.amount(),
                        row.buyerId(),
                        row.sellerId(),
                        row.catalogSetNumber(),
                        row.paymentMethodType() == null
                                ? null
                                : new AdminPaymentMethodView(row.paymentMethodType(), row.paymentProvider()),
                        row.createdAt()))
                .toList();
    }

    @Override
    public List<AdminListingRow> recentListings(String status, String query, int limit) {
        return listings.recentListings(status, query, limit).stream()
                .map(row -> new AdminListingRow(
                        row.id(),
                        row.title(),
                        row.sellerId(),
                        row.price(),
                        row.status(),
                        row.category(),
                        row.createdAt()))
                .toList();
    }

    @Override
    public List<AdminPostRow> recentPosts(String status, String query, int limit) {
        return posts.recentPosts(status, query, limit).stream()
                .map(row -> new AdminPostRow(
                        row.id(), row.authorId(), row.content(), row.type(), row.status(), row.createdAt()))
                .toList();
    }
}
