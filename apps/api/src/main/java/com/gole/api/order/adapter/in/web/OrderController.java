package com.gole.api.order.adapter.in.web;

import com.gole.api.account.adapter.in.web.AuthenticatedUser;
import com.gole.api.common.exception.ConflictException;
import com.gole.api.common.exception.ForbiddenException;
import com.gole.api.order.adapter.in.web.OrderRequests.OpenDisputeRequest;
import com.gole.api.order.adapter.in.web.OrderRequests.PlaceOrderRequest;
import com.gole.api.order.application.port.in.CompleteOrderUseCase;
import com.gole.api.order.application.port.in.GetOrderUseCase;
import com.gole.api.order.application.port.in.GetSellerSettlementsUseCase;
import com.gole.api.order.application.port.in.OpenDisputeUseCase;
import com.gole.api.order.application.port.in.OpenDisputeUseCase.OpenDisputeCommand;
import com.gole.api.order.application.port.in.PayOrderUseCase;
import com.gole.api.order.application.port.in.PlaceOrderUseCase;
import com.gole.api.order.application.port.in.PlaceOrderUseCase.PlaceOrderCommand;
import com.gole.api.order.application.port.in.RefundOrderUseCase;
import com.gole.api.order.domain.model.Order;
import com.gole.api.shipping.application.port.in.GetShipmentUseCase;
import com.gole.api.shipping.domain.model.Shipment;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inbound 어댑터(REST): 주문 라이프사이클. (요구사항 7, 13)
 */
