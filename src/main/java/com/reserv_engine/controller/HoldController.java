package com.reserv_engine.controller;

import com.reserv_engine.dto.CreateHoldRequest;
import com.reserv_engine.dto.HoldResponse;
import com.reserv_engine.service.HoldService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/v1/holds")
@RequiredArgsConstructor
public class HoldController {

    private final HoldService holdService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public HoldResponse createHold(@RequestBody CreateHoldRequest request) {
        return holdService.createHold(request);
    }
}