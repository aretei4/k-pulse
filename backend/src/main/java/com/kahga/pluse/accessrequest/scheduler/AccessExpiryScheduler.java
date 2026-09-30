package com.kahga.pluse.accessrequest.scheduler;

import com.kahga.pluse.accessrequest.entity.AccessRequest;
import com.kahga.pluse.accessrequest.entity.AccessRequestStatus;
import com.kahga.pluse.accessrequest.repository.AccessRequestRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The one piece of backend work no API call triggers: flipping lapsed grants to
 * EXPIRED so they stop showing as live access. Reads still check `isLive()`, so
 * this is bookkeeping rather than the enforcement point.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccessExpiryScheduler {

    private final AccessRequestRepository accessRequestRepository;

    @Scheduled(cron = "0 15 1 * * *")
    @Transactional
    public void expireLapsedGrants() {
        List<AccessRequest> lapsed =
                accessRequestRepository.findByStatusAndExpiresAtBefore(AccessRequestStatus.APPROVED, Instant.now());
        if (lapsed.isEmpty()) {
            return;
        }
        lapsed.forEach(request -> request.setStatus(AccessRequestStatus.EXPIRED));
        accessRequestRepository.saveAll(lapsed);
        log.info("Expired {} access grant(s)", lapsed.size());
    }
}
