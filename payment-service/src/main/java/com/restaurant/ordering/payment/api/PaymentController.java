package com.restaurant.ordering.payment.api;

import com.restaurant.ordering.payment.api.PaymentDtos.BillView;
import com.restaurant.ordering.payment.api.PaymentDtos.PayRequest;
import com.restaurant.ordering.payment.api.PaymentDtos.SplitView;
import com.restaurant.ordering.payment.api.PaymentDtos.TipRequest;
import com.restaurant.ordering.payment.app.PaymentAppService;
import com.restaurant.ordering.security.Claims;
import com.restaurant.ordering.security.Roles;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentAppService paymentService;

    public PaymentController(PaymentAppService paymentService) {
        this.paymentService = paymentService;
    }

    /** Opens the bill, snapshotting the order total. Idempotent. */
    @PostMapping("/bills/{orderId}")
    public BillView open(@PathVariable Long orderId, @AuthenticationPrincipal Jwt jwt) {
        BillView bill = paymentService.openBill(orderId, jwt.getTokenValue());
        requireStaffOrOwnTable(jwt, bill.tableId());
        return bill;
    }

    @GetMapping("/bills/{orderId}")
    public BillView get(@PathVariable Long orderId, @AuthenticationPrincipal Jwt jwt) {
        BillView bill = paymentService.getBill(orderId);
        requireStaffOrOwnTable(jwt, bill.tableId());
        return bill;
    }

    @PostMapping("/bills/{orderId}/tip")
    public BillView tip(@PathVariable Long orderId,
                        @Valid @RequestBody TipRequest request,
                        @AuthenticationPrincipal Jwt jwt) {
        requireStaffOrOwnTable(jwt, paymentService.getBill(orderId).tableId());
        return paymentService.setTip(orderId, request.percent(), request.amountCents());
    }

    /**
     * Previews an even split. Charges nothing — the customer is choosing how to divide it.
     */
    @GetMapping("/bills/{orderId}/split")
    public SplitView split(@PathVariable Long orderId,
                           @RequestParam @Min(1) @Max(20) int ways,
                           @AuthenticationPrincipal Jwt jwt) {
        requireStaffOrOwnTable(jwt, paymentService.getBill(orderId).tableId());
        return paymentService.splitEvenly(orderId, ways);
    }

    /** Charges one share (or the whole bill). Called once per payer on a split. */
    @PostMapping("/bills/{orderId}/pay")
    public BillView pay(@PathVariable Long orderId,
                        @Valid @RequestBody PayRequest request,
                        @AuthenticationPrincipal Jwt jwt) {
        requireStaffOrOwnTable(jwt, paymentService.getBill(orderId).tableId());
        return paymentService.pay(orderId, request.amountCents(), request.tipCents(), request.cardNumber());
    }

    private void requireStaffOrOwnTable(Jwt jwt, Long tableId) {
        if (!Roles.CUSTOMER.equals(jwt.getClaimAsString(Claims.ROLE))) {
            return;
        }
        Object claim = jwt.getClaim(Claims.TABLE_ID);
        Long ownTableId = claim instanceof Number number ? number.longValue() : null;
        if (!tableId.equals(ownTableId)) {
            throw new AccessDeniedException("Session is scoped to table " + ownTableId);
        }
    }
}
