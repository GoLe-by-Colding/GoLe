package com.gole.api.order.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gole.api.account.adapter.in.web.UserAuthInterceptor;
import com.gole.api.account.application.service.SellerIdentityVerificationService;
import com.gole.api.account.application.service.ThirdPartyProvisionConsentService;
import com.gole.api.common.exception.ForbiddenException;
import com.gole.api.common.exception.ServiceUnavailableException;
import com.gole.api.common.operations.OperationalEventPublisher;
import com.gole.api.common.web.GlobalExceptionHandler;
import com.gole.api.listing.application.port.in.GetListingUseCase;
import com.gole.api.listing.domain.model.Listing;
import com.gole.api.order.application.port.in.CompleteOrderUseCase;
import com.gole.api.order.application.port.in.GetOrderUseCase;
import com.gole.api.order.application.port.in.GetSellerSettlementsUseCase;
import com.gole.api.order.application.port.in.OpenDisputeUseCase;
import com.gole.api.order.application.port.in.PayOrderUseCase;
import com.gole.api.order.application.port.in.PlaceOrderUseCase;
import com.gole.api.order.application.port.in.RefundOrderUseCase;
import com.gole.api.order.domain.model.Order;
import com.gole.api.order.domain.model.PhoneNumber;
import com.gole.api.shipping.application.port.in.GetShipmentUseCase;
import com.gole.api.shipping.domain.model.Carrier;
import com.gole.api.shipping.domain.model.Shipment;
import com.gole.api.shipping.domain.model.WaybillNumber;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class OrderControllerConsentTest {

    private final GetOrderUseCase orders = mock(GetOrderUseCase.class);
    private final GetShipmentUseCase shipments = mock(GetShipmentUseCase.class);
    private final ThirdPartyProvisionConsentService consents = mock(ThirdPartyProvisionConsentService.class);
    private final PlaceOrderUseCase placeOrders = mock(PlaceOrderUseCase.class);
    private final GetListingUseCase listings = mock(GetListingUseCase.class);
    private final SellerIdentityVerificationService sellerIdentityVerification =
            mock(SellerIdentityVerificationService.class);
    private OrderController controller;

    @BeforeEach
    void setUp() {
        controller = new OrderController(
                placeOrders,
                mock(PayOrderUseCase.class),
                mock(CompleteOrderUseCase.class),
                mock(RefundOrderUseCase.class),
                orders,
                mock(OpenDisputeUseCase.class),
                shipments,
                mock(GetSellerSettlementsUseCase.class),
                consents,
                listings,
                sellerIdentityVerification);
    }

    @Test
    void newOrderRequiresTheListingSellersVerifiedIdentity() {
        Listing listing = mock(Listing.class);
        when(listing.getSellerId()).thenReturn("seller-1");
        when(listings.getById("listing-1")).thenReturn(listing);
        doThrow(new ServiceUnavailableException(
                        "SELLER_IDENTITY_VERIFICATION_UNAVAILABLE", "seller verification unavailable"))
                .when(sellerIdentityVerification)
                .requireVerifiedSeller("seller-1");

        assertThatThrownBy(() -> controller.place(
                        new OrderRequests.PlaceOrderRequest("listing-1", null, "01011112222"),
                        authenticated("buyer-1")))
                .isInstanceOf(ServiceUnavailableException.class);

        verify(placeOrders, never()).place(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void counterpartContactRequiresConsentAndReturnsOnlyTheOtherPartysPhone() {
        Order order = order();
        when(orders.getById("order-1")).thenReturn(order);
        when(shipments.getByOrderId("order-1")).thenReturn(Optional.of(shipment()));

        doThrow(new ForbiddenException(ThirdPartyProvisionConsentService.REQUIRED_CODE, "consent required"))
                .when(consents)
                .requireCurrent("buyer-1");
        assertThatThrownBy(() -> controller.contacts("order-1", authenticated("buyer-1")))
                .isInstanceOf(ForbiddenException.class);
        verify(shipments, never()).getByOrderId("order-1");

        var sellerView = controller.contacts("order-1", authenticated("seller-1"));
        assertThat(sellerView.counterpartPhone()).isEqualTo("01011112222");
        assertThat(sellerView).hasNoNullFieldsOrProperties();
        verify(consents).requireCurrent("seller-1");
        verify(consents).requireCurrentSubject("buyer-1");
    }

    @Test
    void counterpartContactRequiresThePhoneOwnersCurrentConsentInBothDirections() {
        when(orders.getById("order-1")).thenReturn(order());
        doThrow(new ForbiddenException(ThirdPartyProvisionConsentService.SUBJECT_REQUIRED_CODE, "subject consent"))
                .when(consents)
                .requireCurrentSubject("seller-1");

        assertThatThrownBy(() -> controller.contacts("order-1", authenticated("buyer-1")))
                .isInstanceOf(ForbiddenException.class)
                .extracting("code")
                .isEqualTo(ThirdPartyProvisionConsentService.SUBJECT_REQUIRED_CODE);
        verify(shipments, never()).getByOrderId("order-1");

        org.mockito.Mockito.reset(consents);
        doThrow(new ForbiddenException(ThirdPartyProvisionConsentService.SUBJECT_REQUIRED_CODE, "subject consent"))
                .when(consents)
                .requireCurrentSubject("buyer-1");
        assertThatThrownBy(() -> controller.contacts("order-1", authenticated("seller-1")))
                .isInstanceOf(ForbiddenException.class)
                .extracting("code")
                .isEqualTo(ThirdPartyProvisionConsentService.SUBJECT_REQUIRED_CODE);
        verify(shipments, never()).getByOrderId("order-1");

        org.mockito.Mockito.reset(consents);
        when(shipments.getByOrderId("order-1")).thenReturn(Optional.of(shipment()));
        var buyerView = controller.contacts("order-1", authenticated("buyer-1"));
        assertThat(buyerView.counterpartPhone()).isEqualTo("010-3333-4444");
        verify(consents).requireCurrent("buyer-1");
        verify(consents).requireCurrentSubject("seller-1");
    }

    @Test
    void missingConsentHasStableHttpErrorCodeForApiBypassProtection() throws Exception {
        when(orders.getById("order-1")).thenReturn(order());
        doThrow(new ForbiddenException(ThirdPartyProvisionConsentService.REQUIRED_CODE, "consent required"))
                .when(consents)
                .requireCurrent("buyer-1");
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler(mock(OperationalEventPublisher.class)))
                .build();

        mvc.perform(get("/api/v1/orders/order-1/contacts").requestAttr(UserAuthInterceptor.ATTR_ACCOUNT_ID, "buyer-1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ThirdPartyProvisionConsentService.REQUIRED_CODE));
    }

    @Test
    void ownContactReturnsOnlyOwnPhoneWithoutThirdPartyConsent() {
        when(orders.getById("order-1")).thenReturn(order());

        var response = controller.ownContact("order-1", authenticated("buyer-1"));

        assertThat(response.phone()).isEqualTo("01011112222");
        verify(consents, never()).requireCurrent("buyer-1");
    }

    @Test
    void unrelatedAccountIsRejectedBeforeConsentStatusIsChecked() {
        when(orders.getById("order-1")).thenReturn(order());

        assertThatThrownBy(() -> controller.contacts("order-1", authenticated("outsider")))
                .isInstanceOf(ForbiddenException.class)
                .extracting("code")
                .isEqualTo("ORDER_ACCESS_DENIED");
        verify(consents, never()).requireCurrent("outsider");
    }

    private static Order order() {
        return Order.place(
                "order-1",
                "listing-1",
                "buyer-1",
                "seller-1",
                "10305",
                "used_good",
                15_000,
                new PhoneNumber("010-1111-2222"),
                Instant.parse("2026-09-04T00:00:00Z"));
    }

    private static Shipment shipment() {
        return Shipment.register(
                "shipment-1",
                "order-1",
                "seller-1",
                "buyer-1",
                "010-3333-4444",
                Carrier.CJ_LOGISTICS,
                new WaybillNumber("1234567890"),
                Instant.parse("2026-09-04T00:00:00Z"));
    }

    private static MockHttpServletRequest authenticated(String accountId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(UserAuthInterceptor.ATTR_ACCOUNT_ID, accountId);
        return request;
    }
}
