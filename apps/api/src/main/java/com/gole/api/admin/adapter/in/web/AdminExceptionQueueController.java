package com.gole.api.admin.adapter.in.web;

import com.gole.api.admin.application.port.in.RecordAdminActionUseCase;
import com.gole.api.admin.application.port.in.RecordAdminActionUseCase.RecordAdminActionCommand;
import com.gole.api.admin.application.service.ExceptionQueueService;
import com.gole.api.admin.application.service.ExceptionQueueService.ExceptionEntry;
import com.gole.api.admin.domain.model.AdminActionType;
import com.gole.api.admin.domain.model.AdminTargetType;
import com.gole.api.order.application.port.in.GetOrderUseCase;
import com.gole.api.order.application.port.in.ResolveDisputeUseCase;
import com.gole.api.order.application.port.in.ResolveDisputeUseCase.Resolution;
import com.gole.api.order.application.port.in.ResolveDisputeUseCase.ResolveDisputeCommand;
import com.gole.api.order.domain.model.Order;
import com.gole.api.shipping.application.port.in.GetShipmentUseCase;
import com.gole.api.shipping.domain.model.Shipment;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 예외 큐 — 운영자가 보는 전부. (shipping-and-fees R7.6, admin-console 통합)
 *
 * <p>정상 진행 건은 목록에 뜨지 않는다. 큐가 비어 있으면 운영자가 할 일이 없는 게 정상이다.
 * 분쟁 판정 화면에는 트래커의 객관적 배송 사실을 함께 제공한다(R4.3).
 */
@Tag(name = "Admin · 예외 큐", description = "분쟁·배송 정체·미접수·추적불가 — 사람이 볼 건만")
@RestController
@RequestMapping("/api/admin")
public class AdminExceptionQueueController {

    private final ExceptionQueueService exceptionQueue;
    private final ResolveDisputeUseCase resolveDispute;
    private final GetOrderUseCase getOrder;
    private final GetShipmentUseCase getShipment;
    private final RecordAdminActionUseCase audit;

    public AdminExceptionQueueController(
            ExceptionQueueService exceptionQueue,
            ResolveDisputeUseCase resolveDispute,
            GetOrderUseCase getOrder,
            GetShipmentUseCase getShipment,
            RecordAdminActionUseCase audit) {
        this.exceptionQueue = exceptionQueue;
        this.resolveDispute = resolveDispute;
        this.getOrder = getOrder;
        this.getShipment = getShipment;
        this.audit = audit;
    }

    @Operation(summary = "예외 큐 조회", description = "분쟁·판정지연·배송정체·미접수·추적불가 건만. 정상 진행 건은 없다.")
    @GetMapping("/exception-queue")
    public List<ExceptionEntry> list() {
        return exceptionQueue.list();
    }

    @Operation(summary = "분쟁 판정", description = "환불(refund) 또는 거래 완료(complete). 무개입 파이프라인의 유일한 사람 개입 지점(R9.2).")
    @PostMapping("/orders/{orderId}/dispute-resolution")
    public List<ExceptionEntry> resolve(
            @PathVariable String orderId, @Valid @RequestBody ResolveDisputeRequest request, HttpServletRequest http) {
        Resolution resolution =
                "refund".equalsIgnoreCase(request.resolution()) ? Resolution.REFUND : Resolution.COMPLETE;
        resolveDispute.resolve(new ResolveDisputeCommand(orderId, resolution));
        record(http, AdminActionType.ORDER_DISPUTE_RESOLVE, orderId, resolution.name() + emptyOr(request.note()));
        return exceptionQueue.list();
    }

    @Operation(summary = "거래 연락처 열람(전체 번호)", description = "분쟁 대응 목적. 열람 사실이 감사 로그에 남는다(R8.5).")
    @GetMapping("/orders/{orderId}/contacts")
    public AdminContactsResponse contacts(@PathVariable String orderId, HttpServletRequest http) {
        Order order = getOrder.getById(orderId);
        Shipment shipment = getShipment.getByOrderId(orderId).orElse(null);
        record(http, AdminActionType.ORDER_CONTACT_VIEW, orderId, null);
        return new AdminContactsResponse(
                order.getBuyerPhone() == null ? null : order.getBuyerPhone().value(),
                shipment == null ? null : shipment.getSellerPhone(),
                "거래 분쟁 대응 목적으로만 사용할 수 있으며, 목적 외 사용은 금지됩니다.");
    }

    private void record(HttpServletRequest http, AdminActionType type, String orderId, String reason) {
        AdminActor actor = AdminActor.of(http);
        audit.record(
                new RecordAdminActionCommand(actor.id(), actor.email(), type, AdminTargetType.ORDER, orderId, reason));
    }

    private static String emptyOr(String note) {
        return note == null || note.isBlank() ? "" : " — " + note.trim();
    }

    public record ResolveDisputeRequest(@NotNull String resolution, String note) {}

    public record AdminContactsResponse(String buyerPhone, String sellerPhone, String notice) {}
}
