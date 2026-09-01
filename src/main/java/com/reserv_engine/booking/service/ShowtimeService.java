package com.reserv_engine.booking.service;

import com.reserv_engine.booking.entity.Event;
import com.reserv_engine.booking.entity.Hall;
import com.reserv_engine.booking.entity.Showtime;
import com.reserv_engine.booking.repository.EventRepository;
import com.reserv_engine.booking.repository.HallRepository;
import com.reserv_engine.booking.repository.ShowtimeRepository;
import com.reserv_engine.entity.AvailabilityWindow;
import com.reserv_engine.exception.ResourceNotFoundException;
import com.reserv_engine.service.AvailabilityWindowService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class ShowtimeService {

    private final ShowtimeRepository showtimeRepository;
    private final EventRepository eventRepository;
    private final HallRepository hallRepository;
    private final AvailabilityWindowService availabilityWindowService;

    public ShowtimeService(ShowtimeRepository showtimeRepository, EventRepository eventRepository,
                           HallRepository hallRepository, AvailabilityWindowService availabilityWindowService) {
        this.showtimeRepository = showtimeRepository;
        this.eventRepository = eventRepository;
        this.hallRepository = hallRepository;
        this.availabilityWindowService = availabilityWindowService;
    }

    @Transactional
    public Showtime create(String eventId, String currentUserId, String hallId,
                           LocalDateTime startTime, LocalDateTime endTime) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + eventId));
        if (!event.getOrganizer().getId().equals(currentUserId)) {
            throw new AccessDeniedException("You do not organize this Event");
        }

        Hall hall = hallRepository.findById(hallId)
                .orElseThrow(() -> new ResourceNotFoundException("Hall not found: " + hallId));
        if (!hall.getVenue().getManager().getId().equals(currentUserId)) {
            throw new AccessDeniedException("You do not manage the Venue for this Hall");
        }

        // Reuses the Engine's own service, unmodified — Showtime is just
        // another AvailabilityWindow owner as far as the Engine is concerned.
        AvailabilityWindow window = availabilityWindowService.create(currentUserId, startTime, endTime);

        return showtimeRepository.save(new Showtime(event, hall, window, startTime, endTime));
    }
}