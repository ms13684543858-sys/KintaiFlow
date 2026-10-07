package com.example.kintaiflow.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

/** BAT-001 応当日の判定と付与日数の決定（副作用のない純粋関数）。 */
public final class LeaveGrantCalculator {

    public record GrantDue(LocalDate grantDate, int index) {}

    private LeaveGrantCalculator() {}

    /** date が入社日の応当日なら付与回数 n（0=初回: 6か月後）。毎回 first から加算し、月末・2/29 の丸めを累積させない。 */
    public static OptionalInt indexOf(LocalDate hireDate, LocalDate date) {
        LocalDate first = hireDate.plusMonths(6);
        if (date.isBefore(first)) return OptionalInt.empty();
        int n = date.getYear() - first.getYear();
        return first.plusYears(n).equals(date) ? OptionalInt.of(n) : OptionalInt.empty();
    }

    /** [from, to]（両端含む）にある応当日を日付昇順で返す。 */
    public static List<GrantDue> dueDates(LocalDate hireDate, LocalDate from, LocalDate to) {
        List<GrantDue> list = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            OptionalInt idx = indexOf(hireDate, d);
            if (idx.isPresent()) list.add(new GrantDue(d, idx.getAsInt()));
        }
        return list;
    }

    /** 付与回数から法定日数を決める（月末入社で MONTHS.between が崩れるのを避けるため回数ベース）。 */
    public static BigDecimal grantDays(int index) {
        return LeaveBalanceService.statutoryDays(6L + 12L * index);
    }

    /** 通知用の勤続表記。 */
    public static String label(int index) {
        return index == 0 ? "6か月" : index + "年6か月";
    }
}
