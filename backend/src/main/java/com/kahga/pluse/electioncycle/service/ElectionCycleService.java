package com.kahga.pluse.electioncycle.service;

import com.kahga.pluse.common.exception.BusinessException;
import com.kahga.pluse.common.exception.NotFoundException;
import com.kahga.pluse.electioncycle.dto.CreateElectionCycleRequest;
import com.kahga.pluse.electioncycle.entity.CycleStatus;
import com.kahga.pluse.electioncycle.entity.ElectionCycle;
import com.kahga.pluse.electioncycle.repository.ElectionCycleRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR-A11. One cycle is open at a time and everything recorded lands in it.
 * Closing a cycle keeps its data — that retention is what lets the next campaign
 * compare itself with the last one.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ElectionCycleService {

    private final ElectionCycleRepository electionCycleRepository;

    public List<ElectionCycle> list() {
        return electionCycleRepository.findAll();
    }

    public ElectionCycle require(UUID id) {
        return electionCycleRepository.findById(id).orElseThrow(() -> new NotFoundException("Unknown election cycle"));
    }

    /**
     * The cycle new entries are tagged with. There is always one: the migration
     * opens "Cycle 1", and a cycle is only ever closed by opening the next.
     */
    public ElectionCycle current() {
        return electionCycleRepository
                .findOpen()
                .orElseThrow(() -> new BusinessException(
                        "No election cycle is open — open one before recording sentiment", HttpStatus.CONFLICT));
    }

    /**
     * Opens the next cycle and closes the current one, so two cycles never
     * collect entries at the same time and "which cycle is this?" has one answer.
     */
    @Transactional
    public ElectionCycle open(CreateElectionCycleRequest request) {
        electionCycleRepository.findOpen().ifPresent(open -> {
            open.setStatus(CycleStatus.CLOSED);
            open.setClosedAt(Instant.now());
            electionCycleRepository.save(open);
        });

        return electionCycleRepository.save(ElectionCycle.builder()
                .id(UUID.randomUUID())
                .name(request.name().trim())
                .electionType(request.electionType())
                .year(request.year())
                .status(CycleStatus.OPEN)
                .createdAt(Instant.now())
                .build());
    }
}
