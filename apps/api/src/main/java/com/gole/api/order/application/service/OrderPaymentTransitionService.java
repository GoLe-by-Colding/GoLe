package com.gole.api.order.application.service;

import com.gole.api.order.application.port.out.ListingReservationPort;
import com.gole.api.order.application.port.out.OrderRepositoryPort;
import com.gole.api.order.application.port.out.PaymentGatewayPort.PaymentVerificationResult;
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
    public OrderStatus applyPaymentVerification(String orderId, PaymentVerificationResult verification, Instant now) {
        Order order = load(orderId);
        if (order.getStatus() != OrderStatus.PAYMENT_PENDING && order.getStatus() != OrderStatus.PAYMENT_REVIEW) {
            return order.getStatus();
        }
        switch (verification) {
            case PAID -> order.confirmFundsHeld(now);
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
            case PENDING -> {
                return order.getStatus();
            }
        }
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
