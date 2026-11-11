package com.kahga.pluse.fieldagent.controller;

import com.kahga.pluse.common.response.ApiResponse;
import com.kahga.pluse.fieldagent.dto.AgentAccessState;
import com.kahga.pluse.fieldagent.dto.AreaCountDto;
import com.kahga.pluse.fieldagent.dto.BulkPaymentRequest;
import com.kahga.pluse.fieldagent.dto.BulkPaymentResult;
import com.kahga.pluse.fieldagent.dto.BulkReportsAccessRequest;
import com.kahga.pluse.fieldagent.dto.BulkReportsAccessResult;
import com.kahga.pluse.fieldagent.dto.UpdateReportsAccessRequest;
import com.kahga.pluse.fieldagent.dto.FieldAgentDto;
import com.kahga.pluse.fieldagent.dto.FieldAgentFilter;
import com.kahga.pluse.fieldagent.dto.FieldAgentPageDto;
import com.kahga.pluse.fieldagent.dto.FieldAgentSort;
import com.kahga.pluse.fieldagent.dto.MarkPaymentRequest;
import com.kahga.pluse.fieldagent.dto.ResetPasswordRequest;
import com.kahga.pluse.fieldagent.dto.ResetPasswordResult;
import com.kahga.pluse.fieldagent.dto.UpdateReferralRequest;
import com.kahga.pluse.fieldagent.service.FieldAgentCsv;
import com.kahga.pluse.fieldagent.service.FieldAgentService;
import com.kahga.pluse.location.entity.UnitLevel;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * FR-A20, the admin's Field agents page.
 *
 * <p>SecurityConfig has already refused anyone who is not an admin. Which
 * agents this admin may see is decided in the service from their own account,
 * so the district, block and panchayat parameters here only narrow what they
 * were already allowed to read.
 */
@RestController
@RequestMapping("/api/admin/field-agents")
@RequiredArgsConstructor
public class FieldAgentController {

    private final FieldAgentService fieldAgentService;
    private final FieldAgentCsv fieldAgentCsv;

