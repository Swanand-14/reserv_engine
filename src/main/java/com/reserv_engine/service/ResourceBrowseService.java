package com.reserv_engine.service;

import com.reserv_engine.dto.AvailabilityWindowDto;
import com.reserv_engine.dto.ResourcePoolSummaryDto;
import com.reserv_engine.entity.ResourcePool;
import com.reserv_engine.repository.AvailabilityWindowRepository;
import com.reserv_engine.repository.ResourcePoolRepository;
import com.reserv_engine.repository.ResourceUnitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ResourceBrowseService {
    private final AvailabilityWindowRepository windowRepository;
    private final ResourcePoolRepository poolRepository;
    private final ResourceUnitRepository unitRepository;
    @Transactional(readOnly = true)
    public Page<AvailabilityWindowDto> browseUpcomingWindows(Pageable pageable) {
        return windowRepository
                .findByStartTimeAfterOrderByStartTimeAsc(LocalDateTime.now(), pageable)
                .map(w -> new AvailabilityWindowDto(w.getId(), w.getStartTime(), w.getEndTime()));
    }

    @Transactional(readOnly = true)
    public List<ResourcePoolSummaryDto> browsePoolsForWindow(String windowId) {
        List<ResourcePool> pools = poolRepository.findByAvailabilityWindow_Id(windowId);
        return pools.stream()
                .map(pool -> new ResourcePoolSummaryDto(
                        pool.getId(),
                        pool.getRemainingCapacity()
                ))
                .toList();
    }





}
