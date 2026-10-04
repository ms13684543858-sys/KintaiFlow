package com.example.kintaiflow.controller;

import com.example.kintaiflow.dto.ApprovalCommentRequest;
import com.example.kintaiflow.dto.ApprovalResultResponse;
import com.example.kintaiflow.dto.PendingApprovalListResponse;
import com.example.kintaiflow.service.ApprovalService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** ONL-009 承認待ち一覧 / ONL-010 承認 / ONL-011 差戻し / ONL-012 却下。業務判断は ApprovalService に委譲する。 */
@RestController
@RequestMapping("/api/approvals")
public class ApprovalController {

    private final ApprovalService approvalService;

    public ApprovalController(ApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    /** ONL-009 承認待ち一覧。 */
    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public PendingApprovalListResponse pending(@RequestParam(name = "requestType", required = false) String requestType,
                                               @RequestParam(name = "stepNo", required = false) Integer stepNo,
                                               Authentication auth) {
        return approvalService.listPending(Long.valueOf(auth.getName()), requestType, stepNo);
    }

    /** ONL-010 承認（body 省略可）。 */
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ApprovalResultResponse approve(@PathVariable("id") Long id,
                                          @Valid @RequestBody(required = false) ApprovalCommentRequest request,
                                          Authentication auth) {
        return approvalService.approve(id, Long.valueOf(auth.getName()), commentOf(request));
    }

    /** ONL-011 差戻し（コメント必須は Service で判定）。 */
    @PostMapping("/{id}/return")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ApprovalResultResponse returnBack(@PathVariable("id") Long id,
                                             @Valid @RequestBody(required = false) ApprovalCommentRequest request,
                                             Authentication auth) {
        return approvalService.returnBack(id, Long.valueOf(auth.getName()), commentOf(request));
    }

    /** ONL-012 却下（コメント必須は Service で判定）。 */
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ApprovalResultResponse reject(@PathVariable("id") Long id,
                                         @Valid @RequestBody(required = false) ApprovalCommentRequest request,
                                         Authentication auth) {
        return approvalService.reject(id, Long.valueOf(auth.getName()), commentOf(request));
    }

    private static String commentOf(ApprovalCommentRequest request) {
        return request == null ? null : request.comment();
    }
}
