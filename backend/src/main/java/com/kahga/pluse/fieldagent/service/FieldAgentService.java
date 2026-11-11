package com.kahga.pluse.fieldagent.service;

import com.kahga.pluse.common.exception.BusinessException;
import com.kahga.pluse.common.exception.NotFoundException;
import com.kahga.pluse.fieldagent.dto.AreaCountDto;
import com.kahga.pluse.fieldagent.dto.BulkPaymentRequest;
import com.kahga.pluse.fieldagent.dto.BulkPaymentResult;
import com.kahga.pluse.fieldagent.dto.BulkReportsAccessRequest;
import com.kahga.pluse.fieldagent.dto.BulkReportsAccessResult;
import com.kahga.pluse.fieldagent.dto.FieldAgentDto;
import com.kahga.pluse.fieldagent.dto.FieldAgentFilter;
import com.kahga.pluse.fieldagent.dto.FieldAgentPageDto;
import com.kahga.pluse.fieldagent.dto.MarkPaymentRequest;
import com.kahga.pluse.fieldagent.dto.ResetPasswordResult;
import com.kahga.pluse.fieldagent.repository.FieldAgentRepository;
import com.kahga.pluse.location.entity.Unit;
import com.kahga.pluse.location.entity.UnitLevel;
import com.kahga.pluse.location.service.LocationService;
import com.kahga.pluse.user.entity.Role;
import com.kahga.pluse.security.CurrentUserService;
import com.kahga.pluse.user.entity.User;
import com.kahga.pluse.user.repository.UserRepository;
import com.kahga.pluse.user.service.AdminScopeService;
import com.kahga.pluse.user.service.AgentPasswordService;
import com.kahga.pluse.user.service.ReferralNumbers;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR-A20. Everything the Field agents page asks for, narrowed to what the
 * signed-in admin is allowed to see.
 *
 * <p>Scope is applied here and only here, from the admin's own account: the
 * district, block and panchayat on the request are a convenience for the admin,
 * never a grant of access. An admin who edits the request to name someone
 * else's block gets their own scope intersected with it, which is nothing.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FieldAgentService {

    /** FA-15. Also the ceiling on an export, so neither can be used to pull the whole table. */
    public static final int BULK_LIMIT = 10_000;

    private static final int MAX_PAGE_SIZE = 100;

    private final FieldAgentRepository fieldAgentRepository;
    private final LocationService locationService;
    private final AdminScopeService adminScopeService;
    private final CurrentUserService currentUserService;
    private final AgentPasswordService agentPasswordService;
    private final UserRepository userRepository;

    public FieldAgentPageDto page(FieldAgentFilter filter) {
        FieldAgentRepository.Counts counts = fieldAgentRepository.counts(filter);
        return new FieldAgentPageDto(
                fieldAgentRepository.page(filter),
                filter.page(),
                filter.size(),
                fieldAgentRepository.count(filter),
                counts.all(),
                counts.paid(),
                counts.unpaid());
    }

    public FieldAgentDto require(UUID id) {
        FieldAgentDto agent = fieldAgentRepository.find(id);
        if (agent == null) {
            throw new NotFoundException("Field agent not found");
        }
        requireInScope(id);
        return agent;
    }

    public List<FieldAgentDto> allMatching(FieldAgentFilter filter) {
        return fieldAgentRepository.allMatching(filter, BULK_LIMIT);
    }

    /**
     * Turns what the admin picked into the unit ids an agent's grant has to fall
     * inside, intersected with the admin's own scope.
     *
     * <p>Returns null only for an unrestricted admin who has picked nothing —
     * every other case is a real set, and an empty set means nothing matches
     * rather than everything.
     */
    public Collection<UUID> areaUnitIds(UUID districtId, UUID blockId, UUID panchayatId) {
        UUID picked = panchayatId != null ? panchayatId : blockId != null ? blockId : districtId;
        Set<UUID> scope = adminScopeService.unitIds();
        if (picked == null) {
            return scope;
        }
        Set<UUID> chosen = locationService.idsAtOrUnder(picked);
        if (scope == null) {
            return chosen;
        }
        Set<UUID> both = new HashSet<>(chosen);
        both.retainAll(scope);
        return both;
    }

    /**
     * The dropdown options one level at a time, each with its agent count
     * (FA-3). A scoped admin is offered only units inside their own area.
     */
    public List<AreaCountDto> areaCounts(UnitLevel level, UUID parentId) {
        Set<UUID> scope = adminScopeService.unitIds();
        Map<UUID, Set<UUID>> agentsByUnit = fieldAgentRepository.agentIdsByGrantUnit(scope);

        List<AreaCountDto> options = new ArrayList<>();
        for (Unit unit : locationService.find(level, parentId)) {
            if (scope != null && !scope.contains(unit.getId())) {
                continue;
            }
            Set<UUID> agents = new LinkedHashSet<>();
            for (UUID below : locationService.idsAtOrUnder(unit.getId())) {
                agents.addAll(agentsByUnit.getOrDefault(below, Set.of()));
            }
            options.add(new AreaCountDto(unit.getId(), unit.getName(), agents.size()));
        }
        return options;
    }

    /** FA-11, FA-12: one agent, after checking they are inside the caller's area. */
    @Transactional
    public FieldAgentDto mark(UUID agentId, MarkPaymentRequest payment) {
        require(agentId);
        validate(payment);
        fieldAgentRepository.mark(List.of(agentId), payment, currentUserService.user().getId());
        return fieldAgentRepository.find(agentId);
    }

    /**
     * FA-14, FA-15: one statement, all or nothing, for up to {@link #BULK_LIMIT}
     * agents.
     *
     * <p>"All matching" is resolved here, now, from the caller's own scope —
     * not from a list the browser put together — so the selection cannot reach
     * an agent the admin may not see. A list of ids is filtered the same way:
     * anything outside the scope is dropped rather than silently applied.
     */
    @Transactional
    public BulkPaymentResult markMany(BulkPaymentRequest request, FieldAgentFilter filter) {
        MarkPaymentRequest payment = new MarkPaymentRequest(
                request.paid(), request.amount(), request.paidOn(), request.mode(), request.reference());
        validate(payment);

        List<UUID> ids = request.selection() == BulkPaymentRequest.Selection.ALL_MATCHING
                ? fieldAgentRepository.idsMatching(filter, BULK_LIMIT + 1)
                : inScope(request.ids() == null ? List.of() : request.ids());

        if (ids.isEmpty()) {
            throw new BusinessException("No agents were selected");
        }
        if (ids.size() > BULK_LIMIT) {
            throw new BusinessException(
                    "One action covers at most " + BULK_LIMIT + " agents — narrow the filters and repeat");
        }
        int changed = fieldAgentRepository.mark(ids, payment, currentUserService.user().getId());
        BigDecimal total = payment.paid() && payment.amount() != null
                ? payment.amount().multiply(BigDecimal.valueOf(changed))
                : null;
        return new BulkPaymentResult(changed, payment.paid(), total);
    }

    /**
     * FA-19/FA-25: sets a temporary password and hands it back once, for the
     * admin to pass on. The agent must replace it at their next sign-in, and
     * any ask they had standing is answered by this.
     *
     * @param chosen the easy password the admin typed, or null for a suggestion
     */
    @Transactional
    public ResetPasswordResult resetPassword(UUID agentId, String chosen) {
        require(agentId);
        String password = agentPasswordService.reset(agent(agentId), chosen, currentUserService.user().getId());
        return new ResetPasswordResult(password, AgentPasswordService.TEMPORARY_VALID_DAYS);
    }

    /**
     * FR-A22 / AD-1: gives or removes one agent's Reports page.
     *
     * <p>Only this switch moves. An agent's booth approvals, login and download
     * switch are untouched (AD-8), and the page they get is still limited to
     * the booths those approvals already cover.
     */
    @Transactional
    public FieldAgentDto updateReportsAccess(UUID agentId, boolean enabled) {
        require(agentId);
        fieldAgentRepository.setReportsEnabled(
                List.of(agentId), enabled, currentUserService.user().getId());
        return fieldAgentRepository.find(agentId);
    }

    /**
     * AD-4: the same for a selection, or for every agent the filters match,
     * all-or-nothing in one statement.
     */
    @Transactional
    public BulkReportsAccessResult updateReportsAccessMany(
            BulkReportsAccessRequest request, FieldAgentFilter filter) {

        List<UUID> ids = request.selection() == BulkPaymentRequest.Selection.ALL_MATCHING
                ? fieldAgentRepository.idsMatching(filter, BULK_LIMIT + 1)
                : inScope(request.ids() == null ? List.of() : request.ids());

        if (ids.isEmpty()) {
            throw new BusinessException("No agents were selected");
        }
        if (ids.size() > BULK_LIMIT) {
            throw new BusinessException(
                    "One action covers at most " + BULK_LIMIT + " agents \u2014 narrow the filters and repeat");
        }
        int changed =
                fieldAgentRepository.setReportsEnabled(ids, request.enabled(), currentUserService.user().getId());
        return new BulkReportsAccessResult(changed, request.enabled());
    }

    /**
     * The referral an admin sets or corrects from the panel. Blank clears it,
     * which is the only way a wrong number recorded at sign-up can be undone.
     */
    @Transactional
    public FieldAgentDto updateReferral(UUID agentId, String referralNo) {
        require(agentId);
        User agent = agent(agentId);
        agent.setReferralNo(ReferralNumbers.normalise(referralNo, agent.getPhone()));
        userRepository.save(agent);
        return fieldAgentRepository.find(agentId);
    }

    /** FA-24: clears the ask without setting anything, for one dealt with in person. */
    @Transactional
    public FieldAgentDto dismissPasswordRequest(UUID agentId) {
        require(agentId);
        agentPasswordService.dismissRequest(agent(agentId));
        return fieldAgentRepository.find(agentId);
    }

    private User agent(UUID agentId) {
        return userRepository
                .findById(agentId)
                .filter(user -> user.getRole() == Role.FIELD_AGENT)
                .orElseThrow(() -> new NotFoundException("Field agent not found"));
    }

    /** The size the page may ask for, clamped so one request cannot pull the table. */
    public int pageSize(Integer requested) {
        int size = requested == null ? 25 : requested;
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }

    private void validate(MarkPaymentRequest payment) {
        if (!payment.paid()) {
            return;
        }
        if (payment.amount() == null || payment.amount().signum() <= 0) {
            throw new BusinessException("Enter the amount paid");
        }
        if (payment.mode() == null) {
            throw new BusinessException("Choose how the agent was paid");
        }
    }

    /** Keeps only the ids the caller may act on, so a tampered list cannot widen the reach. */
    private List<UUID> inScope(Collection<UUID> ids) {
        Set<UUID> scope = adminScopeService.unitIds();
        if (scope == null) {
            return List.copyOf(new LinkedHashSet<>(ids));
        }
        Map<UUID, Set<UUID>> agentsByUnit = fieldAgentRepository.agentIdsByGrantUnit(scope);
        Set<UUID> visible = new HashSet<>();
        agentsByUnit.values().forEach(visible::addAll);
        return ids.stream().distinct().filter(visible::contains).toList();
    }

    /** FA-N3: an admin may not read or change an agent outside their own area. */
    private void requireInScope(UUID agentId) {
        Set<UUID> scope = adminScopeService.unitIds();
        if (scope == null) {
            return;
        }
        boolean visible = fieldAgentRepository.agentIdsByGrantUnit(scope).values().stream()
                .anyMatch(agents -> agents.contains(agentId));
        if (!visible) {
            throw new BusinessException("That agent is outside your area", HttpStatus.FORBIDDEN);
        }
    }
}