@Tag(name = "Order", description = "주문 생성·결제·구매확정·환불·조회")
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final PlaceOrderUseCase placeOrderUseCase;
    private final PayOrderUseCase payOrderUseCase;
    private final CompleteOrderUseCase completeOrderUseCase;
    private final RefundOrderUseCase refundOrderUseCase;
    private final GetOrderUseCase getOrderUseCase;
    private final OpenDisputeUseCase openDisputeUseCase;
    private final GetShipmentUseCase getShipmentUseCase;
    private final GetSellerSettlementsUseCase sellerSettlements;

    public OrderController(
            PlaceOrderUseCase placeOrderUseCase,
            PayOrderUseCase payOrderUseCase,
            CompleteOrderUseCase completeOrderUseCase,
            RefundOrderUseCase refundOrderUseCase,
            GetOrderUseCase getOrderUseCase,
            OpenDisputeUseCase openDisputeUseCase,
            GetShipmentUseCase getShipmentUseCase,
            GetSellerSettlementsUseCase sellerSettlements) {
        this.placeOrderUseCase = placeOrderUseCase;
        this.payOrderUseCase = payOrderUseCase;
        this.completeOrderUseCase = completeOrderUseCase;
        this.refundOrderUseCase = refundOrderUseCase;
        this.getOrderUseCase = getOrderUseCase;
        this.openDisputeUseCase = openDisputeUseCase;
        this.getShipmentUseCase = getShipmentUseCase;
        this.sellerSettlements = sellerSettlements;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse place(@Valid @RequestBody PlaceOrderRequest request, HttpServletRequest http) {
        String id = placeOrderUseCase.place(
                new PlaceOrderCommand(request.listingId(), AuthenticatedUser.id(http), request.buyerPhone()));
        return OrderResponse.from(getOrderUseCase.getById(id));
    }

    @PostMapping("/{orderId}/payment")
    public OrderResponse pay(@PathVariable String orderId, HttpServletRequest http) {
        requireBuyer(orderId, http);
        payOrderUseCase.pay(orderId);
        return OrderResponse.from(getOrderUseCase.getById(orderId));
    }

    @PostMapping("/{orderId}/completion")
    public OrderResponse complete(@PathVariable String orderId, HttpServletRequest http) {
        requireBuyer(orderId, http);
        completeOrderUseCase.complete(orderId);
        return OrderResponse.from(getOrderUseCase.getById(orderId));
    }

    /**
     * 구매자 일방 환불. (R4.5)
     *
     * <p>판매자가 운송장을 등록하기 전에만 허용한다 — 상품이 이미 이동 중인데 구매자가
     * 일방적으로 자금을 회수하면 판매자가 물건과 돈을 모두 잃는다. 발송 이후의 문제는
     * 분쟁({@code /dispute})으로 접수해 운영자가 배송 사실을 근거로 판정한다(R4.3).
     */
    @PostMapping("/{orderId}/refund")
    public OrderResponse refund(@PathVariable String orderId, HttpServletRequest http) {
        requireBuyer(orderId, http);
        if (getShipmentUseCase.getByOrderId(orderId).isPresent()) {
            throw new ConflictException("SHIPMENT_ALREADY_REGISTERED", "판매자가 이미 발송한 주문입니다. 문제가 있다면 분쟁을 접수해 주세요");
        }
        refundOrderUseCase.refund(orderId);
        return OrderResponse.from(getOrderUseCase.getById(orderId));
    }

    @Operation(summary = "분쟁 제기", description = "구매자만. 결제 승인(funds_held) 상태에서만 가능하며 자동 구매확정이 정지된다.")
    @PostMapping("/{orderId}/dispute")
    public OrderResponse openDispute(
            @PathVariable String orderId, @Valid @RequestBody OpenDisputeRequest request, HttpServletRequest http) {
        openDisputeUseCase.open(
                new OpenDisputeCommand(orderId, AuthenticatedUser.id(http), request.reason(), request.detail()));
        return OrderResponse.from(getOrderUseCase.getById(orderId));
    }

    @Operation(summary = "거래 연락처 조회", description = "거래 당사자만. 마스킹 없는 전체 번호를 반환한다(R8.4). 분쟁 대응 목적 외 사용 금지(R8.6).")
    @GetMapping("/{orderId}/contacts")
    public OrderContactsResponse contacts(@PathVariable String orderId, HttpServletRequest http) {
        Order order = requireParty(orderId, http);
        Shipment shipment = getShipmentUseCase.getByOrderId(orderId).orElse(null);
        return new OrderContactsResponse(
                order.getBuyerPhone() == null ? null : order.getBuyerPhone().value(),
                shipment == null ? null : shipment.getSellerPhone(),
                "거래 분쟁 대응 목적으로만 사용할 수 있으며, 목적 외 사용(마케팅·재판매 등)은 금지됩니다.");
    }

    @Operation(summary = "주문 단건 조회", description = "거래 당사자(구매자·판매자)만. 판매자는 발송 처리를 위해 조회가 필요하다.")
    @GetMapping("/{orderId}")
    public OrderResponse get(@PathVariable String orderId, HttpServletRequest http) {
        requireParty(orderId, http);
        return OrderResponse.from(getOrderUseCase.getById(orderId));
    }

    @Operation(summary = "내 정산 내역", description = "판매자 본인의 정산 원장(최신순). 지급 예정액과 지급 가능 시각을 함께 준다.")
    @GetMapping("/settlements")
    public List<GetSellerSettlementsUseCase.SellerSettlementSummary> mySettlements(
            @RequestParam(value = "limit", defaultValue = "50") int limit, HttpServletRequest http) {
        return sellerSettlements.listBySeller(AuthenticatedUser.id(http), limit);
    }

    @Operation(summary = "내 판매 내역", description = "sellerId 기준 주문 목록(최신순). 판매자 발송 관리에 사용.")
    @GetMapping("/sales")
    public List<OrderResponse> listBySeller(HttpServletRequest http) {
        return getOrderUseCase.getBySellerId(AuthenticatedUser.id(http)).stream()
                .map(OrderResponse::from)
                .toList();
    }

    @Operation(summary = "내 구매 내역", description = "buyerId 기준 주문 목록(최신순). 프로필 내 주문 내역에 사용.")
    @GetMapping
    public List<OrderResponse> listByBuyer(HttpServletRequest http) {
        return getOrderUseCase.getByBuyerId(AuthenticatedUser.id(http)).stream()
                .map(OrderResponse::from)
                .toList();
    }

    private void requireBuyer(String orderId, HttpServletRequest http) {
        if (!getOrderUseCase.getById(orderId).getBuyerId().equals(AuthenticatedUser.id(http))) {
            throw new ForbiddenException("ORDER_ACCESS_DENIED", "본인의 주문만 처리할 수 있습니다");
        }
    }

    private Order requireParty(String orderId, HttpServletRequest http) {
        Order order = getOrderUseCase.getById(orderId);
        String accountId = AuthenticatedUser.id(http);
        if (!order.getBuyerId().equals(accountId) && !order.getSellerId().equals(accountId)) {
            throw new ForbiddenException("ORDER_ACCESS_DENIED", "거래 당사자만 볼 수 있습니다");
        }
        return order;
    }

    /** 전체 번호 응답(R8.4 전용 엔드포인트). {@code notice}는 목적 외 사용 금지 고지다(R8.6). */
    public record OrderContactsResponse(String buyerPhone, String sellerPhone, String notice) {}
}
