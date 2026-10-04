package com.example.kintaiflow.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 休暇残日数取得の応答（ONL-013）。 */
public record LeaveBalanceResponse(LocalDate asOf, List<Item> balances, Mandatory5 mandatory5,
                                   LocalDate nextGrantDate) {

    /** 休暇種別ごとの集計。 */
    public record Item(Long leaveTypeId, String leaveTypeName, boolean annual, BigDecimal grantedDays,
                       BigDecimal usedDays, BigDecimal remainingDays, LocalDate expiresOn, List<Grant> grants) {
    }

    /** 有効な付与行の内訳。 */
    public record Grant(Long balanceId, LocalDate grantedOn, LocalDate expiresOn, BigDecimal grantedDays,
                        BigDecimal usedDays, BigDecimal remainingDays) {
    }

    /** 年5日取得義務の進捗。 */
    public record Mandatory5(boolean target, BigDecimal requiredDays, BigDecimal takenDays,
                             BigDecimal shortageDays, boolean achieved, LocalDate periodStart,
                             LocalDate periodEnd, BigDecimal grantedDays) {
    }
}
