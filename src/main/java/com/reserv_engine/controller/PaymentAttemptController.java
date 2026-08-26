package com.reserv_engine.controller;

import com.reserv_engine.dto.CreatePaymentAttemptRequest;
import com.reserv_engine.dto.PaymentAttemptResponse;
import com.reserv_engine.security.OwnershipGuard;
import com.reserv_engine.security.SecurityUtils;
import com.reserv_engine.service.PaymentAttemptService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payment-attempts")
@RequiredArgsConstructor
public class PaymentAttemptController {

    private final PaymentAttemptService paymentAttemptService;
    private final OwnershipGuard ownershipGuard;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    public PaymentAttemptResponse attemptPayment(@RequestBody CreatePaymentAttemptRequest request) {
        ownershipGuard.assertOwnsHold(request.holdId(), SecurityUtils.currentUserId());
        return paymentAttemptService.attemptPayment(request);
    }
}