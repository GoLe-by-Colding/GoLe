package com.gole.api.order.application.service;

import com.gole.api.order.application.port.out.ListingReservationPort;
import com.gole.api.order.application.port.out.OrderRepositoryPort;
import com.gole.api.order.application.port.out.PaymentGatewayPort.PaymentVerification;
import com.gole.api.order.domain.exception.OrderNotFoundException;
import com.gole.api.order.domain.model.Order;
import com.gole.api.order.domain.model.OrderStatus;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** PG 네트워크 I/O가 끝난 뒤 주문·매물 상태만 짧은 Mongo 트랜잭션으로 반영한다. */
@Service
public class OrderPaymentTransitionService {

    private final OrderRepositoryPort orders;
    private final ListingReservationPort listings;

    public OrderPaymentTransitionService(OrderRepositoryPort orders, ListingReservationPort listings) {
        this.orders = orders;
        this.listings = listings;
    }

    @Transactional
    public OrderStatus applyPaymentVerification(String orderId, PaymentVerification verification, Instant now) {
        Order order = load(orderId);
        if (order.getStatus() != OrderStatus.PAYMENT_PENDING && order.getStatus() != OrderStatus.PAYMENT_REVIEW) {
            return order.getStatus();
        }
        switch (verification.result()) {
            case PAID -> order.confirmFundsHeld(now, verification.method());
            case FAILED -> {
                order.failPayment(now);
                listings.release(order.getListingId());
            }
            case REVIEW_REQUIRED -> {
                if (order.getStatus() == OrderStatus.PAYMENT_PENDING) {
                    order.flagPaymentReview(now);
                    return orders.save(order).getStatus();
                }
                return order.getStatus();
            }
            case PENDING, NOT_FOUND -> {
                return order.getStatus();
            }
        }
        return orders.save(order).getStatus();
    }

    /**
     * TTL이 지난 결제 대기 주문 중 PG 원장에도 결제 건이 없는 주문만 만료한다.
     *
     * <p>스케줄러가 후보를 고른 뒤 결제 웹훅이 먼저 도착할 수 있으므로, 트랜잭션 안에서 최신 주문 상태를
     * 다시 읽고 PAYMENT_PENDING일 때만 매물 선점을 해제한다.
     */
    @Transactional
    public OrderStatus expireMissingPayment(String orderId, Instant now) {
        Order order = load(orderId);
        if (order.getStatus() != OrderStatus.PAYMENT_PENDING) {
            return order.getStatus();
        }
        order.failPayment(now);
        listings.release(order.getListingId());
        return orders.save(order).getStatus();
    }

    @Transactional
    public OrderStatus markRefundPending(String orderId, Instant now) {
        Order order = load(orderId);
        if (order.getStatus() == OrderStatus.REFUND_PENDING || order.getStatus() == OrderStatus.REFUNDED) {
            return order.getStatus();
        }
        order.requestRefund(now);
        return orders.save(order).getStatus();
    }

    @Transactional
    public OrderStatus finalizeRefund(String orderId, Instant now) {
        Order order = load(orderId);
        if (order.getStatus() == OrderStatus.REFUNDED) {
            return order.getStatus();
        }
        order.refund(now);
        listings.release(order.getListingId());
        return orders.save(order).getStatus();
    }

    private Order load(String orderId) {
        return orders.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
    }
}
