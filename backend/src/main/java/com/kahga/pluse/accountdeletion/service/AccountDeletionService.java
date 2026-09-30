package com.kahga.pluse.accountdeletion.service;

import com.kahga.pluse.accessrequest.repository.AccessRequestRepository;
import com.kahga.pluse.accountdeletion.dto.CreateDeletionRequestDto;
import com.kahga.pluse.accountdeletion.entity.AccountDeletionRequest;
import com.kahga.pluse.accountdeletion.entity.DeletionStatus;
import com.kahga.pluse.accountdeletion.repository.AccountDeletionRequestRepository;
import com.kahga.pluse.auth.repository.OtpTokenRepository;
import com.kahga.pluse.common.exception.BusinessException;
import com.kahga.pluse.common.exception.NotFoundException;
import com.kahga.pluse.housesentiment.repository.HouseSentimentEntryRepository;
import com.kahga.pluse.sentiment.repository.SentimentEntryRepository;
import com.kahga.pluse.user.entity.Role;
import com.kahga.pluse.user.entity.User;
import com.kahga.pluse.user.repository.UserRepository;
import com.kahga.pluse.voterchangerequest.repository.VoterChangeRequestRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Account deletion (Play's "delete account" requirement): an agent asks from the
 * public page or from the app, and an admin approves. Approval erases the
 * account and everything that agent recorded.
 *
 * <p>Filing a request changes nothing on its own, so the public page is safe to
 * leave unauthenticated — the admin is the one who confirms the person asking is
 * the agent named, and only they can trigger the deletion.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountDeletionService {

    private final AccountDeletionRequestRepository deletionRepository;
    private final UserRepository userRepository;
    private final SentimentEntryRepository sentimentEntryRepository;
    private final HouseSentimentEntryRepository houseSentimentEntryRepository;
    private final VoterChangeRequestRepository voterChangeRequestRepository;
    private final AccessRequestRepository accessRequestRepository;
    private final OtpTokenRepository otpTokenRepository;

    public List<AccountDeletionRequest> list(DeletionStatus status) {
        return status == null
                ? deletionRepository.findAllByOrderByRequestedAtDesc()
                : deletionRepository.findByStatus(status);
    }

    public long countPending() {
        return deletionRepository.countByStatus(DeletionStatus.PENDING);
    }

    /**
     * Files a request from the public page. The reply is deliberately the same
     * whether or not the number belongs to an account, so the page cannot be
     * used to find out who is registered.
     */
    @Transactional
    public void request(CreateDeletionRequestDto payload) {
        String phone = payload.phone().trim();
        User agent = userRepository.findByPhone(phone).orElse(null);
        if (agent == null || agent.getRole() != Role.FIELD_AGENT) {
            log.info("Account deletion requested for a number with no field-agent account");
            return;
        }
        file(agent, payload.reason());
    }

    /** Files a request for the signed-in agent — the in-app route to the same queue. */
    @Transactional
    public AccountDeletionRequest requestFor(User agent, String reason) {
        if (agent.getRole() != Role.FIELD_AGENT) {
            throw new BusinessException("Administrator accounts are closed by another administrator, not here");
        }
        return file(agent, reason);
    }

    private AccountDeletionRequest file(User agent, String reason) {
        AccountDeletionRequest pending =
                deletionRepository.findPendingByPhone(agent.getPhone()).orElse(null);
        if (pending != null) {
            return pending; // Asking twice is not an error; the queue already holds it.
        }
        String clean = reason == null || reason.isBlank() ? null : reason.trim();
        return deletionRepository.save(AccountDeletionRequest.builder()
                .id(UUID.randomUUID())
                .user(agent)
                .agentName(agent.getName())
                .agentPhone(agent.getPhone())
                .agentEmail(agent.getEmail())
                .reason(clean)
                .status(DeletionStatus.PENDING)
                .requestedAt(Instant.now())
                .build());
    }

    /**
     * Approves the request: the agent's sentiment entries, house tallies, change
     * proposals, access requests and sign-in codes go, and then the account
     * itself. Only the request row survives, as the record of what was granted.
     */
    @Transactional
    public AccountDeletionRequest approve(User admin, UUID id, String note) {
        AccountDeletionRequest request = require(id);
        requirePending(request);

        User agent = request.getUser() == null
                ? null
                : userRepository.findById(request.getUser().getId()).orElse(null);
        int erased = 0;
        if (agent != null) {
            erased += sentimentEntryRepository.deleteByRecordedById(agent.getId());
            erased += houseSentimentEntryRepository.deleteByRecordedById(agent.getId());
            erased += voterChangeRequestRepository.deleteByAgentId(agent.getId());
            erased += accessRequestRepository.deleteByAgentId(agent.getId());
            otpTokenRepository.deleteByPhone(agent.getPhone());
            userRepository.delete(agent.getId());
            log.info("Deleted a field agent account and {} recorded entries on admin approval", erased);
        }

        request.setUser(null);
        request.setStatus(DeletionStatus.APPROVED);
        request.setReviewedAt(Instant.now());
        request.setReviewedBy(admin);
        request.setReviewerNote(clean(note));
        request.setDeletedEntries(erased);
        return deletionRepository.save(request);
    }

    @Transactional
    public AccountDeletionRequest reject(User admin, UUID id, String note) {
        AccountDeletionRequest request = require(id);
        requirePending(request);
        request.setStatus(DeletionStatus.REJECTED);
        request.setReviewedAt(Instant.now());
        request.setReviewedBy(admin);
        request.setReviewerNote(clean(note));
        return deletionRepository.save(request);
    }

    public AccountDeletionRequest require(UUID id) {
        return deletionRepository.findById(id).orElseThrow(() -> new NotFoundException("Deletion request not found"));
    }

    private void requirePending(AccountDeletionRequest request) {
        if (request.getStatus() != DeletionStatus.PENDING) {
            throw new BusinessException(
                    "This request was already " + request.getStatus().name().toLowerCase(), HttpStatus.CONFLICT);
        }
    }

    private String clean(String note) {
        return note == null || note.isBlank() ? null : note.trim();
    }
}
