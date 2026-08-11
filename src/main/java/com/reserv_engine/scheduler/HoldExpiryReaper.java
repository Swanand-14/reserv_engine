package com.reserv_engine.scheduler;

import com.reserv_engine.core.domain.HoldStatus;
import com.reserv_engine.entity.Hold;
import com.reserv_engine.repository.HoldRepository;
import com.reserv_engine.service.HoldExpiryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class HoldExpiryReaper {
    private static final int BATCH_SIZE = 50;
    private final HoldRepository holdRepository;
    private final HoldExpiryService holdExpiryService;

    @Scheduled(fixedDelay = 30_000)
    public void releaseExpiredHolds(){
        List<Hold> candidates = holdRepository.findExpiredHolds(HoldStatus.ACTIVE, LocalDateTime.now(), PageRequest.of(0,BATCH_SIZE));
        if (candidates.isEmpty()) {
            return;
        }

        log.info("HoldExpiryReaper: releasing {} expired hold(s)", candidates.size());

        for (Hold hold : candidates) {
            try {
                holdExpiryService.releaseHold(hold.getId());
            } catch (Exception ex) {
                log.warn("HoldExpiryReaper: failed to release hold {} — will retry next run",
                        hold.getId(), ex);
            }
        }

    }

}
