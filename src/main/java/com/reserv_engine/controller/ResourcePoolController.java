package com.reserv_engine.controller;

import com.reserv_engine.dto.CreateResourcePoolRequest;
import com.reserv_engine.dto.ResourcePoolResponse;
import com.reserv_engine.entity.ResourcePool;
import com.reserv_engine.security.SecurityUtils;
import com.reserv_engine.service.ResourcePoolService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/availability-windows/{windowId}/pools")
public class ResourcePoolController {

    private final ResourcePoolService resourcePoolService;

    public ResourcePoolController(ResourcePoolService resourcePoolService) {
        this.resourcePoolService = resourcePoolService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<ResourcePoolResponse> create(@PathVariable String windowId,
                                                       @Valid @RequestBody CreateResourcePoolRequest request) {
        String currentUserId = SecurityUtils.currentUserId();
        ResourcePool pool = resourcePoolService.create(windowId, currentUserId, request.poolMode(), request.totalCapacity());
        return ResponseEntity.status(HttpStatus.CREATED).body(ResourcePoolResponse.from(pool));
    }
}