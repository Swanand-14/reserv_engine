package com.reserv_engine.service;

import com.reserv_engine.core.domain.PoolMode;
import com.reserv_engine.entity.AvailabilityWindow;
import com.reserv_engine.entity.ResourcePool;
import com.reserv_engine.entity.ResourceUnit;
import com.reserv_engine.exception.ResourceNotFoundException;
import com.reserv_engine.repository.AvailabilityWindowRepository;
import com.reserv_engine.repository.ResourcePoolRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResourcePoolService {

    private final ResourcePoolRepository resourcePoolRepository;
    private final AvailabilityWindowRepository availabilityWindowRepository;

    public ResourcePoolService(ResourcePoolRepository resourcePoolRepository,
                               AvailabilityWindowRepository availabilityWindowRepository) {
        this.resourcePoolRepository = resourcePoolRepository;
        this.availabilityWindowRepository = availabilityWindowRepository;
    }

    @Transactional
    public ResourcePool create(String windowId, String currentUserId, PoolMode poolMode, int totalCapacity) {
        AvailabilityWindow window = availabilityWindowRepository.findById(windowId)
                .orElseThrow(() -> new ResourceNotFoundException("AvailabilityWindow not found: " + windowId));

        if (!window.getOwnerId().equals(currentUserId)) {
            throw new AccessDeniedException("You do not own this AvailabilityWindow");
        }

        ResourcePool pool = new ResourcePool(window, window.getOwnerId(), poolMode, totalCapacity);

        if (poolMode == PoolMode.UNIT_BASED) {
            for (int i = 0; i < totalCapacity; i++) {
                pool.addResourceUnit(new ResourceUnit(pool));
            }
        }

        return resourcePoolRepository.save(pool);
    }
}