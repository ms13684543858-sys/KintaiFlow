package com.example.kintaiflow.dto;

import jakarta.validation.constraints.Size;

/** ONL-010〜012 承認・差戻し・却下の共通リクエスト。コメント必須（差戻し・却下）は @NotBlank ではなく Service で判定する（E-008）。 */
public record ApprovalCommentRequest(
        @Size(max = 500, message = "コメントは500文字以内で入力してください。") String comment) {
}
