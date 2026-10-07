package com.example.kintaiflow.dto;

import java.time.LocalDate;

/** BAT-001 の付与候補（氏名・メールは取得しない）。 */
public record GrantCandidate(Long id, LocalDate hireDate) {
}
