package com.gole.api.shipping.adapter.in.web;

import com.gole.api.account.adapter.in.web.AuthenticatedUser;
import com.gole.api.common.exception.ForbiddenException;
import com.gole.api.order.application.port.in.GetOrderUseCase;
import com.gole.api.order.domain.model.Order;
import com.gole.api.shipping.application.port.in.GetShipmentUseCase;
import com.gole.api.shipping.application.port.in.RegisterWaybillUseCase;
import com.gole.api.shipping.application.port.in.RegisterWaybillUseCase.RegisterWaybillCommand;
import com.gole.api.shipping.application.port.in.TrackShipmentUseCase;
import com.gole.api.shipping.domain.exception.ShipmentNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inbound 어댑터(REST): 운송장 등록·배송 조회. (shipping-and-fees R1, R2)
 * 주문 하위 리소스로 노출한다: {@code /orders/{orderId}/shipment}.
 */
@Tag(name = "Shipment", description = "운송장 등록·배송 상태 추적")
@RestController
@RequestMapping("/api/v1/orders/{orderId}/shipment")
public class ShipmentController {

    private final RegisterWaybillUseCase registerWaybill;
    private final TrackShipmentUseCase trackShipment;
    private final GetShipmentUseCase getShipment;
    private final GetOrderUseCase getOrder;

    public ShipmentController(
            RegisterWaybillUseCase registerWaybill,
            TrackShipmentUseCase trackShipment,
            GetShipmentUseCase getShipment,
            GetOrderUseCase getOrder) {
        this.registerWaybill = registerWaybill;
        this.trackShipment = trackShipment;
        this.getShipment = getShipment;
        this.getOrder = getOrder;
    }

    @Operation(summary = "운송장 등록/교체", description = "주문 판매자만 가능. 재등록 시 직전 운송장은 이력에 보존된다.")
    @PutMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ShipmentResponse register(
            @PathVariable String orderId, @Valid @RequestBody RegisterWaybillRequest request, HttpServletRequest http) {
        return ShipmentResponse.from(registerWaybill.register(new RegisterWaybillCommand(
                orderId,
                AuthenticatedUser.id(http),
                request.carrier(),
                request.waybillNumber(),
                request.sellerPhone())));
    }

    @Operation(summary = "배송 상태 조회", description = "거래 당사자(구매자·판매자)만 조회할 수 있다.")
    @GetMapping
    public ShipmentResponse get(@PathVariable String orderId, HttpServletRequest http) {
        requireParty(orderId, http);
        return getShipment
                .getByOrderId(orderId)
                .map(ShipmentResponse::from)
                .orElseThrow(() -> new ShipmentNotFoundException(orderId));
    }

    @Operation(summary = "배송 상태 새로고침", description = "트래커를 즉시 재조회해 반영한다(응답은 짧은 TTL로 캐시됨).")
    @PostMapping("/tracking")
    public ShipmentResponse refresh(@PathVariable String orderId, HttpServletRequest http) {
        requireParty(orderId, http);
        return ShipmentResponse.from(trackShipment.track(orderId));
    }

    private void requireParty(String orderId, HttpServletRequest http) {
        Order order = getOrder.getById(orderId);
        String accountId = AuthenticatedUser.id(http);
        if (!order.getBuyerId().equals(accountId) && !order.getSellerId().equals(accountId)) {
            throw new ForbiddenException("SHIPMENT_ACCESS_DENIED", "거래 당사자만 배송 정보를 볼 수 있습니다");
        }
    }

    /**
     * @param sellerPhone 판매자 CS 연락처(R8.2). 최초 등록 시 필수, 교체 시 생략하면 기존 값 유지.
     */
    public record RegisterWaybillRequest(
            @NotBlank String carrier, @NotBlank String waybillNumber, String sellerPhone) {}
}
