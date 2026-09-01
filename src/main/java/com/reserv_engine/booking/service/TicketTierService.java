package com.reserv_engine.booking.service;

import com.reserv_engine.booking.entity.Showtime;
import com.reserv_engine.booking.entity.TicketTier;
import com.reserv_engine.booking.repository.ShowtimeRepository;
import com.reserv_engine.booking.repository.TicketTierRepository;
import com.reserv_engine.core.domain.PoolMode;
import com.reserv_engine.entity.ResourcePool;
import com.reserv_engine.exception.ResourceNotFoundException;
import com.reserv_engine.service.ResourcePoolService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class TicketTierService {

    private final TicketTierRepository ticketTierRepository;
    private final ShowtimeRepository showtimeRepository;
    private final ResourcePoolService resourcePoolService;

    public TicketTierService(TicketTierRepository ticketTierRepository, ShowtimeRepository showtimeRepository,
                             ResourcePoolService resourcePoolService) {
        this.ticketTierRepository = ticketTierRepository;
        this.showtimeRepository = showtimeRepository;
        this.resourcePoolService = resourcePoolService;
    }

    @Transactional
    public TicketTier create(String showtimeId, String currentUserId, String name,
                             BigDecimal price, int totalCapacity) {
        Showtime showtime = showtimeRepository.findById(showtimeId)
                .orElseThrow(() -> new ResourceNotFoundException("Showtime not found: " + showtimeId));

        if (!showtime.getEvent().getOrganizer().getId().equals(currentUserId)) {
            throw new AccessDeniedException("You do not organize the Event for this Showtime");
        }

        // Seats are inherently discrete, so tiers are always UNIT_BASED —
        // no COUNTER_BASED tier makes sense in this domain.
        ResourcePool pool = resourcePoolService.create(
                showtime.getAvailabilityWindow().getId(), currentUserId, PoolMode.UNIT_BASED, totalCapacity);

        return ticketTierRepository.save(new TicketTier(showtime, pool, name, price));
    }
}