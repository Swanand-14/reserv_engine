package com.reserv_engine.controller;

import com.reserv_engine.dto.CreateHoldRequest;
import com.reserv_engine.dto.HoldResponse;
import com.reserv_engine.service.HoldCancelService;
import com.reserv_engine.service.HoldService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/v1/holds")
@RequiredArgsConstructor
public class HoldController {

    private final HoldService holdService;
    private final HoldCancelService holdCancelService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public HoldResponse createHold(@RequestBody CreateHoldRequest request) {
        return holdService.createHold(request);
    }
    @PostMapping("/{id}/cancel")
    public HoldResponse cancelHold(@PathVariable("id") String id) {
        return holdCancelService.cancel(id);
    }
}