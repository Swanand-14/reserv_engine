package com.reserv_engine.service;

import com.reserv_engine.dto.CreateHoldRequest;
import com.reserv_engine.dto.HoldLineResponse;
import com.reserv_engine.dto.HoldResponse;
import com.reserv_engine.entity.Hold;
import com.reserv_engine.repository.HoldRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * [Engine] Public-facing Hold creation API.
 *
 * Deliberately NOT @Transactional. Earlier this method WAS
 * @Transactional(readOnly = true) to keep a Hibernate session open across
 * both lookup paths — but that was the actual bug: under MySQL's default
 * REPEATABLE READ isolation, a single transaction's plain reads all share
 * ONE consistent snapshot, established at that transaction's FIRST read.
 * Since the initial idempotency pre-check below was that first read, the
 * LATER recovery lookup (in the catch block, after losing a commit race)
 * was still looking at the snapshot from BEFORE the winning request had
 * committed — so it correctly, by MySQL's own rules, found nothing, and
 * incorrectly rethrew the original exception instead of recovering.
 *
 * The fix: both lookups now use findByIdempotencyKeyWithLines, which
 * JOIN FETCHes holdLines so the result never needs lazy-loading — so
 * neither read needs an open session afterward, and each is free to be
 * its OWN short, independent transaction (Spring Data's default behavior
 * for a repository query method with no ambient transaction). Each one
 * gets a fresh snapshot at the moment it actually runs, which is exactly
 * what the recovery path needs to see the winner's just-committed Hold.
 */
@Service
@RequiredArgsConstructor
public class HoldService {

    private final HoldRepository holdRepository;
    private final HoldWriteService holdWriteService;

    public HoldResponse createHold(CreateHoldRequest request) {
        var existing = holdRepository.findByIdempotencyKeyWithLines(request.idempotencyKey());
        if (existing.isPresent()) {
            return toResponse(existing.get());
        }

        try {
            Hold created = holdWriteService.attemptCreate(request);
            return toResponse(created);
        } catch (DataIntegrityViolationException ex) {
            return holdRepository.findByIdempotencyKeyWithLines(request.idempotencyKey())
                    .map(this::toResponse)
                    .orElseThrow(() -> ex); // extremely unlikely: not found, rethrow original
        }
    }

    private HoldResponse toResponse(Hold hold) {
        var lines = hold.getHoldLines().stream()
                .map(line -> new HoldLineResponse(
                        line.getResourcePool().getId(),
                        line.getResourceUnit() != null ? line.getResourceUnit().getId() : null,
                        line.getQuantity()
                ))
                .toList();
        return new HoldResponse(hold.getId(), hold.getStatus().name(), hold.getExpiresAt(), lines);
    }
}