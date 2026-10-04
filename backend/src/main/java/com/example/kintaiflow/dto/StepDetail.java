package com.example.kintaiflow.dto;

/** ONL-007 承認履歴の1段分。 */
public record StepDetail(
        Integer stepNo,
        String stepName,
        Long approverId,
        String approverName,
        String status,
        String comment,
        String actedAt,
        Boolean current) {
}
