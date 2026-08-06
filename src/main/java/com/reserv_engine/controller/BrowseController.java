package com.reserv_engine.controller;

import com.reserv_engine.dto.AvailabilityWindowDto;
import com.reserv_engine.dto.ResourcePoolSummaryDto;
import com.reserv_engine.service.ResourceBrowseService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class BrowseController {
    private final ResourceBrowseService browseService;
    @GetMapping("/availability-windows")
    public Page<AvailabilityWindowDto> browseWindows(@PageableDefault(size = 20,sort="startTime")Pageable pageable){
        return browseService.browseUpcomingWindows(pageable);
    }
    @GetMapping("/availability-windows/{windowId}/pools")
    public List<ResourcePoolSummaryDto> browsePools(@PathVariable String windowId){
        return browseService.browsePoolsForWindow(windowId);
    }

}
