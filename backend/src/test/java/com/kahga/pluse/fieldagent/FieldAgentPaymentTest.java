package com.kahga.pluse.fieldagent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kahga.pluse.common.exception.BusinessException;
import com.kahga.pluse.fieldagent.dto.BulkPaymentRequest;
import com.kahga.pluse.fieldagent.dto.BulkPaymentResult;
import com.kahga.pluse.fieldagent.dto.FieldAgentDto;
import com.kahga.pluse.fieldagent.dto.FieldAgentFilter;
import com.kahga.pluse.fieldagent.dto.FieldAgentPageDto;
import com.kahga.pluse.fieldagent.dto.FieldAgentSort;
import com.kahga.pluse.fieldagent.dto.MarkPaymentRequest;
import com.kahga.pluse.fieldagent.dto.PaymentMode;
import com.kahga.pluse.fieldagent.service.FieldAgentService;
import com.kahga.pluse.security.UserPrincipal;
import com.kahga.pluse.user.entity.Role;
import com.kahga.pluse.user.entity.User;
import com.kahga.pluse.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR-A20, the payment side: marking one agent (FA-11, FA-12) and marking a
 * selection (FA-14, FA-15).
 *
 * <p>Payment is one status per agent holding the last payment's details, so
 * what these assert above all is that marking an agent <em>not</em> paid leaves
 * nothing of the old payment behind to be read as current.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FieldAgentPaymentTest {

    private static final BigDecimal AMOUNT = new BigDecimal("1500.00");

    @Autowired
    private FieldAgentService fieldAgentService;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void signInAsSuperAdmin() {
        User admin = userRepository.findByRoleOrderByNameAsc(Role.SUPER_ADMIN).stream()
                .filter(User::isActive)
                .findFirst()
                .orElseThrow();
        var principal = UserPrincipal.from(admin);
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void markingOneAgentPaidRecordsTheAmountDateModeAndWhoDidIt() {
        UUID agentId = someone().id();

        FieldAgentDto paid = fieldAgentService.mark(
                agentId,
                new MarkPaymentRequest(true, AMOUNT, LocalDate.of(2026, 11, 5), PaymentMode.UPI, "UTR-99"));

        assertThat(paid.paid()).isTrue();
        assertThat(paid.paidAmount()).isEqualByComparingTo(AMOUNT);
        assertThat(paid.paidOn()).isEqualTo(LocalDate.of(2026, 11, 5));
        assertThat(paid.paidMode()).isEqualTo(PaymentMode.UPI);
        assertThat(paid.paidRef()).isEqualTo("UTR-99");
        assertThat(paid.paidUpdatedAt()).isNotNull();
        assertThat(paid.paidUpdatedByName()).isNotBlank();
    }

    /** FA-12: not paid means nothing is left that could be read as a payment. */
    @Test
    void markingAnAgentNotPaidClearsEveryPaymentDetail() {
        UUID agentId = someone().id();
        fieldAgentService.mark(
                agentId, new MarkPaymentRequest(true, AMOUNT, LocalDate.now(), PaymentMode.CASH, "slip 12"));

        FieldAgentDto cleared =
                fieldAgentService.mark(agentId, new MarkPaymentRequest(false, null, null, null, null));

        assertThat(cleared.paid()).isFalse();
        assertThat(cleared.paidAmount()).isNull();
        assertThat(cleared.paidOn()).isNull();
        assertThat(cleared.paidMode()).isNull();
        assertThat(cleared.paidRef()).isNull();
        // Who cleared it is still recorded; that is the audit, not the payment.
        assertThat(cleared.paidUpdatedAt()).isNotNull();
    }

    @Test
    void anAmountOrAModeIsRequiredToMarkSomeoneAsPaid() {
        UUID agentId = someone().id();

        assertThatThrownBy(() -> fieldAgentService.mark(
                        agentId, new MarkPaymentRequest(true, null, LocalDate.now(), PaymentMode.UPI, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("amount");

        assertThatThrownBy(() -> fieldAgentService.mark(
                        agentId, new MarkPaymentRequest(true, AMOUNT, LocalDate.now(), null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("how the agent was paid");
    }

    /** FA-14: ticked rows only, and the result says what it did. */
    @Test
    void aBulkActionOverTickedRowsMarksExactlyThoseRows() {
        List<FieldAgentDto> agents = page(null).items();
        List<UUID> two = agents.stream().limit(2).map(FieldAgentDto::id).toList();

        BulkPaymentResult result = fieldAgentService.markMany(bulk(BulkPaymentRequest.Selection.IDS, two), filter(null));

        assertThat(result.agents()).isEqualTo(2);
        assertThat(result.paid()).isTrue();
        assertThat(result.total()).isEqualByComparingTo(AMOUNT.multiply(BigDecimal.valueOf(2)));
        assertThat(page(true).total()).isEqualTo(2);
    }

    /**
     * FA-14 and acceptance criterion 4: "all matching" is resolved on the
     * server, so it reaches past the page the admin could see.
     */
    @Test
    void allMatchingMarksEveryAgentTheFiltersMatchNotOnlyTheVisiblePage() {
        long everyone = page(null).total();
        assertThat(everyone).as("the seed needs more agents than this page holds").isGreaterThan(1);

        // A page of one: anything paged-based would mark exactly one agent.
        BulkPaymentResult result = fieldAgentService.markMany(
                bulk(BulkPaymentRequest.Selection.ALL_MATCHING, null),
                new FieldAgentFilter(scope(), null, null, null, null, FieldAgentSort.NAME, false, 0, 1));

        assertThat(result.agents()).isEqualTo((int) everyone);
        assertThat(page(true).total()).isEqualTo(everyone);
        assertThat(page(false).total()).isZero();
    }

    /** The tab counts follow a bulk action, which is how the admin sees it worked. */
    @Test
    void theTabCountsFollowABulkAction() {
        long everyone = page(null).total();
        List<UUID> one = List.of(page(null).items().get(0).id());

        fieldAgentService.markMany(bulk(BulkPaymentRequest.Selection.IDS, one), filter(null));

        FieldAgentPageDto after = page(null);
        assertThat(after.paidCount()).isEqualTo(1);
        assertThat(after.unpaidCount()).isEqualTo(everyone - 1);
        assertThat(after.paidCount() + after.unpaidCount()).isEqualTo(after.allCount());
    }

    @Test
    void anEmptySelectionIsRefusedRatherThanSilentlyDoingNothing() {
        assertThatThrownBy(() -> fieldAgentService.markMany(
                        bulk(BulkPaymentRequest.Selection.IDS, List.of()), filter(null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("No agents were selected");
    }

    private BulkPaymentRequest bulk(BulkPaymentRequest.Selection selection, List<UUID> ids) {
        return new BulkPaymentRequest(
                selection, ids, null, null, null, null, null, null, true, AMOUNT, LocalDate.now(), PaymentMode.UPI,
                null);
    }

    private FieldAgentDto someone() {
        return page(null).items().get(0);
    }

    private FieldAgentPageDto page(Boolean paid) {
        return fieldAgentService.page(filter(paid));
    }

    private FieldAgentFilter filter(Boolean paid) {
        return new FieldAgentFilter(scope(), null, paid, null, null, FieldAgentSort.NAME, false, 0, 100);
    }

    private java.util.Collection<UUID> scope() {
        return fieldAgentService.areaUnitIds(null, null, null);
    }
}
