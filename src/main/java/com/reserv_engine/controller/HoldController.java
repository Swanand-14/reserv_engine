package com.reserv_engine.controller;

import com.reserv_engine.dto.CreateHoldRequest;
import com.reserv_engine.dto.HoldResponse;
import com.reserv_engine.security.OwnershipGuard;
import com.reserv_engine.security.SecurityUtils;
import com.reserv_engine.service.HoldCancelService;
import com.reserv_engine.service.HoldService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/v1/holds")
@RequiredArgsConstructor
public class HoldController {

    private final HoldService holdService;
    private final HoldCancelService holdCancelService;
    private final OwnershipGuard ownershipGuard;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CUSTOMER')")
    public HoldResponse createHold(@RequestBody CreateHoldRequest request) {
        String currentUserId = SecurityUtils.currentUserId();
        CreateHoldRequest secured = new CreateHoldRequest(currentUserId, request.idempotencyKey(), request.lines());
        return holdService.createHold(secured);
    }
    @PostMapping("/{id}/cancel")
    @PreAuthorize("isAuthenticated()")
    public HoldResponse cancelHold(@PathVariable("id") String id) {
        ownershipGuard.assertOwnsHold(id, SecurityUtils.currentUserId());
        return holdCancelService.cancel(id);
    }
}