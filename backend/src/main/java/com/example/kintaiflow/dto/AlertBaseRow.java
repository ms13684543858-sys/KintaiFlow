package com.example.kintaiflow.dto;

import com.example.kintaiflow.entity.LeaveBalance;

import java.math.BigDecimal;
import java.time.LocalDate;

/** BAT-002 の対象行（年休の付与行＋対象者の氏名）。氏名は通知本文にのみ使う。 */
public record AlertBaseRow(Long userId, String userName, LocalDate grantedOn, BigDecimal grantedDays) {

    public static AlertBaseRow of(LeaveBalance b, String userName) {
        return new AlertBaseRow(b.getUserId(), userName, b.getGrantedOn(), b.getGrantedDays());
    }
}
