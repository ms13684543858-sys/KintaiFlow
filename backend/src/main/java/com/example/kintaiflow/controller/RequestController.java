package com.example.kintaiflow.controller;

import com.example.kintaiflow.dto.*;
import com.example.kintaiflow.service.RequestService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** ONL-005〜008, ONL-022 申請の作成・一覧・詳細・取下げ・提出。操作対象は常にログイン本人（ロール制限なし）。 */
@RestController
@RequestMapping("/api/requests")
public class RequestController {

    private final RequestService requestService;

    public RequestController(RequestService requestService) {
        this.requestService = requestService;
    }

    /** ONL-005 申請作成。 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateRequestResponse create(Authentication auth, @Valid @RequestBody CreateRequestRequest request) {
        return requestService.create(Long.valueOf(auth.getName()), request);
    }

    /** ONL-006 申請一覧取得。 */
    @GetMapping
    public RequestListResponse list(Authentication auth, @RequestParam(required = false) String status) {
        return requestService.list(Long.valueOf(auth.getName()), status);
    }

    /** ONL-007 申請詳細取得。 */
    @GetMapping("/{id}")
    public RequestDetailResponse detail(Authentication auth, @PathVariable Long id) {
        return requestService.getDetail(id, Long.valueOf(auth.getName()));
    }

    /** ONL-008 申請取下げ。 */
    @PostMapping("/{id}/withdraw")
    public WithdrawResponse withdraw(Authentication auth, @PathVariable Long id) {
        return requestService.withdraw(id, Long.valueOf(auth.getName()));
    }

    /** ONL-022 申請提出（下書き・差戻しの提出）。 */
    @PostMapping("/{id}/submit")
    public SubmitRequestResponse submit(Authentication auth, @PathVariable Long id) {
        return requestService.submit(id, Long.valueOf(auth.getName()));
    }
}