    @GetMapping
    public ApiResponse<FieldAgentPageDto> list(
            @RequestParam(required = false) UUID districtId,
            @RequestParam(required = false) UUID blockId,
            @RequestParam(required = false) UUID panchayatId,
            @RequestParam(required = false) AgentAccessState access,
            @RequestParam(required = false) Boolean paid,
            @RequestParam(required = false) Boolean reports,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) FieldAgentSort sort,
            @RequestParam(defaultValue = "false") boolean desc,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size) {

        return ApiResponse.ok(fieldAgentService.page(
                filter(districtId, blockId, panchayatId, access, paid, reports, q, sort, desc, page, size)));
    }

    /** FA-3: the options for one dropdown, each with the agents behind it. */
    @GetMapping("/areas")
    public ApiResponse<List<AreaCountDto>> areas(
            @RequestParam UnitLevel level, @RequestParam(required = false) UUID parentId) {
        return ApiResponse.ok(fieldAgentService.areaCounts(level, parentId));
    }

    @GetMapping("/{id}")
    public ApiResponse<FieldAgentDto> one(@PathVariable UUID id) {
        return ApiResponse.ok(fieldAgentService.require(id));
    }

    @PutMapping("/{id}/payment")
    public ApiResponse<FieldAgentDto> markPayment(
            @PathVariable UUID id, @Valid @RequestBody MarkPaymentRequest request) {
        FieldAgentDto agent = fieldAgentService.mark(id, request);
        return ApiResponse.ok(agent, request.paid() ? "Marked as paid" : "Marked as not paid");
    }

    @PostMapping("/payment/bulk")
    public ApiResponse<BulkPaymentResult> markMany(@Valid @RequestBody BulkPaymentRequest request) {
        FieldAgentFilter filter = filter(
                request.districtId(),
                request.blockId(),
                request.panchayatId(),
                request.access(),
                request.paidFilter(),
                null,
                request.search(),
                null,
                false,
                0,
                null);
        BulkPaymentResult result = fieldAgentService.markMany(request, filter);
        return ApiResponse.ok(
                result,
                "%d agent(s) marked as %s".formatted(result.agents(), result.paid() ? "paid" : "not paid"));
    }

    /**
     * FA-19/FA-25. The response carries the password in plain text once, for
     * the admin to read out; it is never readable again and never exported.
     */
    @PostMapping("/{id}/password-reset")
    public ApiResponse<ResetPasswordResult> resetPassword(
            @PathVariable UUID id, @Valid @RequestBody(required = false) ResetPasswordRequest request) {
        ResetPasswordResult result = fieldAgentService.resetPassword(id, request == null ? null : request.password());
        return ApiResponse.ok(result, "Password reset — give it to the agent now, it is not shown again");
    }

    /** FR-A22 / AD-1: gives or removes one agent's Reports page. */
    @PutMapping("/{id}/reports-access")
    public ApiResponse<FieldAgentDto> updateReportsAccess(
            @PathVariable UUID id, @Valid @RequestBody UpdateReportsAccessRequest request) {
        FieldAgentDto agent = fieldAgentService.updateReportsAccess(id, request.enabled());
        return ApiResponse.ok(agent, request.enabled() ? "Reports access given" : "Reports access removed");
    }

    /** AD-4: the same for ticked rows, or for every agent the filters match. */
    @PostMapping("/reports-access/bulk")
    public ApiResponse<BulkReportsAccessResult> updateReportsAccessMany(
            @Valid @RequestBody BulkReportsAccessRequest request) {

        FieldAgentFilter filter = filter(
                request.districtId(),
                request.blockId(),
                request.panchayatId(),
                request.access(),
                request.paidFilter(),
                request.reportsFilter(),
                request.search(),
                null,
                false,
                0,
                null);
        BulkReportsAccessResult result = fieldAgentService.updateReportsAccessMany(request, filter);
        return ApiResponse.ok(
                result,
                "%d agent(s) %s the Reports page"
                        .formatted(result.agents(), result.enabled() ? "given" : "no longer have"));
    }

    /** The referral number, set or corrected by an admin; blank clears it. */
    @PutMapping("/{id}/referral")
    public ApiResponse<FieldAgentDto> updateReferral(
            @PathVariable UUID id, @Valid @RequestBody UpdateReferralRequest request) {
        return ApiResponse.ok(
                fieldAgentService.updateReferral(id, request.referralNo()),
                request.referralNo() == null || request.referralNo().isBlank()
                        ? "Referral cleared"
                        : "Referral saved");
    }

    /** FA-24: the agent's ask, answered without setting a password. */
    @DeleteMapping("/{id}/password-request")
    public ApiResponse<FieldAgentDto> dismissPasswordRequest(@PathVariable UUID id) {
        return ApiResponse.ok(fieldAgentService.dismissPasswordRequest(id), "Request dismissed");
    }

    /** FA-16. Exactly the agents the current filters match, nothing else. */
    @GetMapping(value = "/export.csv", produces = "text/csv")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) UUID districtId,
            @RequestParam(required = false) UUID blockId,
            @RequestParam(required = false) UUID panchayatId,
            @RequestParam(required = false) AgentAccessState access,
            @RequestParam(required = false) Boolean paid,
            @RequestParam(required = false) Boolean reports,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) FieldAgentSort sort,
            @RequestParam(defaultValue = "false") boolean desc) {

        List<FieldAgentDto> agents = fieldAgentService.allMatching(
                filter(districtId, blockId, panchayatId, access, paid, reports, q, sort, desc, 0, null));
        byte[] body = fieldAgentCsv.build(agents).getBytes(StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename("field-agents-%s.csv".formatted(LocalDate.now()))
                                .build()
                                .toString())
                .body(body);
    }

    private FieldAgentFilter filter(
            UUID districtId,
            UUID blockId,
            UUID panchayatId,
            AgentAccessState access,
            Boolean paid,
            Boolean reports,
            String q,
            FieldAgentSort sort,
            boolean desc,
            int page,
            Integer size) {

        return new FieldAgentFilter(
                fieldAgentService.areaUnitIds(districtId, blockId, panchayatId),
                access,
                paid,
                reports,
                q,
                sort == null ? FieldAgentSort.NAME : sort,
                desc,
                Math.max(0, page),
                fieldAgentService.pageSize(size));
    }
}
