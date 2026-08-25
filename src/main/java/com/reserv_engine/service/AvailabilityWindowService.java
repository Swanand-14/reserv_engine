package com.reserv_engine.service;

import com.reserv_engine.entity.AvailabilityWindow;
import com.reserv_engine.repository.AvailabilityWindowRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class AvailabilityWindowService {

    private final AvailabilityWindowRepository availabilityWindowRepository;

    public AvailabilityWindowService(AvailabilityWindowRepository availabilityWindowRepository) {
        this.availabilityWindowRepository = availabilityWindowRepository;
    }

    @Transactional
    public AvailabilityWindow create(String ownerId, LocalDateTime startTime, LocalDateTime endTime) {
        AvailabilityWindow window = new AvailabilityWindow(ownerId, startTime, endTime);
        return availabilityWindowRepository.save(window);
    }
}